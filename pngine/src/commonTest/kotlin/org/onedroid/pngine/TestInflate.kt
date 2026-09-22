package org.onedroid.pngine

/**
 * Test-only DEFLATE decoder (RFC 1951) inside a zlib wrapper (RFC 1950).
 *
 * The encoder is also verified against `java.util.zip.Inflater` in the JVM
 * source set; this exists so the same round-trip assertions can run on JS,
 * Wasm and native, where no platform inflater is reachable from common code.
 * It is deliberately strict: a malformed stream throws rather than returning
 * partial output.
 */
internal object TestInflate {

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51,
        59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4,
        4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385,
        513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10,
        10, 11, 11, 12, 12, 13, 13,
    )
    private val CODE_LENGTH_ORDER = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    )

    /** Unwraps and inflates a zlib stream, checking header and Adler-32. */
    fun zlibDecompress(stream: ByteArray): ByteArray {
        require(stream.size >= 6) { "zlib stream too short: ${stream.size}" }
        val cmf = stream[0].toInt() and 0xFF
        val flg = stream[1].toInt() and 0xFF
        require(cmf and 0x0F == 8) { "not deflate: CM=${cmf and 0x0F}" }
        require(flg and 0x20 == 0) { "preset dictionary not supported" }
        require(((cmf shl 8) or flg) % 31 == 0) { "bad zlib header check" }

        val output = Inflater(stream, 2).run()

        val trailerAt = stream.size - 4
        val expected = ((stream[trailerAt].toInt() and 0xFF) shl 24) or
            ((stream[trailerAt + 1].toInt() and 0xFF) shl 16) or
            ((stream[trailerAt + 2].toInt() and 0xFF) shl 8) or
            (stream[trailerAt + 3].toInt() and 0xFF)
        val actual = adler32(output)
        require(expected == actual) { "adler32 mismatch: $expected vs $actual" }

        return output
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return (b shl 16) or a
    }

    private class Tree(val counts: IntArray, val symbols: IntArray)

    private class Inflater(private val data: ByteArray, private var position: Int) {

        private var output = ByteArray(1024)
        private var size = 0

        private var bitBuffer = 0
        private var bitCount = 0

        fun run(): ByteArray {
            while (true) {
                val last = readBits(1)
                when (val type = readBits(2)) {
                    0 -> storedBlock()
                    1 -> huffmanBlock(FIXED_LITLEN, FIXED_DIST)
                    2 -> {
                        val (litLen, dist) = readDynamicTrees()
                        huffmanBlock(litLen, dist)
                    }

                    else -> throw IllegalArgumentException("reserved block type $type")
                }
                if (last == 1) break
            }
            return output.copyOf(size)
        }

        private fun storedBlock() {
            bitBuffer = 0
            bitCount = 0
            val length = readBits(16)
            val complement = readBits(16)
            require(length == (complement.inv() and 0xFFFF)) {
                "stored block length mismatch"
            }
            repeat(length) { emit(readBits(8)) }
        }

        private fun huffmanBlock(litLenTree: Tree, distTree: Tree) {
            while (true) {
                val symbol = decode(litLenTree)
                when {
                    symbol < 256 -> emit(symbol)
                    symbol == 256 -> return
                    else -> {
                        val lengthCode = symbol - 257
                        require(lengthCode < LENGTH_BASE.size) { "bad length code $symbol" }
                        val length = LENGTH_BASE[lengthCode] + readBits(LENGTH_EXTRA[lengthCode])

                        val distCode = decode(distTree)
                        require(distCode < DIST_BASE.size) { "bad distance code $distCode" }
                        val distance = DIST_BASE[distCode] + readBits(DIST_EXTRA[distCode])
                        require(distance <= size) { "distance $distance past start of output" }

                        // Copied byte by byte: overlapping copies are legal
                        // and common (run-length encoding of a repeat).
                        var from = size - distance
                        repeat(length) { emit(output[from++].toInt() and 0xFF) }
                    }
                }
            }
        }

        private fun readDynamicTrees(): Pair<Tree, Tree> {
            val hlit = readBits(5) + 257
            val hdist = readBits(5) + 1
            val hclen = readBits(4) + 4

            val clLengths = IntArray(19)
            for (i in 0 until hclen) clLengths[CODE_LENGTH_ORDER[i]] = readBits(3)
            val clTree = buildTree(clLengths)

            val lengths = IntArray(hlit + hdist)
            var i = 0
            while (i < lengths.size) {
                when (val symbol = decode(clTree)) {
                    16 -> {
                        require(i > 0) { "repeat with no previous length" }
                        val previous = lengths[i - 1]
                        repeat(3 + readBits(2)) { lengths[i++] = previous }
                    }

                    17 -> repeat(3 + readBits(3)) { lengths[i++] = 0 }
                    18 -> repeat(11 + readBits(7)) { lengths[i++] = 0 }
                    else -> lengths[i++] = symbol
                }
            }

            return buildTree(lengths.copyOfRange(0, hlit)) to
                buildTree(lengths.copyOfRange(hlit, lengths.size))
        }

        private fun decode(tree: Tree): Int {
            var code = 0
            var first = 0
            var index = 0
            for (length in 1..15) {
                code = code or readBits(1)
                val count = tree.counts[length]
                if (code - first < count) return tree.symbols[index + (code - first)]
                index += count
                first = (first + count) shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("invalid Huffman code")
        }

        private fun emit(byte: Int) {
            if (size == output.size) output = output.copyOf(output.size * 2)
            output[size++] = byte.toByte()
        }

        private fun readBits(count: Int): Int {
            var result = 0
            var filled = 0
            while (filled < count) {
                if (bitCount == 0) {
                    require(position < data.size) { "ran off the end of the stream" }
                    bitBuffer = data[position++].toInt() and 0xFF
                    bitCount = 8
                }
                val take = minOf(count - filled, bitCount)
                result = result or ((bitBuffer and ((1 shl take) - 1)) shl filled)
                bitBuffer = bitBuffer ushr take
                bitCount -= take
                filled += take
            }
            return result
        }
    }

    private fun buildTree(lengths: IntArray): Tree {
        val counts = IntArray(16)
        for (length in lengths) counts[length]++
        counts[0] = 0

        val offsets = IntArray(16)
        for (length in 1..15) offsets[length] = offsets[length - 1] + counts[length - 1]

        val symbols = IntArray(lengths.size)
        for (symbol in lengths.indices) {
            val length = lengths[symbol]
            if (length != 0) symbols[offsets[length]++] = symbol
        }
        return Tree(counts, symbols)
    }

    private val FIXED_LITLEN = buildTree(
        IntArray(288) { symbol ->
            when {
                symbol < 144 -> 8
                symbol < 256 -> 9
                symbol < 280 -> 7
                else -> 8
            }
        }
    )

    private val FIXED_DIST = buildTree(IntArray(30) { 5 })
}
