package org.onedroid.sample.png

/** Subset of `PngineOptions` the demo UI exposes. */
data class DemoOptions(
    val maxColors: Int = 256,
    val dithering: Boolean = true,
)

/**
 * Bridge to the Pngine library.
 *
 * Pngine is an Android library (it takes `android.graphics.Bitmap` and ships
 * as an AAR), so only the Android target has a real implementation. Every
 * other target reports [isSupported] `false` and the UI degrades to an
 * explanation.
 */
expect object PngEncoder {

    /** True only where the Pngine AAR is on the classpath. */
    val isSupported: Boolean

    /** Why encoding is unavailable, or null when [isSupported]. */
    val unsupportedReason: String?

    /** PNG-8 via `Pngine.encodePixels`. Throws when [isSupported] is false. */
    fun encodePng8(pixels: IntArray, width: Int, height: Int, options: DemoOptions): ByteArray

    /** 32-bit PNG via the platform encoder, for size comparison. */
    fun encodeBaselinePng(pixels: IntArray, width: Int, height: Int): ByteArray
}
