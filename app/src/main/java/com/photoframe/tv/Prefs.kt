package com.photoframe.tv

import android.content.Context
import java.security.SecureRandom

object Prefs {
    val INTERVALS = intArrayOf(5, 8, 15, 30, 60)
    private fun p(c: Context) = c.getSharedPreferences("frame", Context.MODE_PRIVATE)

    fun intervalSec(c: Context) = p(c).getInt("interval", 8)
    fun nextInterval(c: Context): Int {
        val cur = intervalSec(c)
        val n = INTERVALS[(INTERVALS.indexOf(cur) + 1) % INTERVALS.size]
        p(c).edit().putInt("interval", n).apply()
        return n
    }

    /** Random secret embedded in the QR URL so only people who can see the TV can upload. */
    fun token(c: Context): String {
        p(c).getString("token", null)?.let { return it }
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        val t = bytes.joinToString("") { "%02x".format(it) }
        p(c).edit().putString("token", t).apply()
        return t
    }
}
