package com.photoframe.tv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/** Manual slideshow. LEFT/RIGHT = prev/next, OK = pause/resume, DOWN = delete current photo, BACK = exit. */
class SlideshowActivity : Activity() {
    private lateinit var view: SlideshowView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = SlideshowView(this)
        setContentView(view)
        hideSystemUi()
    }

    override fun onResume() { super.onResume(); hideSystemUi(); view.start() }
    override fun onPause() { super.onPause(); view.stop() }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> view.next()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> view.prev()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val p = view.togglePause()
                Toast.makeText(this, if (p) "Paused" else "Playing", Toast.LENGTH_SHORT).show()
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> confirmDelete()
            else -> return super.onKeyDown(keyCode, e)
        }
        return true
    }

    private fun confirmDelete() {
        val f = view.currentFile() ?: return
        AlertDialog.Builder(this)
            .setMessage("Delete this photo from the TV?")
            .setPositiveButton("Delete") { _, _ -> f.delete(); view.next() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
