package com.s7zb.photoframetv

import android.service.dreams.DreamService
import android.widget.FrameLayout

/** Appears under Settings > Device Preferences > Screen saver on Android TV. */
class PhotoDream : DreamService() {
    private var show: Slideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setInteractive(false)
        setFullscreen(true)
        setScreenBright(true)
        PhotoStore.init(applicationContext)
        val root = FrameLayout(this)
        setContentView(root)
        show = Slideshow(this, root, false)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        show?.start()
    }

    override fun onDreamingStopped() {
        show?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        show?.stop()
        show = null
        super.onDetachedFromWindow()
    }
}
