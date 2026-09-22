package org.onedroid.sample

import org.onedroid.pngine.Pngine
import org.onedroid.pngine.PngineOptions
import org.onedroid.sample.png.TestImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the sample's own encoding path on every target the app ships to, so
 * "Pngine works here" is asserted rather than assumed.
 */
class PngineSampleTest {

    private val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    @Test
    fun `encodes the demo image to a PNG stream`() {
        val png = Pngine.encodePixels(
            pixels = TestImage.argbPixels(),
            width = TestImage.WIDTH,
            height = TestImage.HEIGHT,
            options = PngineOptions(maxColors = 64),
        )

        assertTrue(png.size > 8, "expected a non-trivial stream, got ${png.size} bytes")
        assertEquals(
            signature.toList(),
            png.copyOfRange(0, 8).toList(),
            "stream must start with the PNG signature",
        )

        val raw = TestImage.WIDTH * TestImage.HEIGHT * 4
        assertTrue(png.size < raw / 4, "expected heavy compression, got ${png.size} of $raw")
    }

    @Test
    fun `a smaller palette produces a smaller stream`() {
        val pixels = TestImage.argbPixels()
        val large = Pngine.encodePixels(
            pixels.copyOf(), TestImage.WIDTH, TestImage.HEIGHT,
            PngineOptions(maxColors = 256),
        ).size
        val small = Pngine.encodePixels(
            pixels.copyOf(), TestImage.WIDTH, TestImage.HEIGHT,
            PngineOptions(maxColors = 16),
        ).size

        assertTrue(small < large, "16 colours produced $small bytes, 256 produced $large")
    }
}
