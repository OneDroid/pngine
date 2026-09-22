package au.com.mactechnology.pngine

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the encoder through [Pngine.encodePixels], which touches no
 * Android types, and verifies the emitted bytes with [TestPngReader].
 */
class Png8EncoderTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `emits a structurally valid indexed PNG`() {
        val pixels = IntArray(4) { argb(255, 255, 0, 0) }

        val png = TestPngReader.read(Pngine.encodePixels(pixels, 2, 2))

        assertEquals(2, png.width)
        assertEquals(2, png.height)
        assertEquals(8, png.bitDepth)
        assertEquals(3, png.colorType)
    }

    @Test
    fun `round-trips dimensions`() {
        val width = 37
        val height = 19
        val pixels = IntArray(width * height) { argb(255, it % 256, 64, 128) }

        val png = TestPngReader.read(Pngine.encodePixels(pixels, width, height))

        assertEquals(width, png.width)
        assertEquals(height, png.height)
    }

    @Test
    fun `reproduces a flat colour exactly`() {
        val expected = argb(255, 12, 200, 90)
        val png = TestPngReader.read(Pngine.encodePixels(IntArray(64) { expected }, 8, 8))

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals("pixel ($x,$y)", expected, png.argbAt(x, y))
            }
        }
    }

    @Test
    fun `preserves fully transparent pixels`() {
        val width = 16
        val height = 16
        // Left half opaque, right half fully transparent.
        val pixels = IntArray(width * height) { i ->
            if (i % width < width / 2) argb(255, 200, 30, 30) else 0
        }

        val png = TestPngReader.read(Pngine.encodePixels(pixels, width, height))

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x < width / 2) {
                    assertEquals("opaque pixel ($x,$y)", 255, png.alphaAt(x, y))
                } else {
                    assertEquals("transparent pixel ($x,$y)", 0, png.alphaAt(x, y))
                }
            }
        }
    }

    /**
     * Regression guard for strided transparency detection. A histogram-only
     * alpha check misses transparent pixels that land on skipped
     * coordinates, leaving no transparent palette entry and turning those
     * pixels opaque.
     */
    @Test
    fun `detects transparency even when the histogram subsamples`() {
        val width = 8
        val height = 8
        // Lone transparent pixel at (1,1), which a stride of 2 skips.
        val pixels = IntArray(width * height) { argb(255, 90, 90, 90) }
        pixels[1 * width + 1] = 0

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(sampleStride = 2, dithering = false),
            )
        )

        assertEquals(
            "the lone transparent pixel must survive",
            0,
            png.alphaAt(1, 1),
        )
    }

    /**
     * Regression guard for the palette lookup cache. A cache keyed only by
     * a hash slot, with no key comparison, returns another colour's palette
     * index on collision. A 256-entry palette over a smooth gradient leaves
     * only small residuals, so a large outlier means a bad lookup.
     */
    @Test
    fun `non-dithered remap stays close to the source`() {
        val width = 256
        val height = 64
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            argb(255, x, (x + y) and 0xFF, 255 - x)
        }
        val source = pixels.copyOf()

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(dithering = false, maxColors = 256),
            )
        )

        var worst = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val expected = source[y * width + x]
                val actual = png.argbAt(x, y)
                for (shift in intArrayOf(16, 8, 0)) {
                    val delta = abs(
                        ((expected ushr shift) and 0xFF) - ((actual ushr shift) and 0xFF)
                    )
                    if (delta > worst) worst = delta
                }
            }
        }

        assertTrue("worst per-channel error was $worst, expected <= 48", worst <= 48)
    }

    @Test
    fun `honours a reduced palette size`() {
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { i ->
            argb(255, (i * 7) and 0xFF, (i * 13) and 0xFF, (i * 29) and 0xFF)
        }

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(maxColors = 8, dithering = false),
            )
        )

        val distinct = png.distinctColors()
        assertTrue("expected at most 8 colours, found ${distinct.size}", distinct.size <= 8)
    }

    @Test
    fun `omits the tRNS chunk for fully opaque images`() {
        val pixels = IntArray(64) { argb(255, 10, 20, 30) }
        val bytes = Pngine.encodePixels(pixels, 8, 8)

        assertTrue("opaque image must not carry tRNS", !containsChunk(bytes, "tRNS"))
    }

    @Test
    fun `writes a tRNS chunk when transparency is present`() {
        val pixels = IntArray(64) { i -> if (i == 0) 0 else argb(255, 10, 20, 30) }
        val bytes = Pngine.encodePixels(pixels, 8, 8)

        assertTrue("transparent image must carry tRNS", containsChunk(bytes, "tRNS"))
    }

    @Test
    fun `dithered output decodes cleanly`() {
        val width = 64
        val height = 64
        val pixels = IntArray(width * height) { i -> argb(255, i % width * 4, 100, 180) }

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(maxColors = 16, ditheringMethod = DitheringMethod.FLOYD_STEINBERG),
            )
        )

        assertEquals(width, png.width)
        assertEquals(height, png.height)
    }

    @Test
    fun `jarvis judice ninke output decodes cleanly`() {
        val width = 48
        val height = 32
        val pixels = IntArray(width * height) { i -> argb(255, 40, i % width * 5, 200) }

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(
                    maxColors = 32,
                    ditheringMethod = DitheringMethod.JARVIS_JUDICE_NINKE,
                ),
            )
        )

        assertEquals(width, png.width)
        assertEquals(height, png.height)
    }

    /**
     * A single-axis gradient produces boxes that are flat on two channels.
     * Multiplying bare ranges collapses their split priority to zero, so
     * they never subdivide and the palette stays coarse.
     */
    @Test
    fun `splits boxes that are flat on some axes`() {
        val width = 256
        val height = 8
        // Red varies, green and blue are constant.
        val pixels = IntArray(width * height) { i -> argb(255, i % width, 128, 64) }

        val png = TestPngReader.read(
            Pngine.encodePixels(
                pixels, width, height,
                PngineOptions(maxColors = 64, dithering = false),
            )
        )

        val distinct = png.distinctColors().size
        assertTrue("expected a rich palette, got $distinct colours", distinct >= 32)
    }

    @Test
    fun `rejects an inconsistent pixel count`() {
        val error = runCatching { Pngine.encodePixels(IntArray(10), 4, 4) }.exceptionOrNull()
        assertTrue(
            "expected IllegalArgumentException, got $error",
            error is IllegalArgumentException,
        )
    }

    @Test
    fun `rejects an out-of-range palette size`() {
        val error = runCatching { PngineOptions(maxColors = 1) }.exceptionOrNull()
        assertTrue(
            "expected IllegalArgumentException, got $error",
            error is IllegalArgumentException,
        )
    }

    private fun containsChunk(bytes: ByteArray, type: String): Boolean {
        val needle = type.toByteArray(Charsets.US_ASCII)
        for (i in 8..bytes.size - needle.size) {
            if (bytes.copyOfRange(i, i + needle.size).contentEquals(needle)) return true
        }
        return false
    }
}
