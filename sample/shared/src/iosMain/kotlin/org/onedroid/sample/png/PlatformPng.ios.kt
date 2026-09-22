package org.onedroid.sample.png

// No synchronous platform PNG encoder to compare against here.
actual fun platformPngBaseline(pixels: IntArray, width: Int, height: Int): ByteArray? = null

actual val platformPngName: String = "none"
