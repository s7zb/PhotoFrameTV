package com.s7zb.photoframetv

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Single owner of the upload server, so there is never more than one instance (no port conflicts).
 * The server runs while at least one "owner" ("activity" = app on screen, "service" = background service) holds it.
 */
object ServerHost {
    @Volatile var listener: (() -> Unit)? = null

    private var server: UploadServer? = null
    private val owners = HashSet<String>()
    private var wifiLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquire(ctx: Context, owner: String): Boolean {
        val app = ctx.applicationContext
        PhotoStore.init(app)
        owners.add(owner)
        val s = server ?: UploadServer(app) { listener?.invoke() }.also { server = it }
        val ok = s.start()
        if (ok) acquireWifi(app)
        return ok
    }

    @Synchronized
    fun release(owner: String) {
        owners.remove(owner)
        if (owners.isEmpty()) {
            server?.stop()
            server = null
            releaseWifi()
        }
    }

    @Synchronized fun isRunning(): Boolean = server?.isRunning == true
    @Synchronized fun port(): Int = server?.port ?: 0

    @Suppress("DEPRECATION")
    private fun acquireWifi(app: Context) {
        try {
            if (wifiLock?.isHeld == true) return
            val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "photoframe").also { it.acquire() }
        } catch (e: Exception) { /* optional */ }
    }

    private fun releaseWifi() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (e: Exception) { }
        wifiLock = null
    }
}
