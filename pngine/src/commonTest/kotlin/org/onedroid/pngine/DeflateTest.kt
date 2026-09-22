package org.onedroid.pngine

import org.onedroid.pngine.internal.Deflate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Round-trips the pure-Kotlin compressor through [TestInflate], which also
 * verifies the zlib header and Adler-32 trailer. The JVM source set repeats
 * these cases against `java.util.zip.Inflater`.
 */
class DeflateTest {

    private fun roundTrip(data: ByteArray, level: Int) {
        val compressed = Deflate.zlibCompress(data, level)
        val restored = TestInflate.zlibDecompress(compressed)
        assertContentEquals(data, restored, "level $level, ${data.size} bytes")
    }

    private fun allLevels(data: ByteArray) {
        for (level in 0..9) roundTrip(data, level)
    }

    @Test
    fun `round-trips an empty input`() = allLevels(ByteArray(0))

    @Test
    fun `round-trips a single byte`() = allLevels(byteArrayOf(42))

    @Test
    fun `round-trips input shorter than the minimum match`() =
        allLevels(byteArrayOf(1, 2))

    @Test
    fun `round-trips a long run of one byte`() =
        allLevels(ByteArray(100_000) { 7 })

    @Test
    fun `round-trips repeating text`() {
        val unit = "the quick brown fox jumps over the lazy dog. "
        val text = buildString { repeat(500) { append(unit) } }
        allLevels(ByteArray(text.length) { text[it].code.toByte() })
    }

    @Test
    fun `round-trips incompressible noise`() {
        val random = Xorshift(0x5EED)
        allLevels(ByteArray(40_000) { random.next().toByte() })
    }

    @Test
    fun `round-trips data larger than one stored block`() {
        val random = Xorshift(99)
        // Past 65535 bytes the stored path has to split into several blocks.
        allLevels(ByteArray(200_000) { (random.next() and 0x03).toByte() })
    }

    @Test
    fun `round-trips matches at the very end of the input`() {
        // The tail repeats the head, so the final match runs to the last byte.
        val head = ByteArray(1000) { (it % 17).toByte() }
        allLevels(head + head)
    }

    @Test
    fun `round-trips distances that span the whole window`() {
        val random = Xorshift(7)
        val prefix = ByteArray(32_000) { random.next().toByte() }
        val filler = ByteArray(700) { 0 }
        allLevels(prefix + filler + prefix.copyOfRange(0, 600))
    }

    @Test
    fun `round-trips image-like rows`() {
        // Row-major gradient with a filter byte per row, as PNG feeds it.
        val width = 128
        val height = 96
        val data = ByteArray((width + 1) * height) { i ->
            val column = i % (width + 1)
            if (column == 0) 0 else ((i / (width + 1) + column) % 64).toByte()
        }
        allLevels(data)
    }

    @Test
    fun `compresses repetitive data well below its input size`() {
        val data = ByteArray(50_000) { (it % 8).toByte() }
        val compressed = Deflate.zlibCompress(data, 9)
        assertTrue(
            compressed.size < data.size / 20,
            "expected heavy compression, got ${compressed.size} from ${data.size}",
        )
    }

    @Test
    fun `never expands incompressible data by much`() {
        val random = Xorshift(1234)
        val data = ByteArray(30_000) { random.next().toByte() }
        val compressed = Deflate.zlibCompress(data, 9)
        assertTrue(
            compressed.size < data.size + data.size / 100 + 64,
            "stored fallback should cap the overhead, got ${compressed.size}",
        )
    }

    @Test
    fun `higher levels do not produce larger output than level 1`() {
        val data = ByteArray(60_000) { ((it * 31) % 251).toByte() }
        val fast = Deflate.zlibCompress(data, 1).size
        val best = Deflate.zlibCompress(data, 9).size
        assertTrue(best <= fast, "level 9 produced $best bytes, level 1 produced $fast")
    }

    /** Deterministic PRNG, so a failure is reproducible on every target. */
    private class Xorshift(seed: Int) {
        private var state = if (seed == 0) 1 else seed

        fun next(): Int {
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            return state
        }
    }
}
