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
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var captureStatusText: TextView
    private lateinit var toggleButton: Button
    private var isCapturing = false

    companion object {
        var captureEnabled = false
            private set

        fun setCaptureEnabled(context: Context, enabled: Boolean) {
            captureEnabled = enabled
            // Notify the service if it's running
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
            Toast.makeText(this, "Please enable accessibility service first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        isCapturing = !isCapturing
        setCaptureEnabled(this, isCapturing)
        
        if (isCapturing) {
            toggleButton.text = "STOP CAPTURE"
            captureStatusText.text = "Capture: ON"
            captureStatusText.setTextColor(0xFF00FF00.toInt()) // Green
            Toast.makeText(this, "UI Capture Started", Toast.LENGTH_SHORT).show()
        } else {
            toggleButton.text = "START CAPTURE"
            captureStatusText.text = "Capture: OFF"
            captureStatusText.setTextColor(0xFFFF0000.toInt()) // Red
            Toast.makeText(this, "UI Capture Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Request MANAGE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE),
                    101
                )
            }
        } else {
            // Android 10 and below: Request WRITE_EXTERNAL_STORAGE
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
            statusText.text = "Status: Accessibility service is enabled ✓"
            statusText.setTextColor(0xFF00FF00.toInt()) // Green
        } else {
            statusText.text = "Status: ❌ Accessibility service is NOT enabled"
            statusText.setTextColor(0xFFFF0000.toInt()) // Red
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            // Check multiple possible formats
            val possibleNames = listOf(
                "com.uitreecapture/.MyAccessibilityService",
                "com.trojan.autoscreenshot/.MyAccessibilityService",
                "uitreecapture/MyAccessibilityService",
                "autoscreenshot/MyAccessibilityService",
                "MyAccessibilityService"
            )
            
            for (name in possibleNames) {
                if (enabledServices.contains(name)) {
                    return true
                }
            }
            
            // Also check if any service from our package is enabled
            val services = enabledServices.split(":")
            for (service in services) {
                if (service.contains("uitreecapture") || service.contains("autoscreenshot")) {
                    return true
                }
            }
            
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
        checkCaptureStatus()
    }

    private fun checkCaptureStatus() {
        try {
            val logFile = File(android.os.Environment.getExternalStorageDirectory(), "Controller/capture_log.txt")
            if (logFile.exists()) {
                val lastLine = logFile.readLines().lastOrNull()
                if (lastLine != null && lastLine.contains("[ERROR]")) {
                    val errorMsg = lastLine.substringAfter("[ERROR] ").substringAfter(" - ")
                    Toast.makeText(this, "Capture Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}