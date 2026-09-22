package org.onedroid.sample.png

/**
 * The platform's own 32-bit PNG encoder, used purely as a size baseline to
 * compare Pngine against. Not every target has one — returns null there.
 *
 * Pngine itself needs no expect/actual: it is pure Kotlin and runs from
 * common code on every target.
 */
expect fun platformPngBaseline(pixels: IntArray, width: Int, height: Int): ByteArray?

/** Name of the baseline encoder, for the UI. */
expect val platformPngName: String
