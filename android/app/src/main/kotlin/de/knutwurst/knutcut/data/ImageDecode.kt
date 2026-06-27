package de.knutwurst.knutcut.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import de.knutwurst.knutcut.svgcore.RasterImage

/**
 * Decode a picked raster image (PNG/JPG/BMP/WebP/…) into a [RasterImage] of ARGB pixels for tracing.
 *
 * The image is downsampled so its long edge is at most [maxEdge] px — tracing a few-hundred-pixel
 * image is fast and the cut detail is plenty, while a full-resolution photo would be slow and blow
 * up memory. JPEG EXIF orientation is honoured so sideways phone photos trace upright.
 */
object ImageDecode {

    /** Returns null when the uri can't be opened or isn't a decodable image. */
    fun decode(resolver: ContentResolver, uri: Uri, maxEdge: Int = 768): RasterImage? {
        val (w0, h0) = readBounds(resolver, uri) ?: return null
        if (w0 <= 0 || h0 <= 0) return null

        // Power-of-two pre-downsample at decode time (cheap, low memory).
        var sample = 1
        while (maxOf(w0, h0) / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        bmp = applyExifOrientation(resolver, uri, bmp)

        // Exact scale-down for the remainder (inSampleSize only halves).
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge > maxEdge) {
            val s = maxEdge.toFloat() / longEdge
            val nw = (bmp.width * s).toInt().coerceAtLeast(1)
            val nh = (bmp.height * s).toInt().coerceAtLeast(1)
            bmp = Bitmap.createScaledBitmap(bmp, nw, nh, true)
        }

        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h) // packed ARGB (Color ints), matching RasterImage
        return RasterImage(w, h, px)
    }

    private fun readBounds(resolver: ContentResolver, uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    private fun applyExifOrientation(resolver: ContentResolver, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return runCatching { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }.getOrDefault(bmp)
    }
}
