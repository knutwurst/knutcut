package de.knutwurst.knutcut.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import de.knutwurst.knutcut.svgcore.CropRect
import de.knutwurst.knutcut.svgcore.RasterImage
import kotlin.math.roundToInt

/**
 * Decode a picked raster image (PNG/JPG/BMP/WebP/…) into a [RasterImage] of ARGB pixels for tracing.
 *
 * The image is downsampled so its long edge is at most [maxEdge] px — tracing a few-hundred-pixel
 * image is fast and the cut detail is plenty, while a full-resolution photo would be slow and blow
 * up memory. JPEG EXIF orientation is honored so sideways phone photos trace upright.
 */
object ImageDecode {

    /** Returns null when the uri can't be opened or isn't a decodable image. */
    fun decode(resolver: ContentResolver, uri: Uri, maxEdge: Int = 1200): RasterImage? {
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

    /** The source file's stored (pre-EXIF) pixel size and its EXIF orientation. */
    data class SourceInfo(val nativeWidth: Int, val nativeHeight: Int, val orientation: Int)

    fun readSourceInfo(resolver: ContentResolver, uri: Uri): SourceInfo? {
        val (w, h) = readBounds(resolver, uri) ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return SourceInfo(w, h, orientation)
    }

    /**
     * Map a crop rectangle from DISPLAY space (the EXIF-corrected orientation the user cropped in) to
     * the file's NATIVE pixel space (what [BitmapRegionDecoder] reads). Returns [x, y, w, h] in native
     * coords, or null for the flip/transpose orientations we don't attempt (caller falls back).
     */
    fun cropDisplayToNative(orientation: Int, nW: Int, nH: Int, dx: Int, dy: Int, dw: Int, dh: Int): IntArray? = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_180 -> intArrayOf(nW - (dx + dw), nH - (dy + dh), dw, dh)
        ExifInterface.ORIENTATION_ROTATE_90 -> intArrayOf(dy, nH - (dx + dw), dh, dw)
        ExifInterface.ORIENTATION_ROTATE_270 -> intArrayOf(nW - (dy + dh), dx, dh, dw)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> null
        else -> intArrayOf(dx, dy, dw, dh) // NORMAL / UNDEFINED
    }

    /**
     * Decode just the cropped region from the original file at (near) native resolution, so a small
     * crop traces with far more detail than cropping the downsampled preview would give. [crop] is in
     * the downsampled-display coords of the preview ([dispW] x [dispH]). Returns null when the region
     * can't be decoded (unsupported format / orientation), so the caller falls back to the preview.
     */
    fun decodeRegion(resolver: ContentResolver, uri: Uri, src: SourceInfo, dispW: Int, dispH: Int, crop: CropRect, maxEdge: Int = 2000): RasterImage? {
        if (dispW <= 0 || dispH <= 0) return null
        val rot90 = src.orientation == ExifInterface.ORIENTATION_ROTATE_90 || src.orientation == ExifInterface.ORIENTATION_ROTATE_270
        val fullDispW = if (rot90) src.nativeHeight else src.nativeWidth
        val fullDispH = if (rot90) src.nativeWidth else src.nativeHeight
        // Crop (downsampled display) -> full display.
        val scaleX = fullDispW.toDouble() / dispW; val scaleY = fullDispH.toDouble() / dispH
        val dx = (crop.x * scaleX).toInt().coerceIn(0, fullDispW)
        val dy = (crop.y * scaleY).toInt().coerceIn(0, fullDispH)
        val dw = ((crop.x + crop.w) * scaleX).roundToInt().coerceIn(0, fullDispW) - dx
        val dh = ((crop.y + crop.h) * scaleY).roundToInt().coerceIn(0, fullDispH) - dy
        if (dw <= 0 || dh <= 0) return null
        // Full display -> native rect.
        val nr = cropDisplayToNative(src.orientation, src.nativeWidth, src.nativeHeight, dx, dy, dw, dh) ?: return null
        val nx = nr[0].coerceIn(0, src.nativeWidth); val ny = nr[1].coerceIn(0, src.nativeHeight)
        val nw = nr[2].coerceIn(1, src.nativeWidth - nx); val nh = nr[3].coerceIn(1, src.nativeHeight - ny)

        var sample = 1
        while (maxOf(nw, nh) / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val rect = Rect(nx, ny, nx + nw, ny + nh)

        val decoder = runCatching {
            @Suppress("DEPRECATION")
            resolver.openInputStream(uri)?.use { BitmapRegionDecoder.newInstance(it, false) }
        }.getOrNull() ?: return null
        var bmp = runCatching { decoder.decodeRegion(rect, opts) }.getOrNull()
        decoder.recycle()
        if (bmp == null) return null

        // Use the SAME orientation that drove the rect mapping (not a second EXIF read that could differ).
        bmp = applyExifOrientation(bmp, src.orientation)
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge > maxEdge) {
            val s = maxEdge.toFloat() / longEdge
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        }
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
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
        return applyExifOrientation(bmp, orientation)
    }

    /** Rotate/flip [bmp] for a known EXIF [orientation]. Used with the captured orientation so the
     *  region rect mapping and the rotation can never disagree from two independent EXIF reads. */
    private fun applyExifOrientation(bmp: Bitmap, orientation: Int): Bitmap {
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
