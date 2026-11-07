package com.s7zb.photoframetv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

val COLOR_BG = 0xFF0F1115.toInt()
val COLOR_PANEL = 0xFF222833.toInt()
val COLOR_ACCENT = 0xFF4F8CFF.toInt()
val COLOR_TEXT_DIM = 0xFF9AA4B5.toInt()

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** D-pad friendly button: clearly highlighted when focused. */
fun Context.tvButton(label: String): Button {
    val b = Button(this)
    b.text = label
    b.setAllCaps(false)
    b.textSize = 22f
    b.setTextColor(Color.WHITE)
    b.isFocusable = true
    b.isFocusableInTouchMode = false
    val r = dp(12).toFloat()
    val normal = GradientDrawable().apply { setColor(COLOR_PANEL); setCornerRadius(r) }
    val focused = GradientDrawable().apply {
        setColor(COLOR_ACCENT); setCornerRadius(r); setStroke(dp(4), Color.WHITE)
    }
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_focused), focused)
    sl.addState(intArrayOf(android.R.attr.state_pressed), focused)
    sl.addState(intArrayOf(), normal)
    b.background = sl
    b.setPadding(dp(24), dp(12), dp(24), dp(12))
    return b
}

object Prefs {
    val STEPS = intArrayOf(3, 5, 8, 10, 15, 30, 60)
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("photoframe", Context.MODE_PRIVATE)
    fun duration(ctx: Context): Int = sp(ctx).getInt("duration", 8)
    fun setDuration(ctx: Context, sec: Int) = sp(ctx).edit().putInt("duration", sec).apply()

    fun shuffle(ctx: Context): Boolean = sp(ctx).getBoolean("shuffle", false)
    fun setShuffle(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("shuffle", on).apply()
    fun captions(ctx: Context): Boolean = sp(ctx).getBoolean("captions", false)
    fun setCaptions(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("captions", on).apply()

    /** Keep the upload server running (foreground service) when the app is not on screen. Off by default. */
    fun background(ctx: Context): Boolean = sp(ctx).getBoolean("background", false)
    fun setBackground(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("background", on).apply()

    /** Move to the next/previous preset (dir = +1 / -1), clamped. Returns the new value. */
    fun stepDuration(current: Int, dir: Int): Int {
        var idx = STEPS.indexOfFirst { it >= current }
        if (idx < 0) idx = STEPS.size - 1
        idx = (idx + dir).coerceIn(0, STEPS.size - 1)
        return STEPS[idx]
    }
}

object NetUtil {
    /** Best guess at the TV's LAN IPv4 address (Wi-Fi or Ethernet). */
    fun localIp(): String? {
        try {
            val candidates = ArrayList<Pair<Int, String>>()
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val rank = when {
                            name.startsWith("wlan") -> 0
                            name.startsWith("eth") -> 1
                            else -> 2
                        }
                        candidates.add(rank to (addr.hostAddress ?: continue))
                    }
                }
            }
            return candidates.minByOrNull { it.first }?.second
        } catch (e: Exception) {
            return null
        }
    }
}

object QrUtil {
    fun make(text: String, size: Int): Bitmap {
        val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }
}
