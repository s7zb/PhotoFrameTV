package com.photoframe.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Local storage of photos in the app's private files dir (no storage permission needed). */
object PhotoStore {
    val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val counter = AtomicInteger()

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Oldest first. */
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name } ?: emptyList()

    fun newFile(ctx: Context, originalName: String?, tmp: Boolean = false): File {
        var ext = originalName?.substringAfterLast('.', "")?.lowercase() ?: ""
        if (ext !in EXTENSIONS) ext = "jpg"
        val base = "%013d_%03d".format(System.currentTimeMillis(), counter.incrementAndGet() % 1000)
        return File(dir(ctx), if (tmp) "$base.part" else "$base.$ext")
    }

    fun deleteAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** True if the file is a decodable image. */
    fun isImage(f: File): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return o.outWidth > 0 && o.outHeight > 0
    }

    /** Decode downsampled to roughly fit maxW x maxH, honouring EXIF rotation. */
    fun decode(f: File, maxW: Int, maxH: Int): Bitmap? {
        return try {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, b)
            if (b.outWidth <= 0) return null
            val big = maxOf(b.outWidth, b.outHeight)
            val small = minOf(b.outWidth, b.outHeight)
            val tBig = maxOf(maxW, maxH)
            val tSmall = minOf(maxW, maxH)
            var s = 1
            while (big / (s * 2) >= tBig && small / (s * 2) >= tSmall) s *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = BitmapFactory.decodeFile(f.path, o) ?: return null
            val deg = when (ExifInterface(f.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg == 0f) bmp else {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
                if (r !== bmp) bmp.recycle()
                r
            }
        } catch (e: Throwable) { null }
    }
}
