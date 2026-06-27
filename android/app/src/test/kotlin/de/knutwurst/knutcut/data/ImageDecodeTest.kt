package de.knutwurst.knutcut.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-math tests for the EXIF crop mapping used by the native-resolution region re-decode. The
 * crop is selected in DISPLAY space (EXIF-corrected); these map it back to the file's NATIVE pixel
 * space for BitmapRegionDecoder. Orientation constants (ExifInterface): 1 normal, 3 = 180,
 * 6 = 90° CW, 8 = 270° CW; 2/4/5/7 (flip/transpose) are unsupported → null.
 */
class ImageDecodeTest {

    // Native image is 100 x 60 (landscape). For 90/270 the display is 60 x 100 (portrait).
    private val nW = 100; private val nH = 60

    @Test fun normalIsIdentity() {
        assertArrayEquals(intArrayOf(10, 20, 30, 40), ImageDecode.cropDisplayToNative(1, nW, nH, 10, 20, 30, 40))
    }

    @Test fun rotate180MirrorsBothAxes() {
        // x: 100-(10+30)=60, y: 60-(20+40)=0, w/h unchanged.
        assertArrayEquals(intArrayOf(60, 0, 30, 40), ImageDecode.cropDisplayToNative(3, nW, nH, 10, 20, 30, 40))
    }

    @Test fun rotate90MapsDisplayRectToNative() {
        // display (10,20,30,40) in a 60x100 portrait → native (dy, nH-(dx+dw), dh, dw).
        assertArrayEquals(intArrayOf(20, 20, 40, 30), ImageDecode.cropDisplayToNative(6, nW, nH, 10, 20, 30, 40))
    }

    @Test fun rotate270MapsDisplayRectToNative() {
        // native (nW-(dy+dh), dx, dh, dw).
        assertArrayEquals(intArrayOf(40, 10, 40, 30), ImageDecode.cropDisplayToNative(8, nW, nH, 10, 20, 30, 40))
    }

    @Test fun flipAndTransposeAreUnsupported() {
        assertNull(ImageDecode.cropDisplayToNative(2, nW, nH, 10, 20, 30, 40)) // flip horizontal
        assertNull(ImageDecode.cropDisplayToNative(4, nW, nH, 10, 20, 30, 40)) // flip vertical
        assertNull(ImageDecode.cropDisplayToNative(5, nW, nH, 10, 20, 30, 40)) // transpose
        assertNull(ImageDecode.cropDisplayToNative(7, nW, nH, 10, 20, 30, 40)) // transverse
    }
}
