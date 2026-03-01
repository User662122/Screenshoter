package com.uitreecapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnOpenSettings: Button

    // Polls isRunning every 500ms so the button always reflects real state
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            uiHandler.postDelayed(this, 500)
        }
    }

    // Android 11+ "All files access" result
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                ensureOutputDir()
                Toast.makeText(this, "Storage permission granted ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied – file won't be saved!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInterval      = findViewById(R.id.etInterval)
        tvStatus        = findViewById(R.id.tvStatus)
        btnStartStop    = findViewById(R.id.btnStartStop)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        btnOpenSettings.setOnClickListener { openAccessibilitySettings() }
        btnStartStop.setOnClickListener {
            if (UIHierarchyService.isRunning) stopCapture() else startCapture()
        }

        // FIX #3 — request storage permissions at launch for all API levels
        requestStoragePermissions()
    }

    override fun onResume() {
        super.onResume()
        // FIX #2 — poll real state every 500ms so button stays in sync
        uiHandler.post(uiRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
    }

    // ── Storage permissions (FIX #3) ─────────────────────────────────────────

    private fun requestStoragePermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ needs special "All files access"
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this,
                        "Grant 'All files access' to save the hierarchy file",
                        Toast.LENGTH_LONG).show()
                    manageStorageLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    ensureOutputDir()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6–10: runtime WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE),
                        REQ_STORAGE)
                } else {
                    ensureOutputDir()
                }
            }
            else -> ensureOutputDir() // < Android 6: granted at install
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureOutputDir()
                Toast.makeText(this, "Storage permission granted ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied – file won't be saved!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Capture control ───────────────────────────────────────────────────────

    private fun startCapture() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Grant storage permission first!", Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }
        val interval = etInterval.text.toString().toIntOrNull()?.coerceAtLeast(100) ?: 1000
        startService(Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_START
            putExtra(UIHierarchyService.EXTRA_INTERVAL, interval)
        })
        // FIX #2 — wait for service to flip isRunning before refreshing UI
        uiHandler.postDelayed({ updateUI() }, 400)
    }

    private fun stopCapture() {
        startService(Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_STOP
        })
        // FIX #2 — wait for service to flip isRunning before refreshing UI
        uiHandler.postDelayed({ updateUI() }, 400)
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun updateUI() {
        val running = UIHierarchyService.isRunning
        btnStartStop.text = if (running) "⏹  Stop Capture" else "▶  Start Capture"
        tvStatus.text = if (running)
            "Status: 🟢 CAPTURING\nOutput: /storage/emulated/0/Controller/ui_hierarchy.txt"
        else
            "Status: ⚪ IDLE\nOutput: /storage/emulated/0/Controller/ui_hierarchy.txt"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val target = "$packageName/${UIHierarchyAccessibilityService::class.java.name}"
        // Split on ":" because Android stores multiple services separated by colons
        return prefString.split(":").any { it.trim().equals(target, ignoreCase = true) }
    }

    private fun hasStoragePermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun ensureOutputDir() {
        val dir = File(Environment.getExternalStorageDirectory(), "Controller")
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this,
                "⚠ Failed to create /Controller – check permissions", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQ_STORAGE = 200
    }
}