package com.uitreecapture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnOpenSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInterval = findViewById(R.id.etInterval)
        tvStatus = findViewById(R.id.tvStatus)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        btnOpenSettings.setOnClickListener { openAccessibilitySettings() }

        btnStartStop.setOnClickListener {
            if (UIHierarchyService.isRunning) stopCapture() else startCapture()
        }

        // Request MANAGE_EXTERNAL_STORAGE for Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 100)
            }
        }

        ensureOutputDir()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun startCapture() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable the Accessibility Service first!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        val interval = etInterval.text.toString().toIntOrNull()?.coerceAtLeast(100) ?: 1000
        val intent = Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_START
            putExtra(UIHierarchyService.EXTRA_INTERVAL, interval)
        }
        startService(intent)
        Toast.makeText(this, "Capture started!", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopCapture() {
        val intent = Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Capture stopped!", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun updateUI() {
        if (UIHierarchyService.isRunning) {
            btnStartStop.text = "Stop Capture"
            tvStatus.text = "Status: CAPTURING\nOutput: /storage/emulated/0/Controller/ui_hierarchy.txt"
        } else {
            btnStartStop.text = "Start Capture"
            tvStatus.text = "Status: IDLE\nOutput: /storage/emulated/0/Controller/ui_hierarchy.txt"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return prefString?.contains("$packageName/${UIHierarchyAccessibilityService::class.java.name}") == true
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun ensureOutputDir() {
        File(Environment.getExternalStorageDirectory(), "Controller").mkdirs()
    }
}
