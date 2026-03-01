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
                // Permission just granted — NOW create the folder
                tryCreateOutputDir()
            } else {
                Toast.makeText(this,
                    "❌ 'All files access' denied — file cannot be saved!\nGrant it before starting capture.",
                    Toast.LENGTH_LONG).show()
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

        // Only request permission here — do NOT touch the folder yet
        requestStoragePermissions()

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
    }

    // ── Storage permissions ───────────────────────────────────────────────────

    private fun requestStoragePermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this,
                        "Please grant 'All files access' to save the hierarchy file",
                        Toast.LENGTH_LONG).show()
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName"))
                    )
                }
                // If already granted, folder will be created lazily on first Start press
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE),
                        REQ_STORAGE)
                }
                // If already granted, folder will be created lazily on first Start press
            }
            // else: < Android 6, no runtime permission needed
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission just granted — NOW create the folder
                tryCreateOutputDir()
                Toast.makeText(this, "✅ Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this,
                    "❌ Storage permission denied — file cannot be saved!\nGrant it before starting capture.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Capture control ───────────────────────────────────────────────────────

    private fun startCapture() {
        // Step 1: check accessibility
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        // Step 2: check storage permission
        if (!hasStoragePermission()) {
            Toast.makeText(this,
                "⚠️ Storage permission not granted yet!\nTap OK on the permission dialog.",
                Toast.LENGTH_LONG).show()
            requestStoragePermissions()
            return
        }

        // Step 3: create folder RIGHT NOW (every time, handles folder-was-deleted case too)
        if (!tryCreateOutputDir()) {
            // tryCreateOutputDir already shows the relevant Toast — just abort
            return
        }

        // Step 4: start
        val interval = etInterval.text.toString().toIntOrNull()?.coerceAtLeast(100) ?: 1000
        startService(Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_START
            putExtra(UIHierarchyService.EXTRA_INTERVAL, interval)
        })
        uiHandler.postDelayed({ updateUI() }, 400)
    }

    private fun stopCapture() {
        startService(Intent(this, UIHierarchyService::class.java).apply {
            action = UIHierarchyService.ACTION_STOP
        })
        uiHandler.postDelayed({ updateUI() }, 400)
    }

    // ── Folder creation — called lazily, NEVER in onCreate ───────────────────

    /**
     * Tries to create /storage/emulated/0/Controller.
     * - If it already exists → treats it as success, shows nothing.
     * - If it doesn't exist → tries mkdirs(), shows result Toast.
     * Returns true if the folder is ready to use, false otherwise.
     */
    private fun tryCreateOutputDir(): Boolean {
        val dir = File(Environment.getExternalStorageDirectory(), "Controller")

        return when {
            dir.exists() && dir.isDirectory -> {
                // Folder already there — nothing to do, just proceed silently
                true
            }
            dir.exists() && !dir.isDirectory -> {
                // A FILE called "Controller" exists at that path — very unlikely but handle it
                Toast.makeText(this,
                    "❌ Cannot create folder: a file named 'Controller' already exists at that path!",
                    Toast.LENGTH_LONG).show()
                false
            }
            else -> {
                // Folder doesn't exist — try to create it now
                val created = dir.mkdirs()
                if (created) {
                    Toast.makeText(this,
                        "✅ Created folder: ${dir.absolutePath}",
                        Toast.LENGTH_SHORT).show()
                    true
                } else {
                    Toast.makeText(this,
                        "❌ Failed to create folder: ${dir.absolutePath}\n" +
                        "Check that 'All files access' is granted in Settings → Apps → UIHierarchyCapture",
                        Toast.LENGTH_LONG).show()
                    false
                }
            }
        }
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
        return prefString.split(":").any { it.trim().equals(target, ignoreCase = true) }
    }

    private fun hasStoragePermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    companion object {
        private const val REQ_STORAGE = 200
    }
}