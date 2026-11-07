package com.s7zb.photoframetv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Full-screen slideshow with cross-fade. Shared by SlideshowActivity (interactive)
 * and PhotoDream (system screensaver, non-interactive).
 * Images use FIT_CENTER, so aspect ratio is always preserved (portrait gets black bars).
 */
class Slideshow(private val ctx: Context, private val root: FrameLayout, private val interactive: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val cache = LruCache<String, Bitmap>(3)

    private var baseFiles: List<File> = emptyList()   // sorted by upload time
    private var files: List<File> = emptyList()       // current play order (shuffled or sorted)
    private var index = 0
    private var token = 0
    @Volatile private var stopped = false

    var paused = false
        private set
    var durationSec = Prefs.duration(ctx)
        private set
    var shuffle = Prefs.shuffle(ctx)
        private set
    var captionsOn = Prefs.captions(ctx)
        private set

    private val viewA = makeImageView()
    private val viewB = makeImageView()
    private var front = viewA
    private var back = viewB
    private val hud = TextView(ctx)
    private val message = TextView(ctx)
    private val cap = TextView(ctx)

    private val screenW = maxOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)
    private val screenH = minOf(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)

    private val advance = Runnable { step(1) }
    private val hideHud = Runnable { hud.animate().alpha(0f).setDuration(300).start() }

    init {
        root.setBackgroundColor(Color.BLACK)
        root.addView(viewA)
        root.addView(viewB)
        viewB.alpha = 0f

        message.setTextColor(Color.WHITE)
        message.textSize = 28f
        message.gravity = Gravity.CENTER
        message.visibility = View.GONE
        root.addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        cap.setTextColor(Color.WHITE)
        cap.textSize = 26f
        cap.gravity = Gravity.CENTER
        cap.maxLines = 2
        cap.ellipsize = android.text.TextUtils.TruncateAt.END
        cap.setShadowLayer(6f, 0f, 2f, Color.BLACK)
        cap.setBackgroundColor(0x99000000.toInt())
        cap.setPadding(ctx.dp(24), ctx.dp(10), ctx.dp(24), ctx.dp(10))
        cap.alpha = 0f
        val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        clp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        clp.setMargins(ctx.dp(80), 0, ctx.dp(80), ctx.dp(40))
        root.addView(cap, clp)

        hud.setTextColor(Color.WHITE)
        hud.textSize = 20f
        hud.setBackgroundColor(0xAA000000.toInt())
        hud.setPadding(ctx.dp(20), ctx.dp(10), ctx.dp(20), ctx.dp(10))
        hud.alpha = 0f
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.setMargins(ctx.dp(32), ctx.dp(32), 0, 0)
        root.addView(hud, lp)
    }

    private fun makeImageView(): ImageView {
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        return iv
    }

    fun start() {
        stopped = false
        baseFiles = PhotoStore.list()
        if (baseFiles.isEmpty()) {
            files = emptyList()
            showEmpty()
            return
        }
        message.visibility = View.GONE
        files = buildOrder(null)
        index = if (interactive || shuffle) 0 else Random.nextInt(files.size)
        show(index)
        if (interactive) showHud("Left/Right: previous/next   OK: pause/resume   Up/Down: duration   Hold OK or Menu: shuffle & captions   Back: exit", 6000)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        exec.shutdownNow()
        viewA.animate().cancel(); viewB.animate().cancel()
        viewA.setImageDrawable(null)
        viewB.setImageDrawable(null)
        cache.evictAll()
    }

    private fun showEmpty() {
        message.text = "No photos yet.\nOpen PhotoFrame TV and scan the QR code to add some."
        message.visibility = View.VISIBLE
    }

    private fun show(i: Int) {
        if (stopped || files.isEmpty()) return
        handler.removeCallbacks(advance)
        val my = ++token
        index = i
        val f = files[i]
        val cached = cache.get(f.path)
        if (cached != null) {
            display(my, cached, f)
            return
        }
        try {
            exec.execute {
                val bmp = ImageLoader.decode(f, screenW, screenH)
                handler.post {
                    if (stopped || my != token) return@post
                    if (bmp == null) {
                        // unreadable file: drop it from this run and move on
                        baseFiles = baseFiles.filter { it != f }
                        files = files.filter { it != f }
                        if (files.isEmpty()) showEmpty() else show(i % files.size)
                    } else {
                        display(my, bmp, f)
                    }
                }
            }
        } catch (e: Exception) { /* executor shut down */ }
    }

    private fun display(my: Int, bmp: Bitmap, f: File) {
        if (stopped || my != token) return
        cache.put(f.path, bmp)

        back.setImageBitmap(bmp)
        back.animate().cancel()
        front.animate().cancel()
        back.alpha = 0f
        back.bringToFront()
        cap.bringToFront()
        hud.bringToFront()
        updateCaption(f)
        back.animate().alpha(1f).setDuration(600).start()
        front.animate().alpha(0f).setDuration(600).start()
        val tmp = front; front = back; back = tmp

        if (!paused && files.size > 1) handler.postDelayed(advance, durationSec * 1000L)
        prefetchNext()
    }

    private fun prefetchNext() {
        if (files.size < 2) return
        if (shuffle && index == files.size - 1) return
        val next = files[(index + 1) % files.size]
        if (cache.get(next.path) != null) return
        try {
            exec.execute {
                if (stopped) return@execute
                val b = ImageLoader.decode(next, screenW, screenH)
                if (b != null && !stopped) cache.put(next.path, b)
            }
        } catch (e: Exception) { }
    }

    private fun step(delta: Int) {
        if (files.isEmpty()) return
        val n = files.size
        if (delta > 0 && shuffle && n > 1 && index == n - 1) {
            // cycle finished: new random order whose first photo differs from the one just shown
            files = reshuffleAvoiding(files[index])
            show(0)
            return
        }
        show(((index + delta) % n + n) % n)
    }

    private fun buildOrder(current: File?): List<File> {
        if (!shuffle) return baseFiles
        val l = baseFiles.shuffled().toMutableList()
        if (current != null) {
            val i = l.indexOf(current)
            if (i > 0) { l.removeAt(i); l.add(0, current) }
        }
        return l
    }

    private fun reshuffleAvoiding(last: File): List<File> {
        val l = baseFiles.shuffled().toMutableList()
        if (l.size > 1 && l[0] == last) {
            val j = 1 + Random.nextInt(l.size - 1)
            val t = l[0]; l[0] = l[j]; l[j] = t
        }
        return l
    }

    private fun updateCaption(f: File?) {
        cap.animate().cancel()
        val text = if (captionsOn && f != null) PhotoStore.captionFor(f) else ""
        if (text.isEmpty()) {
            cap.alpha = 0f
            return
        }
        cap.text = text
        cap.alpha = 0f
        cap.animate().alpha(1f).setDuration(600).start()
    }

    /** Switch shuffle on/off without interrupting the photo that is on screen. */
    fun applyShuffle(on: Boolean) {
        if (on == shuffle) return
        shuffle = on
        Prefs.setShuffle(ctx, on)
        if (files.isNotEmpty()) {
            val cur = files[index]
            files = buildOrder(cur)
            index = if (on) 0 else files.indexOf(cur).coerceAtLeast(0)
            prefetchNext()
        }
        showHud("Shuffle: " + if (on) "On" else "Off")
    }

    fun applyCaptions(on: Boolean) {
        if (on == captionsOn) return
        captionsOn = on
        Prefs.setCaptions(ctx, on)
        updateCaption(files.getOrNull(index))
        showHud("Captions: " + if (on) "On" else "Off")
    }

    fun next() { step(1); flash() }
    fun previous() { step(-1); flash() }

    private fun flash() {
        if (files.isNotEmpty()) showHud("${index + 1} / ${files.size}    ${PhotoStore.displayName(files[index])}")
    }

    fun togglePause() {
        paused = !paused
        handler.removeCallbacks(advance)
        if (!paused && files.size > 1) handler.postDelayed(advance, durationSec * 1000L)
        showHud(if (paused) "Paused" else "Playing - ${durationSec}s per photo")
    }

    fun applyPaused(p: Boolean) { if (p != paused) togglePause() }

    fun changeDuration(dir: Int) {
        durationSec = Prefs.stepDuration(durationSec, dir)
        Prefs.setDuration(ctx, durationSec)
        if (!paused && files.size > 1) {
            handler.removeCallbacks(advance)
            handler.postDelayed(advance, durationSec * 1000L)
        }
        showHud("Slide duration: ${durationSec}s")
    }

    private fun showHud(msg: String, holdMs: Long = 2500) {
        if (!interactive) return
        hud.text = msg
        hud.animate().cancel()
        hud.alpha = 1f
        handler.removeCallbacks(hideHud)
        handler.postDelayed(hideHud, holdMs)
    }
}
