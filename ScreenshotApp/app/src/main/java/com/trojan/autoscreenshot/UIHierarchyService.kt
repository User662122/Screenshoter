package com.uitreecapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast

class UIHierarchyService : Service() {

    companion object {
        const val ACTION_START   = "ACTION_START"
        const val ACTION_STOP    = "ACTION_STOP"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"

        @Volatile var isRunning: Boolean = false

        private const val CHANNEL_ID = "UIHierarchyCaptureChannel"
        private const val NOTIF_ID   = 1
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun toast(msg: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(applicationContext, msg,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val interval = intent.getIntExtra(EXTRA_INTERVAL, 1000)

                val accessSvc = UIHierarchyAccessibilityService.instance
                if (accessSvc == null) {
                    toast(
                        "❌ Cannot start: Accessibility Service is NOT running!\n" +
                        "Go back and enable it in Settings first.",
                        long = true
                    )
                    return START_NOT_STICKY
                }

                startForegroundWithNotification()
                isRunning = true
                accessSvc.startCapture(interval)
                toast("▶ UIHierarchy capture started (${interval}ms interval)")
            }

            ACTION_STOP -> {
                isRunning = false
                UIHierarchyAccessibilityService.instance?.stopCapture()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
                toast("⏹ Capture stopped")
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "UI Hierarchy Capture", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        try {
            startForeground(NOTIF_ID, builder
                .setContentTitle("UI Hierarchy Capture")
                .setContentText("Capturing UI tree in background…")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
            )
        } catch (e: Exception) {
            toast("⚠️ Foreground notification failed: ${e.message}", long = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safety net: if OS kills the service, reset the flag
        if (isRunning) {
            isRunning = false
            toast("⚠️ Capture service was killed by the system!", long = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}