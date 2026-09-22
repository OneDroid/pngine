package org.onedroid.sample.png

private const val REASON =
    "Pngine is an Android library compiled to JVM bytecode; it cannot run in the browser (Wasm)."

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
