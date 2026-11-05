package com.photoframe.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var server: PhotoServer? = null
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var qr: ImageView
    private lateinit var url: TextView
    private lateinit var status: TextView
    private lateinit var thumbs: LinearLayout
    private lateinit var btnInterval: Button
    private var received = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        qr = findViewById(R.id.qr); url = findViewById(R.id.url)
        status = findViewById(R.id.status); thumbs = findViewById(R.id.thumbs)
        btnInterval = findViewById(R.id.btnInterval)

        findViewById<Button>(R.id.btnSlideshow).setOnClickListener {
            startActivity(Intent(this, SlideshowActivity::class.java))
        }
        btnInterval.setOnClickListener { Prefs.nextInterval(this); updateInterval() }
        findViewById<Button>(R.id.btnSaver).setOnClickListener { showSaverHelp() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            AlertDialog.Builder(this).setMessage("Delete ALL photos stored on this TV?")
                .setPositiveButton("Delete all") { _, _ ->
                    PhotoStore.deleteAll(this); thumbs.removeAllViews(); updateStatus()
                }
                .setNegativeButton("Cancel", null).show()
        }
        findViewById<Button>(R.id.btnSlideshow).requestFocus()
        updateInterval()
    }

    override fun onStart() {
        super.onStart()
        startServer()
        loadThumbs()
        updateStatus()
    }

    override fun onDestroy() {
        server?.stop(); server = null
        bg.shutdownNow()
        super.onDestroy()
    }

    private fun startServer() {
        if (server == null) {
            server = PhotoServer(this, Prefs.token(this)) { f -> runOnUiThread { onPhotoReceived(f) } }
        }
        val s = server!!
        val ok = s.start()
        val ip = NetUtil.localIp()
        if (!ok) { showOffline("Could not start the upload server"); return }
        if (ip == null) { showOffline("No network. Connect the TV to Wi-Fi."); return }
        val link = "http://$ip:${s.port}/?t=${Prefs.token(this)}"
        qr.visibility = View.VISIBLE
        qr.setImageBitmap(QrUtil.make(link))
        url.text = link
    }

    private fun showOffline(msg: String) {
        qr.visibility = View.INVISIBLE
        url.text = msg
    }

    private fun onPhotoReceived(f: File) {
        received++
        updateStatus()
        addThumb(f, atStart = true)
    }

    private fun updateStatus() {
        val n = PhotoStore.list(this).size
        status.text = if (received > 0) "$n photos on this TV  •  $received received this session" else "$n photos on this TV"
    }

    private fun updateInterval() { btnInterval.text = "Interval: ${Prefs.intervalSec(this)}s" }

    private fun loadThumbs() {
        thumbs.removeAllViews()
        val files = PhotoStore.list(this).reversed().take(30)
        files.forEach { addThumb(it, atStart = false) }
    }

    private fun addThumb(f: File, atStart: Boolean) {
        val d = resources.displayMetrics.density
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF2A2F36.toInt())
            layoutParams = LinearLayout.LayoutParams((128 * d).toInt(), (96 * d).toInt()).apply { marginEnd = (8 * d).toInt() }
        }
        if (atStart) thumbs.addView(iv, 0) else thumbs.addView(iv)
        if (atStart && thumbs.childCount > 30) thumbs.removeViewAt(thumbs.childCount - 1)
        try {
            bg.execute {
                val bmp: Bitmap? = PhotoStore.decode(f, 256, 192)
                runOnUiThread { if (bmp != null) iv.setImageBitmap(bmp) }
            }
        } catch (_: Exception) {}
    }

    private fun showSaverHelp() {
        AlertDialog.Builder(this)
            .setTitle("Use as screensaver")
            .setMessage(
                "On the TV open:\nSettings > Device Preferences > Screen saver > Screen saver, then choose \"Photo Frame\".\n\n" +
                "If it is not listed, run this once from a computer:\n" +
                "adb shell settings put secure screensaver_components com.photoframe.tv/.PhotoDreamService"
            )
            .setPositiveButton("Open settings") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
