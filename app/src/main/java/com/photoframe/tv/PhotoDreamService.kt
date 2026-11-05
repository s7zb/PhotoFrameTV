package com.photoframe.tv

import android.service.dreams.DreamService

/** The system screensaver ("Daydream") entry point. */
class PhotoDreamService : DreamService() {
    private var view: SlideshowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // any key / touch ends the screensaver
        isFullscreen = true
        val v = SlideshowView(this)
        view = v
        setContentView(v)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); view?.start() }
    override fun onDreamingStopped() { view?.stop(); super.onDreamingStopped() }
    override fun onDetachedFromWindow() { view?.stop(); view = null; super.onDetachedFromWindow() }
}
