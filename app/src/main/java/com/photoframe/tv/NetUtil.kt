package com.photoframe.tv

import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    /** Best local IPv4 address (prefers Wi-Fi / Ethernet), or null when offline. */
    fun localIp(): String? {
        var fallback: String? = null
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) {
                        val n = ni.name.lowercase()
                        if (n.startsWith("wlan") || n.startsWith("eth")) return a.hostAddress
                        if (fallback == null) fallback = a.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return fallback
    }
}
