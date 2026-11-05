package com.photoframe.tv

import android.content.Context
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Tiny dependency-free HTTP server.
 *   GET  /                      -> mobile upload page (assets/upload.html)
 *   GET  /api/count?t=TOKEN     -> {"count":N}
 *   POST /api/upload?t=TOKEN&name=x.jpg   (raw image bytes as body) -> {"ok":true}
 * Each photo is a separate request, so the browser gets real upload progress events.
 */
class PhotoServer(
    private val ctx: Context,
    private val token: String,
    private val onPhoto: (File) -> Unit
) {
    @Volatile private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null
    @Volatile var port = 0
        private set

    companion object {
        const val MAX_UPLOAD = 60L * 1024 * 1024
    }

    @Synchronized
    fun start(): Boolean {
        if (serverSocket != null) return true
        var s: ServerSocket? = null
        for (p in 8080..8099) {
            try { s = ServerSocket(p); port = p; break } catch (_: IOException) {}
        }
        val server = s ?: return false
        serverSocket = server
        val ex = Executors.newCachedThreadPool()
        pool = ex
        thread(name = "photo-accept", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val sock = server.accept()
                    ex.execute { handle(sock) }
                } catch (_: IOException) { break } catch (_: java.util.concurrent.RejectedExecutionException) { break }
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        pool?.shutdownNow()
        pool = null
    }

    // ---------------------------------------------------------------- request handling

    private fun handle(sock: Socket) {
        sock.use {
            try {
                sock.soTimeout = 30_000
                val input = BufferedInputStream(sock.getInputStream(), 65536)
                val out = sock.getOutputStream()

                val reqLine = readLine(input) ?: return
                val parts = reqLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val target = parts[1]

                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(input) ?: return
                    if (l.isEmpty()) break
                    val i = l.indexOf(':')
                    if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }

                val qi = target.indexOf('?')
                val path = if (qi >= 0) target.substring(0, qi) else target
                val query = HashMap<String, String>()
                if (qi >= 0) target.substring(qi + 1).split('&').forEach {
                    val e = it.indexOf('=')
                    if (e > 0) query[it.substring(0, e)] = try { URLDecoder.decode(it.substring(e + 1), "UTF-8") } catch (_: Exception) { "" }
                }

                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        val html = ctx.assets.open("upload.html").use { it.readBytes() }
                        respond(out, 200, "OK", "text/html; charset=utf-8", html)
                    }
                    method == "GET" && path == "/api/count" -> {
                        if (query["t"] != token) return forbidden(out)
                        respondJson(out, 200, """{"count":${PhotoStore.list(ctx).size}}""")
                    }
                    method == "POST" && path == "/api/upload" -> {
                        if (query["t"] != token) return forbidden(out)
                        receiveUpload(input, out, headers, query["name"])
                    }
                    else -> respondJson(out, 404, """{"ok":false,"error":"Not found"}""")
                }
            } catch (_: Exception) {
                // connection dropped / timeout: nothing more to do
            }
        }
    }

    private fun forbidden(out: OutputStream) =
        respondJson(out, 403, """{"ok":false,"error":"Invalid or missing token. Scan the QR code on the TV again."}""")

    private fun receiveUpload(input: InputStream, out: OutputStream, headers: Map<String, String>, name: String?) {
        val len = headers["content-length"]?.toLongOrNull()
        if (len == null || len <= 0) return respondJson(out, 411, """{"ok":false,"error":"Content-Length required"}""")
        if (len > MAX_UPLOAD) return respondJson(out, 413, """{"ok":false,"error":"File too large (max 60 MB)"}""")

        val tmp = PhotoStore.newFile(ctx, name, tmp = true)
        try {
            FileOutputStream(tmp).use { fos ->
                val buf = ByteArray(64 * 1024)
                var remaining = len
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw IOException("Connection closed early")
                    fos.write(buf, 0, n)
                    remaining -= n
                }
            }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, o)
            if (o.outWidth <= 0) {
                tmp.delete()
                return respondJson(out, 415, """{"ok":false,"error":"Unsupported image format"}""")
            }
            val dest = PhotoStore.newFile(ctx, name)
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
            onPhoto(dest)
            respondJson(out, 200, """{"ok":true,"file":"${dest.name}"}""")
        } catch (e: Exception) {
            tmp.delete()
            try { respondJson(out, 500, """{"ok":false,"error":"Upload failed"}""") } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 8192) throw IOException("Header too long")
        }
        return sb.toString()
    }

    private fun respondJson(out: OutputStream, code: Int, json: String) =
        respond(out, code, if (code == 200) "OK" else "Error", "application/json; charset=utf-8", json.toByteArray())

    private fun respond(out: OutputStream, code: Int, status: String, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }
}
