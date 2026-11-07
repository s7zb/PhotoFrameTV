package com.s7zb.photoframetv

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Tiny HTTP server (no external libraries).
 *   GET  /        -> mobile upload page (assets/upload.html)
 *   GET  /count   -> {"count":N}
 *   POST /upload?name=<filename>   body = raw image bytes
 */
class UploadServer(private val appContext: Context, private val onChanged: () -> Unit) {
    @Volatile var port: Int = 0
        private set
    @Volatile private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null

    val isRunning: Boolean get() = serverSocket?.isClosed == false

    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true
        var ss: ServerSocket? = null
        for (p in (8080..8090).toList() + 0) {      // 0 = any free port as last resort
            val cand = ServerSocket()
            try {
                cand.reuseAddress = true
                cand.bind(InetSocketAddress(p))
                ss = cand
                break
            } catch (e: IOException) {
                try { cand.close() } catch (e2: IOException) { }
            }
        }
        val server: ServerSocket = ss ?: return false
        serverSocket = server
        port = server.localPort
        val executor = Executors.newCachedThreadPool()
        pool = executor
        thread(name = "upload-accept", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val s = server.accept()
                    executor.execute { handle(s) }
                } catch (e: IOException) {
                    break
                }
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        try { serverSocket?.close() } catch (e: IOException) { }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 30_000
                val input = BufferedInputStream(s.getInputStream(), 64 * 1024)
                val out = BufferedOutputStream(s.getOutputStream())

                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val target = parts[1]

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }

                val path = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))

                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        val html = appContext.assets.open("upload.html").use { it.readBytes() }
                        send(out, 200, "OK", "text/html; charset=utf-8", html)
                    }
                    method == "GET" && path == "/count" -> {
                        val n = PhotoStore.list().size
                        send(out, 200, "OK", "application/json", JSONObject().put("count", n).toString().toByteArray())
                    }
                    method == "GET" && path == "/favicon.ico" -> send(out, 204, "No Content", "text/plain", ByteArray(0))
                    method == "POST" && path == "/upload" -> {
                        val length = headers["content-length"]?.toLongOrNull()
                        if (length == null) {
                            sendJson(out, 411, "Length Required", false, "Missing Content-Length")
                        } else if (length > PhotoStore.MAX_BYTES) {
                            sendJson(out, 413, "Payload Too Large", false, "File is larger than 80 MB")
                        } else {
                            val name = query["name"] ?: "photo.jpg"
                            when (val r = PhotoStore.save(name, headers["content-type"], input, length, query["caption"])) {
                                is PhotoStore.Result.Ok -> {
                                    sendJson(out, 200, "OK", true, r.name)
                                    onChanged()
                                }
                                is PhotoStore.Result.Fail -> sendJson(out, r.code, "Error", false, r.message)
                            }
                        }
                    }
                    else -> send(out, 404, "Not Found", "text/plain", "Not found".toByteArray())
                }
            } catch (e: Exception) {
                // client went away or timed out; nothing to do
            }
        }
    }

    private fun sendJson(out: OutputStream, code: Int, status: String, ok: Boolean, msg: String) {
        val j = JSONObject().put("ok", ok).put(if (ok) "name" else "error", msg)
        send(out, code, status, "application/json", j.toString().toByteArray(Charsets.UTF_8))
    }

    private fun send(out: OutputStream, code: Int, status: String, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun parseQuery(q: String): Map<String, String> {
        val m = HashMap<String, String>()
        if (q.isEmpty()) return m
        for (pair in q.split("&")) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            try {
                m[URLDecoder.decode(pair.substring(0, i), "UTF-8")] = URLDecoder.decode(pair.substring(i + 1), "UTF-8")
            } catch (e: Exception) { }
        }
        return m
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 8192) return null
        }
    }
}
