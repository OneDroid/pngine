package org.onedroid.pngine

import org.onedroid.pngine.internal.Deflate
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * Cross-checks the pure-Kotlin compressor against zlib itself. The common
 * tests use an inflater written for this project; these use the platform's,
 * so a shared misreading of RFC 1951 cannot hide a bug.
 */
class DeflateJvmTest {

    private fun zlibInflate(compressed: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        InflaterOutputStream(out, Inflater()).use { it.write(compressed) }
        return out.toByteArray()
    }

    private fun roundTrip(data: ByteArray) {
        for (level in 0..9) {
            val restored = zlibInflate(Deflate.zlibCompress(data, level))
            assertContentEquals(data, restored, "level $level, ${data.size} bytes")
        }
    }

    @Test
    fun `zlib reads every level`() {
        roundTrip(ByteArray(0))
        roundTrip(byteArrayOf(0))
        roundTrip(ByteArray(300) { (it % 3).toByte() })
        roundTrip(ByteArray(120_000) { (it % 251).toByte() })
    }

    @Test
    fun `zlib reads random inputs of assorted sizes`() {
        val random = Random(20260922)
        for (size in listOf(1, 2, 3, 258, 259, 1024, 65_535, 65_536, 70_000)) {
            roundTrip(random.nextBytes(size))
        }
    }

    @Test
    fun `zlib reads highly repetitive input`() {
        val unit = "pngine ".encodeToByteArray()
        val data = ByteArray(80_000) { unit[it % unit.size] }
        roundTrip(data)
    }

    @Test
    fun `stays within a reasonable margin of zlib's own ratio`() {
        val data = ByteArray(200_000) { i -> ((i / 97) % 13 + (i % 7)).toByte() }

        val ours = Deflate.zlibCompress(data, 9).size

        val reference = ByteArrayOutputStream()
        val deflater = java.util.zip.Deflater(9)
        try {
            java.util.zip.DeflaterOutputStream(reference, deflater).use { it.write(data) }
        } finally {
            deflater.end()
        }
        val theirs = reference.size()

        assertTrue(
            ours <= theirs * 2,
            "pure-Kotlin deflate produced $ours bytes against zlib's $theirs",
        )
    }
}
