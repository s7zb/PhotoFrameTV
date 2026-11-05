package com.photoframe.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/** Full-screen cross-fading slideshow shared by the screensaver (DreamService) and SlideshowActivity. */
class SlideshowView(context: Context) : FrameLayout(context) {
    private val imgA = newImage()
    private val imgB = newImage().also { it.alpha = 0f }
    private var front = imgA
    private var back = imgB
    private val msg = TextView(context).apply {
        text = "No photos yet\n\nOpen the Photo Frame app and scan the QR code\nto add photos from your phone"
        setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
    }
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var files: List<File> = emptyList()
    private var index = -1
    private var loadToken = 0
    private var running = false
    var paused = false
        private set

    private val tick = object : Runnable {
        override fun run() { if (!paused) step(1); schedule() }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(imgA, LayoutParams(-1, -1))
        addView(imgB, LayoutParams(-1, -1))
        addView(msg, LayoutParams(-2, -2, Gravity.CENTER))
    }

    private fun newImage() = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }

    private fun intervalMs() = Prefs.intervalSec(context) * 1000L

    fun start() { running = true; step(1); schedule() }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    fun next() { step(1); schedule() }
    fun prev() { step(-1); schedule() }
    fun togglePause(): Boolean { paused = !paused; return paused }
    fun currentFile(): File? = files.getOrNull(index)

    private fun schedule() {
        handler.removeCallbacks(tick)
        if (running) handler.postDelayed(tick, intervalMs())
    }

    private fun step(dir: Int) {
        files = PhotoStore.list(context)
        if (files.isEmpty()) { msg.visibility = VISIBLE; index = -1; return }
        index = ((index + dir) % files.size + files.size) % files.size
        val f = files[index]
        val token = ++loadToken
        val w = width.takeIf { it > 0 } ?: 1920
        val h = height.takeIf { it > 0 } ?: 1080
        try {
            executor.execute {
                val bmp = PhotoStore.decode(f, w, h)
                handler.post {
                    if (token != loadToken || !running) return@post
                    if (bmp == null) { f.delete(); step(if (dir < 0) -1 else 1) } else show(bmp)
                }
            }
        } catch (_: Exception) {}
    }

    private fun show(bmp: Bitmap) {
        msg.visibility = GONE
        back.setImageBitmap(bmp)
        back.animate().cancel(); front.animate().cancel()
        back.animate().alpha(1f).setDuration(900).start()
        front.animate().alpha(0f).setDuration(900).start()
        val t = front; front = back; back = t
    }
}
