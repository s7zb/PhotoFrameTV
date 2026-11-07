package com.s7zb.photoframetv

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/** Photos live in the app's private files dir, so they persist across app restarts. */
object PhotoStore {
    sealed class Result {
        data class Ok(val name: String) : Result()
        data class Fail(val code: Int, val message: String) : Result()
    }

    const val MAX_BYTES = 80L * 1024 * 1024
    private val ALLOWED = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val PREFIX = Regex("^\\d+_\\d+_")
    private val counter = AtomicLong(0)

    @Volatile private var dirRef: File? = null
    val dir: File get() = dirRef ?: error("PhotoStore.init not called")

    @Synchronized
    fun init(ctx: Context) {
        if (dirRef == null) {
            val d = File(ctx.applicationContext.filesDir, "photos")
            d.mkdirs()
            dirRef = d
            // clean up partial uploads left by a crash
            d.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    fun list(): List<File> =
        (dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".cap") } ?: emptyArray()).sortedBy { it.name }

    fun displayName(f: File): String = f.name.replace(PREFIX, "")

    fun delete(f: File): Boolean {
        try { File(f.path + ".cap").delete() } catch (e: Exception) { }
        return f.delete()
    }

    private fun cleanCaption(raw: String?): String =
        (raw ?: "").replace(Regex("\\p{Cntrl}"), " ").replace(Regex("\\s+"), " ").trim().take(120)

    /** Caption typed on the phone page if there is one, otherwise a readable version of the file name. */
    fun captionFor(f: File): String {
        try {
            val c = File(f.path + ".cap")
            if (c.isFile) {
                val t = c.readText(Charsets.UTF_8).trim()
                if (t.isNotEmpty()) return t
            }
        } catch (e: Exception) { }
        return displayName(f).substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    }

    private fun extFor(contentType: String?): String = when {
        contentType == null -> ".jpg"
        contentType.contains("png") -> ".png"
        contentType.contains("webp") -> ".webp"
        contentType.contains("gif") -> ".gif"
        contentType.contains("bmp") -> ".bmp"
        contentType.contains("heic") || contentType.contains("heif") -> ".heic"
        else -> ".jpg"
    }

    private fun safeName(raw: String, contentType: String?): String {
        var n = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        n = n.replace(Regex("[^A-Za-z0-9._ -]"), "_").trimStart('.')
        if (n.length > 80) n = n.takeLast(80).trimStart('.')
        if (n.isEmpty()) n = "photo"
        val ext = n.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED) n += extFor(contentType)
        return n
    }

    private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")

    /** True if the file starts with an ISO-BMFF 'ftyp' box carrying a HEIC/HEIF brand. */
    private fun looksLikeHeif(f: File): Boolean = try {
        val b = ByteArray(12)
        val n = f.inputStream().use { it.read(b) }
        n == 12 && String(b, 4, 4, Charsets.ISO_8859_1) == "ftyp" &&
            String(b, 8, 4, Charsets.ISO_8859_1).lowercase() in HEIF_BRANDS
    } catch (e: Exception) { false }

    /** Returns null if Android can decode the image, otherwise a Fail with a specific message. */
    private fun checkDecodable(f: File): Result.Fail? {
        val heif = looksLikeHeif(f)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var ok = bounds.outWidth > 0 && bounds.outHeight > 0
        if (ok) {
            // real (heavily down-sampled) decode: catches files whose header parses but pixels cannot be read
            try {
                val t = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })
                if (t == null) ok = false else t.recycle()
            } catch (e: OutOfMemoryError) { /* decodable, just short on memory right now */ }
        }
        if (ok) return null
        return if (heif) {
            Result.Fail(415, "This TV can't open HEIC/HEIF photos. On iPhone choose Settings > Camera > Formats > Most Compatible, or share the photo as JPEG.")
        } else {
            Result.Fail(415, "This file is damaged or is not a supported photo (use JPEG, PNG, WebP, GIF or BMP).")
        }
    }

    /** Streams exactly [length] bytes from [input] into a new photo file and validates it is a decodable image. */
    fun save(rawName: String, contentType: String?, input: InputStream, length: Long, caption: String? = null): Result {
        if (length <= 0) return Result.Fail(400, "Empty file")
        if (length > MAX_BYTES) return Result.Fail(413, "File is larger than 80 MB")
        if (dir.usableSpace < length + 50L * 1024 * 1024) return Result.Fail(507, "Not enough free space on the TV")

        val tmp = File(dir, "tmp_${System.nanoTime()}.part")
        try {
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) {
                        tmp.delete()
                        return Result.Fail(400, "Upload was interrupted")
                    }
                    out.write(buf, 0, n)
                    remaining -= n
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            return Result.Fail(500, "Could not write file: ${e.message}")
        }

        val problem = checkDecodable(tmp)
        if (problem != null) {
            tmp.delete()
            return problem
        }

        val finalName = "${System.currentTimeMillis()}_${String.format("%06d", counter.incrementAndGet())}_${safeName(rawName, contentType)}"
        val dest = File(dir, finalName)
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            return Result.Fail(500, "Could not save file")
        }
        val cap = cleanCaption(caption)
        if (cap.isNotEmpty()) {
            try { File(dir, "$finalName.cap").writeText(cap, Charsets.UTF_8) } catch (e: Exception) { /* caption is optional */ }
        }
        return Result.Ok(displayName(dest))
    }
}
