package org.onedroid.sample.png

private const val REASON =
    "Pngine ships as an Android AAR and needs android.graphics, so it is not on the desktop (JVM) classpath."

actual object PngEncoder {

    actual val isSupported: Boolean = false

    actual val unsupportedReason: String? = REASON

    actual fun encodePng8(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: DemoOptions,
    ): ByteArray = throw UnsupportedOperationException(REASON)

    actual fun encodeBaselinePng(pixels: IntArray, width: Int, height: Int): ByteArray =
        throw UnsupportedOperationException(REASON)
}
