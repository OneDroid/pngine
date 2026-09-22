package org.onedroid.sample.png

import android.graphics.Bitmap
import org.onedroid.pngine.Pngine
import org.onedroid.pngine.PngineOptions
import java.io.ByteArrayOutputStream

actual object PngEncoder {

    actual val isSupported: Boolean = true

    actual val unsupportedReason: String? = null

    actual fun encodePng8(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: DemoOptions,
    ): ByteArray = Pngine.encodePixels(
        // encodePixels normalises alpha in place; the caller keeps the source.
        pixels = pixels.copyOf(),
        width = width,
        height = height,
        options = PngineOptions(
            maxColors = options.maxColors,
            dithering = options.dithering,
        ),
    )

    actual fun encodeBaselinePng(pixels: IntArray, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
