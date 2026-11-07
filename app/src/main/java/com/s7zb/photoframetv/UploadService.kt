package com.s7zb.photoframetv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Optional foreground service that keeps the upload server alive when the app is not on screen.
 * Only runs when the user turns on "Background server" in the app (off by default to save battery/RAM).
 */
class UploadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            goForeground()
        } catch (e: Exception) {
            // e.g. system refuses a foreground start from the background
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ServerHost.acquire(this, "service")) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServerHost.release("service")
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Upload server", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val piFlags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), piFlags)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setContentTitle("PhotoFrame TV upload server")
            .setContentText("Ready to receive photos from your phone")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "upload_server"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            val i = Intent(ctx, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, UploadService::class.java))
        }
    }
}
