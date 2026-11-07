package com.s7zb.photoframetv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/** Memory-efficient decoding: always sub-samples to roughly the size it will be shown at, and applies EXIF rotation. */
object ImageLoader {
    fun decode(file: File, maxW: Int, maxH: Int): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = exifOrientation(file)
            val swap = orientation in 5..8
            val srcW = if (swap) bounds.outHeight else bounds.outWidth
            val srcH = if (swap) bounds.outWidth else bounds.outHeight

            var sample = 1
            while (srcW / (sample * 2) >= maxW && srcH / (sample * 2) >= maxH) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(file.path, opts) ?: return null
            val m = Matrix()
            when (orientation) {
                2 -> m.postScale(-1f, 1f)
                3 -> m.postRotate(180f)
                4 -> m.postScale(1f, -1f)
                5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                6 -> m.postRotate(90f)
                7 -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
                8 -> m.postRotate(-90f)
                else -> return bmp
            }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            return rotated
        } catch (e: OutOfMemoryError) {
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
    } catch (e: Exception) {
        1
    }
}
