package com.uitreecapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var captureStatusText: TextView
    private lateinit var toggleButton: Button
    private var isCapturing = false

    companion object {
        fun setCaptureEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, MyAccessibilityService::class.java)
            intent.action = "CAPTURE_TOGGLE"
            intent.putExtra("capture_enabled", enabled)
            context.startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        captureStatusText = findViewById(R.id.captureStatusText)
        toggleButton = findViewById(R.id.toggleCaptureButton)

        checkAccessibilityStatus()
        requestStoragePermission()
    }

    fun toggleCapture(view: View) {

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        isCapturing = !isCapturing
        setCaptureEnabled(this, isCapturing)

        if (isCapturing) {
            toggleButton.text = "STOP CAPTURE"
            captureStatusText.text = "Capture: ON"
            captureStatusText.setTextColor(0xFF00FF00.toInt())
        } else {
            toggleButton.text = "START CAPTURE"
            captureStatusText.text = "Capture: OFF"
            captureStatusText.setTextColor(0xFFFF0000.toInt())
        }
    }

    fun runScript(view: View) {

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable accessibility first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val intent = Intent(this, MyAccessibilityService::class.java)
        intent.action = "RUN_SCRIPT"
        startService(intent)

        Toast.makeText(this, "Script execution started", Toast.LENGTH_SHORT).show()
    }

    private fun requestStoragePermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }

        } else {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    100
                )
            }
        }
    }

    private fun checkAccessibilityStatus() {

        if (isAccessibilityServiceEnabled()) {
            statusText.text = "Status: Accessibility Enabled ✓"
            statusText.setTextColor(0xFF00FF00.toInt())
        } else {
            statusText.text = "Status: ❌ Accessibility NOT enabled"
            statusText.setTextColor(0xFFFF0000.toInt())
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains("uitreecapture")
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }
}