package org.onedroid.sample.png

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Generates the demo source image: a smooth two-axis colour gradient behind a
 * soft-edged disc. Gradients band badly under naive quantization and the disc
 * has partial alpha, so the result exercises both dithering and the alpha
 * handling in Pngine.
 */
object TestImage {

    const val WIDTH: Int = 320
    const val HEIGHT: Int = 320

    /** Packed ARGB, row-major, alpha in the high byte. */
    fun argbPixels(): IntArray {
        val pixels = IntArray(WIDTH * HEIGHT)
        val cx = WIDTH / 2f
        val cy = HEIGHT / 2f
        val radius = minOf(WIDTH, HEIGHT) / 2f - 8f

        for (y in 0 until HEIGHT) {
            val fy = y / (HEIGHT - 1f)
            for (x in 0 until WIDTH) {
                val fx = x / (WIDTH - 1f)

                val r = (255f * fx).roundToInt()
                val g = (255f * (1f - fy)).roundToInt()
                val b = (255f * (0.35f + 0.65f * (fx * fy))).roundToInt()

                // Soft 12px edge on the disc so alpha is not just 0 or 255.
                val d = hypot(x - cx, y - cy)
                val a = when {
                    d <= radius - 12f -> 255
                    d >= radius -> 0
                    else -> (255f * ((radius - d) / 12f)).roundToInt()
                }

                pixels[y * WIDTH + x] =
                    (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }
}
