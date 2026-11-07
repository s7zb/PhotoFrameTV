package com.s7zb.photoframetv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout

class SlideshowActivity : Activity() {
    private lateinit var root: FrameLayout
    private var show: Slideshow? = null
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhotoStore.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        root.removeAllViews()
        show = Slideshow(this, root, true).also { it.start() }
    }

    override fun onStop() {
        dialog?.dismiss()
        dialog = null
        show?.stop()
        show = null
        super.onStop()
    }

    /** Settings list that works with any remote: Up/Down to move, OK to tick/untick. */
    private fun openSettings() {
        val s = show ?: return
        if (dialog?.isShowing == true) return
        val labels = arrayOf("Shuffle photos", "Show captions")
        val checked = booleanArrayOf(s.shuffle, s.captionsOn)
        dialog = AlertDialog.Builder(this)
            .setTitle("Slideshow settings")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (which == 0) s.applyShuffle(isChecked) else s.applyCaptions(isChecked)
            }
            .setPositiveButton("Close", null)
            .create()
        dialog?.show()
    }

    private fun isOk(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = show ?: return super.onKeyDown(keyCode, event)
        if (isOk(keyCode)) {
            // short press = pause/resume (handled on key-up), long press = settings
            if (event.repeatCount == 0) event.startTracking()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> s.next()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> s.previous()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> s.togglePause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> s.applyPaused(false)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> s.applyPaused(true)
            KeyEvent.KEYCODE_DPAD_UP -> s.changeDuration(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> s.changeDuration(-1)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_SETTINGS -> openSettings()
            KeyEvent.KEYCODE_PROG_GREEN -> s.applyShuffle(!s.shuffle)
            KeyEvent.KEYCODE_PROG_YELLOW -> s.applyCaptions(!s.captionsOn)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) {
            openSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val s = show
        if (s != null && isOk(keyCode)) {
            if (event.isTracking && !event.isCanceled) s.togglePause()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
