package com.uitreecapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class UIHierarchyService : Service() {

    companion object {
        const val ACTION_START  = "ACTION_START"
        const val ACTION_STOP   = "ACTION_STOP"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"

        // FIX #2 — volatile so changes are visible across threads immediately
        @Volatile var isRunning: Boolean = false

        private const val CHANNEL_ID = "UIHierarchyCaptureChannel"
        private const val NOTIF_ID   = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val interval = intent.getIntExtra(EXTRA_INTERVAL, 1000)
                startForegroundWithNotification()
                // FIX #2 — flip the flag BEFORE calling startCapture so MainActivity
                // sees the updated state even if it polls immediately
                isRunning = true
                UIHierarchyAccessibilityService.instance?.startCapture(interval)
            }
            ACTION_STOP -> {
                // FIX #2 — flip flag first, then clean up
                isRunning = false
                UIHierarchyAccessibilityService.instance?.stopCapture()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelf()
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

        startForeground(NOTIF_ID, builder
            .setContentTitle("UI Hierarchy Capture")
            .setContentText("Capturing UI tree in background…")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Safety net: make sure flag is cleared if the service is killed
        isRunning = false
    }
}