package au.com.mactechnology.pngine

import java.util.zip.Inflater

/**
 * Deliberately strict, dependency-free reader for colour-type 3 PNGs.
 *
 * Decoding the bytes here rather than handing them to a platform decoder
 * keeps the assertions honest: a malformed length, CRC or chunk order fails
 * the test instead of being quietly tolerated.
 */
internal class DecodedPng(
    val width: Int,
    val height: Int,
    val bitDepth: Int,
    val colorType: Int,
    val palette: IntArray,
    val indices: ByteArray,
) {
    /** Packed ARGB for the pixel at ([x], [y]). */
    fun argbAt(x: Int, y: Int): Int = palette[indices[y * width + x].toInt() and 0xFF]

    fun alphaAt(x: Int, y: Int): Int = (argbAt(x, y) ushr 24) and 0xFF

    fun distinctColors(): Set<Int> {
        val seen = HashSet<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) seen.add(argbAt(x, y))
        }
        return seen
    }
}

internal object TestPngReader {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun read(bytes: ByteArray): DecodedPng {
        require(bytes.size > 8) { "stream too short: ${bytes.size} bytes" }
        require(bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) { "bad PNG signature" }

        var pos = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        var rgb: ByteArray? = null
        var alpha: ByteArray? = null
        val idat = ArrayList<ByteArray>()
        var sawEnd = false

        while (pos < bytes.size) {
            val length = readInt(bytes, pos)
            require(length >= 0) { "negative chunk length at offset $pos" }
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            require(dataStart + length + 4 <= bytes.size) {
                "chunk $type overruns the stream"
            }
            val data = bytes.copyOfRange(dataStart, dataStart + length)

            val expectedCrc = readInt(bytes, dataStart + length)
            val actualCrc = java.util.zip.CRC32().run {
                update(bytes, pos + 4, 4)
                update(data)
                value.toInt()
            }
            require(expectedCrc == actualCrc) { "CRC mismatch in chunk $type" }

            when (type) {
                "IHDR" -> {
                    require(length == 13) { "IHDR must be 13 bytes, was $length" }
                    width = readInt(data, 0)
                    height = readInt(data, 4)
                    bitDepth = data[8].toInt() and 0xFF
                    colorType = data[9].toInt() and 0xFF
                    require(data[12].toInt() == 0) { "interlaced PNGs are not expected" }
                }
                "PLTE" -> rgb = data
                "tRNS" -> alpha = data
                "IDAT" -> idat.add(data)
                "IEND" -> sawEnd = true
            }

            pos = dataStart + length + 4
        }

        require(sawEnd) { "missing IEND chunk" }
        require(colorType == 3) { "expected indexed colour type 3, was $colorType" }
        require(bitDepth == 8) { "expected bit depth 8, was $bitDepth" }
        val plte = requireNotNull(rgb) { "missing PLTE chunk" }
        require(plte.size % 3 == 0) { "PLTE length ${plte.size} is not a multiple of 3" }
        require(idat.isNotEmpty()) { "missing IDAT data" }

        val entries = plte.size / 3
        val palette = IntArray(entries) { i ->
            val a = if (alpha != null && i < alpha.size) alpha[i].toInt() and 0xFF else 255
            (a shl 24) or
                ((plte[i * 3].toInt() and 0xFF) shl 16) or
                ((plte[i * 3 + 1].toInt() and 0xFF) shl 8) or
                (plte[i * 3 + 2].toInt() and 0xFF)
        }

        val compressed = ByteArray(idat.sumOf { it.size })
        var offset = 0
        for (part in idat) {
            part.copyInto(compressed, offset)
            offset += part.size
        }

        val raw = inflate(compressed, expectedSize = (width + 1) * height)
        require(raw.size == (width + 1) * height) {
            "inflated ${raw.size} bytes, expected ${(width + 1) * height}"
        }

        // Strip the per-row filter byte. Only filter type 0 is emitted.
        val indices = ByteArray(width * height)
        for (y in 0 until height) {
            val rowStart = y * (width + 1)
            require(raw[rowStart].toInt() == 0) {
                "row $y uses filter ${raw[rowStart]}, expected 0"
            }
            raw.copyInto(
                destination = indices,
                destinationOffset = y * width,
                startIndex = rowStart + 1,
                endIndex = rowStart + 1 + width,
            )
        }

        return DecodedPng(width, height, bitDepth, colorType, palette, indices)
    }

    private fun inflate(data: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArray(expectedSize)
            var total = 0
            while (!inflater.finished() && total < out.size) {
                val n = inflater.inflate(out, total, out.size - total)
                if (n == 0) break
                total += n
            }
            return out.copyOf(total)
        } finally {
            inflater.end()
        }
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
