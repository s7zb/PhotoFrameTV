package com.s7zb.photoframetv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var qrView: ImageView
    private lateinit var urlText: TextView
    private lateinit var hintText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnSlideshow: Button
    private lateinit var btnLibrary: Button
    private lateinit var btnDuration: Button
    private lateinit var btnBackground: Button
    private lateinit var btnShuffle: Button
    private lateinit var btnCaptions: Button
    private lateinit var btnSaver: Button

    private var shownUrl: String? = null
    private val poll = object : Runnable {
        override fun run() {
            refreshNetwork()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhotoStore.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        ServerHost.acquire(this, "activity")
        if (Prefs.background(this)) UploadService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // retry if the server failed earlier (e.g. no free port at that moment)
        if (!ServerHost.isRunning()) ServerHost.acquire(this, "activity")
        ServerHost.listener = { runOnUiThread { updateCount() } }
        updateCount()
        handler.removeCallbacks(poll)
        poll.run()
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        ServerHost.listener = null
        super.onPause()
    }

    override fun onDestroy() {
        // keeps running only if the background service is holding it
        ServerHost.release("activity")
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.setBackgroundColor(COLOR_BG)
        root.setPadding(dp(56), dp(28), dp(56), dp(28))

        // ---- left: QR code ----
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.gravity = Gravity.CENTER
        root.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val title = TextView(this)
        title.text = "PhotoFrame TV"
        title.setTextColor(Color.WHITE)
        title.textSize = 34f
        title.typeface = Typeface.DEFAULT_BOLD
        left.addView(title)

        val sub = TextView(this)
        sub.text = "Scan with your phone's camera to add photos"
        sub.setTextColor(COLOR_TEXT_DIM)
        sub.textSize = 20f
        sub.setPadding(0, dp(6), 0, dp(24))
        left.addView(sub)

        qrView = ImageView(this)
        qrView.setBackgroundColor(Color.WHITE)
        qrView.setPadding(dp(14), dp(14), dp(14), dp(14))
        left.addView(qrView, LinearLayout.LayoutParams(dp(300), dp(300)))

        urlText = TextView(this)
        urlText.setTextColor(Color.WHITE)
        urlText.textSize = 24f
        urlText.typeface = Typeface.MONOSPACE
        urlText.setPadding(0, dp(20), 0, dp(4))
        left.addView(urlText)

        hintText = TextView(this)
        hintText.setTextColor(COLOR_TEXT_DIM)
        hintText.textSize = 18f
        left.addView(hintText)

        // ---- right: menu (scrollable so it fits any screen) ----
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        root.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.CENTER_VERTICAL
        scroll.addView(right, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        statusText = TextView(this)
        statusText.setTextColor(Color.WHITE)
        statusText.textSize = 26f
        statusText.setPadding(0, 0, 0, dp(16))
        right.addView(statusText)

        btnSlideshow = tvButton("Start slideshow")
        btnLibrary = tvButton("Photo library")
        btnDuration = tvButton("")
        btnShuffle = tvButton("")
        btnCaptions = tvButton("")
        btnBackground = tvButton("")
        btnSaver = tvButton("Set up screensaver")

        fun addFull(b: Button) {
            b.textSize = 20f
            val lp = LinearLayout.LayoutParams(dp(420), dp(56))
            lp.bottomMargin = dp(10)
            right.addView(b, lp)
        }
        addFull(btnSlideshow)
        addFull(btnLibrary)
        addFull(btnDuration)

        // Shuffle and Captions share one row (Left/Right moves between them)
        val toggles = LinearLayout(this)
        toggles.orientation = LinearLayout.HORIZONTAL
        for ((i, b) in listOf(btnShuffle, btnCaptions).withIndex()) {
            b.textSize = 20f
            val lp = LinearLayout.LayoutParams(dp(205), dp(56))
            if (i == 0) lp.rightMargin = dp(10)
            toggles.addView(b, lp)
        }
        val tlp = LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT)
        tlp.bottomMargin = dp(10)
        right.addView(toggles, tlp)

        addFull(btnBackground)
        addFull(btnSaver)

        btnSlideshow.setOnClickListener {
            if (PhotoStore.list().isEmpty()) {
                Toast.makeText(this, "No photos yet - scan the QR code to upload some.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, SlideshowActivity::class.java))
            }
        }
        btnLibrary.setOnClickListener { startActivity(Intent(this, LibraryActivity::class.java)) }
        btnDuration.setOnClickListener {
            val next = Prefs.STEPS.firstOrNull { it > Prefs.duration(this) } ?: Prefs.STEPS[0]
            Prefs.setDuration(this, next)
            updateLabels()
        }
        btnShuffle.setOnClickListener {
            Prefs.setShuffle(this, !Prefs.shuffle(this))
            updateLabels()
        }
        btnCaptions.setOnClickListener {
            Prefs.setCaptions(this, !Prefs.captions(this))
            updateLabels()
        }
        btnBackground.setOnClickListener {
            val on = !Prefs.background(this)
            Prefs.setBackground(this, on)
            if (on) UploadService.start(this) else UploadService.stop(this)
            updateLabels()
        }
        btnSaver.setOnClickListener { openScreensaverSettings() }
        updateLabels()

        val tip = TextView(this)
        tip.text = "Slideshow keys: Left/Right = previous/next, OK = pause/resume, Up/Down = duration, hold OK = shuffle & captions"
        tip.setTextColor(COLOR_TEXT_DIM)
        tip.textSize = 16f
        tip.setPadding(0, dp(6), 0, 0)
        right.addView(tip)

        setContentView(root)
        btnSlideshow.requestFocus()
    }

    private fun openScreensaverSettings() {
        Toast.makeText(this, "Choose \"PhotoFrame TV\" under Screen saver.", Toast.LENGTH_LONG).show()
        for (action in listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                startActivity(Intent(action))
                return
            } catch (e: Exception) { /* try the next one */ }
        }
        Toast.makeText(
            this,
            "This TV has no screensaver settings screen. The in-app slideshow still works.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateLabels() {
        btnDuration.text = "Slide duration: ${Prefs.duration(this)} s"
        btnShuffle.text = "Shuffle: " + if (Prefs.shuffle(this)) "On" else "Off"
        btnCaptions.text = "Captions: " + if (Prefs.captions(this)) "On" else "Off"
        btnBackground.text = "Background server: " + if (Prefs.background(this)) "On" else "Off"
    }

    private fun updateCount() {
        val n = PhotoStore.list().size
        statusText.text = if (n == 1) "1 photo on this TV" else "$n photos on this TV"
        btnLibrary.text = "Photo library ($n)"
    }

    private fun refreshNetwork() {
        if (!ServerHost.isRunning()) {
            showNoServer("Upload server could not start.", "Close and reopen the app.")
            return
        }
        val ip = NetUtil.localIp()
        if (ip == null) {
            showNoServer("No network connection", "Connect the TV to Wi-Fi or Ethernet.")
            return
        }
        val url = "http://$ip:${ServerHost.port()}/"
        if (url == shownUrl) return
        shownUrl = url
        qrView.setImageBitmap(QrUtil.make(url, 600))
        qrView.visibility = View.VISIBLE
        urlText.text = url
        hintText.text = "Your phone must be on the same Wi-Fi network"
    }

    private fun showNoServer(line1: String, line2: String) {
        shownUrl = null
        qrView.setImageDrawable(null)
        qrView.visibility = View.INVISIBLE
        urlText.text = line1
        hintText.text = line2
    }
}
