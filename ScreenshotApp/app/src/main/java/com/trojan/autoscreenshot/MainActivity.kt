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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var btnRunScript: Button  // NEW

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
                Toast.makeText(this, "✅ 'All files access' granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "❌ 'All files access' denied — files cannot be saved!\nGrant it before starting capture.",
                    Toast.LENGTH_LONG
                ).show()
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
        btnRunScript    = findViewById(R.id.btnRunScript)  // NEW

        btnOpenSettings.setOnClickListener { openAccessibilitySettings() }
        btnStartStop.setOnClickListener {
            if (UIHierarchyService.isRunning) stopCapture() else startCapture()
        }
        btnRunScript.setOnClickListener { runScript() }  // NEW

        updatePermissionHint()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiRefreshRunnable)
        updatePermissionHint()
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
    }

    // ── NEW: Run Script ───────────────────────────────────────────────────────

    private fun runScript() {
        // 1. Check Script Executor accessibility service is connected
        val service = ScriptExecutorService.instance
        if (service == null) {
            Toast.makeText(
                this,
                "❌ Script Executor not active!\nGo to Settings → Accessibility → Script Executor and enable it.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // 2. Check storage permission
        if (!hasStoragePermission()) {
            Toast.makeText(
                this,
                "⚠️ Storage permission needed! Grant 'All files access' first.",
                Toast.LENGTH_LONG
            ).show()
            requestStoragePermissions()
            return
        }

        // 3. Build commands.json and write it to /storage/emulated/0/Controller/
        try {
            val controlDir = File(Environment.getExternalStorageDirectory(), "Controller")
            controlDir.mkdirs()
            val cmdFile = File(controlDir, "commands.json")

            // ── Define your script actions here ──────────────────────────────
            val actions = JSONArray().apply {
                put(JSONObject().apply {
                    put("action", "open_app")
                    put("package", "com.android.chrome")
                })
                put(JSONObject().apply {
                    put("action", "wait")
                    put("ms", 2000)
                })
                put(JSONObject().apply {
                    put("action", "new_tab")
                    put("url", "https://airdrop.io")
                })
                put(JSONObject().apply {
                    put("action", "wait")
                    put("ms", 3000)
                })
            }
            // ─────────────────────────────────────────────────────────────────

            val json = JSONObject().apply {
                put("actions", actions)
            }

            cmdFile.writeText(json.toString(2))

            Toast.makeText(
                this,
                "✅ Script sent! Running ${actions.length()} action(s)…",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "❌ Failed to write script: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Permission hint ───────────────────────────────────────────────────────

    private fun updatePermissionHint() {
        if (!hasStoragePermission()) {
            tvStatus.text = "Status: ⚠️ Storage permission required\nTap 'Grant Storage Permission' below"
        }
    }

    // ── Storage permissions ───────────────────────────────────────────────────

    private fun requestStoragePermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(
                        this,
                        "Please grant 'All files access' to save the hierarchy file",
                        Toast.LENGTH_LONG
                    ).show()
                    try {
                        manageStorageLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (e: Exception) {
                        manageStorageLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        REQ_STORAGE
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "❌ Storage permission denied — files cannot be saved!\nGrant it before starting capture.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Capture control ───────────────────────────────────────────────────────

    private fun startCapture() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        if (!hasStoragePermission()) {
            Toast.makeText(
                this,
                "⚠️ Storage permission not granted yet!\nGranting permission now...",
                Toast.LENGTH_LONG
            ).show()
            requestStoragePermissions()
            return
        }

        if (!tryCreateOutputDir()) return

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

    // ── Folder creation ───────────────────────────────────────────────────────

    private fun tryCreateOutputDir(): Boolean {
        val dir = File(Environment.getExternalStorageDirectory(), "Controller")

        return when {
            dir.exists() && dir.isDirectory -> true
            dir.exists() && !dir.isDirectory -> {
                Toast.makeText(
                    this,
                    "❌ Cannot create folder: a file named 'Controller' already exists at ${dir.absolutePath}!",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
            else -> {
                val created = dir.mkdirs()
                if (created) {
                    Toast.makeText(this, "✅ Created folder: ${dir.absolutePath}", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        "Go to Settings → Apps → UIHierarchyCapture\nand grant 'All files access'."
                    } else {
                        "Go to Settings → Apps → UIHierarchyCapture → Permissions\nand grant Storage permission."
                    }
                    Toast.makeText(
                        this,
                        "❌ Failed to create folder: ${dir.absolutePath}\n$hint",
                        Toast.LENGTH_LONG
                    ).show()
                    false
                }
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun updateUI() {
        val running = UIHierarchyService.isRunning
        btnStartStop.text = if (running) "⏹  Stop Capture" else "▶  Start Capture"

        val outputPath = File(Environment.getExternalStorageDirectory(), "Controller/ui_hierarchy.txt").absolutePath

        tvStatus.text = if (running)
            "Status: 🟢 CAPTURING\nOutput: $outputPath"
        else
            "Status: ⚪ IDLE\nOutput: $outputPath"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isAccessibilityServiceEnabled(): Boolean {
        val prefString = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${UIHierarchyAccessibilityService::class.java.name}"
        return prefString.split(":").any { it.trim().equals(target, ignoreCase = true) }
    }

    private fun hasStoragePermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    private fun openAccessibilitySettings() =
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    companion object {
        private const val REQ_STORAGE = 200
    }
}