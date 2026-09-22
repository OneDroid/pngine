/*
 * Copyright 2026 OneDroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.onedroid.pngine.internal

/**
 * DEFLATE (RFC 1951) inside a zlib wrapper (RFC 1950), in pure Kotlin.
 *
 * The compressor is a conventional LZ77 front end — hash chains over a 32 KiB
 * window, optional lazy matching — feeding a block writer that picks the
 * cheapest of a stored, fixed-Huffman or dynamic-Huffman encoding for each
 * block.
 *
 * It exists because PNG needs a zlib stream and `java.util.zip.Deflater` only
 * exists on the JVM. Output is a valid zlib stream that any inflater reads;
 * it is not bit-identical to zlib's.
 */
internal object Deflate {

    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 258
    private const val WINDOW_SIZE = 32768
    private const val WINDOW_MASK = WINDOW_SIZE - 1
    private const val HASH_SIZE = 32768
    private const val HASH_MASK = HASH_SIZE - 1

    private const val END_OF_BLOCK = 256
    private const val LITLEN_SYMBOLS = 286
    private const val DIST_SYMBOLS = 30
    private const val MAX_CODE_BITS = 15
    private const val MAX_CODE_LENGTH_BITS = 7
    private const val MAX_SYMBOLS_PER_BLOCK = 16384
    private const val MAX_STORED_BLOCK = 65535

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

    /** RFC 1951 §3.2.7: the order code lengths are written in. */
    private val CODE_LENGTH_ORDER = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    )

    private val LENGTH_CODE = IntArray(MAX_MATCH + 1).also { table ->
        var code = 0
        for (length in MIN_MATCH..MAX_MATCH) {
            while (code < LENGTH_BASE.size - 1 && length >= LENGTH_BASE[code + 1]) code++
            table[length] = code
        }
    }

    // Distances split into two tables: exact for 1..256, and one entry per
    // 128-distance block above that, which works because every distance base
    // over 256 is 128k + 1.
    private val DIST_CODE_LOW = IntArray(257).also { table ->
        var code = 0
        for (distance in 1..256) {
            while (code < DIST_BASE.size - 1 && distance >= DIST_BASE[code + 1]) code++
            table[distance] = code
        }
    }
    private val DIST_CODE_HIGH = IntArray(256).also { table ->
        for (slot in table.indices) {
            val distance = (slot shl 7) + 1
            var code = 0
            while (code < DIST_BASE.size - 1 && distance >= DIST_BASE[code + 1]) code++
            table[slot] = code
        }
    }

    private val FIXED_LITLEN_LENGTHS = IntArray(288) { symbol ->
        when {
            symbol < 144 -> 8
            symbol < 256 -> 9
            symbol < 280 -> 7
            else -> 8
        }
    }
    private val FIXED_DIST_LENGTHS = IntArray(DIST_SYMBOLS) { 5 }

    /** Compression knobs per level, in the spirit of zlib's table. */
    private class Config(val maxChain: Int, val niceLength: Int, val lazy: Boolean)

    private fun configFor(level: Int): Config = when (level) {
        1 -> Config(maxChain = 4, niceLength = 8, lazy = false)
        2 -> Config(maxChain = 8, niceLength = 16, lazy = false)
        3 -> Config(maxChain = 32, niceLength = 32, lazy = false)
        4 -> Config(maxChain = 16, niceLength = 16, lazy = true)
        5 -> Config(maxChain = 32, niceLength = 32, lazy = true)
        6 -> Config(maxChain = 128, niceLength = 128, lazy = true)
        7 -> Config(maxChain = 256, niceLength = 128, lazy = true)
        8 -> Config(maxChain = 1024, niceLength = 258, lazy = true)
        else -> Config(maxChain = 4096, niceLength = MAX_MATCH, lazy = true)
    }

    /**
     * Compresses [data] into a zlib stream at [level] (0 stores, 9 is
     * slowest and smallest).
     */
    fun zlibCompress(data: ByteArray, level: Int): ByteArray {
        require(level in 0..9) { "level must be 0..9, was $level" }

        val writer = BitWriter(data.size / 3 + 64)
        writeZlibHeader(writer, level)

        if (level == 0 || data.isEmpty()) {
            writeStoredBlocks(writer, data, 0, data.size, last = true)
        } else {
            compressBlocks(writer, data, configFor(level))
        }

        writer.alignToByte()
        val adler = Adler32.of(data)
        writer.writeByte(adler ushr 24)
        writer.writeByte(adler ushr 16)
        writer.writeByte(adler ushr 8)
        writer.writeByte(adler)
        return writer.toByteArray()
    }

    private fun writeZlibHeader(writer: BitWriter, level: Int) {
        // CM = 8 (deflate), CINFO = 7 (32 KiB window).
        val cmf = 0x78
        val flevel = when {
            level <= 1 -> 0
            level <= 5 -> 1
            level == 6 -> 2
            else -> 3
        }
        var flg = flevel shl 6
        val check = 31 - ((cmf shl 8) or flg) % 31
        if (check != 31) flg = flg or check
        writer.writeByte(cmf)
        writer.writeByte(flg)
    }

    private fun compressBlocks(writer: BitWriter, data: ByteArray, config: Config) {
        val size = data.size
        val head = IntArray(HASH_SIZE) { -1 }
        val prev = IntArray(WINDOW_SIZE)
        val symbols = SymbolBuffer(MAX_SYMBOLS_PER_BLOCK)

        var position = 0
        var inserted = 0
        var blockStart = 0

        var pendingValid = false
        var pendingMatch = 0

        fun insertUpTo(limit: Int) {
            while (inserted <= limit) {
                if (inserted + MIN_MATCH <= size) {
                    val hash = hash3(data, inserted)
                    prev[inserted and WINDOW_MASK] = head[hash]
                    head[hash] = inserted
                }
                inserted++
            }
        }

        while (position < size) {
            var match: Int
            if (pendingValid) {
                match = pendingMatch
                pendingValid = false
            } else {
                insertUpTo(position)
                match = findMatch(data, position, prev, config)
            }

            var length = match ushr 16
            val distance = match and 0xFFFF

            // Lazy matching: a longer match one byte later is usually worth
            // emitting this byte as a literal.
            if (config.lazy && length >= MIN_MATCH && length < config.niceLength &&
                position + 1 < size
            ) {
                insertUpTo(position + 1)
                val next = findMatch(data, position + 1, prev, config)
                if ((next ushr 16) > length) {
                    symbols.addLiteral(data[position].toInt() and 0xFF)
                    pendingMatch = next
                    pendingValid = true
                    position++
                    if (symbols.size >= MAX_SYMBOLS_PER_BLOCK) {
                        writeBlock(writer, symbols, data, blockStart, position, last = false)
                        symbols.reset()
                        blockStart = position
                    }
                    continue
                }
            }

            if (length >= MIN_MATCH) {
                symbols.addMatch(length, distance)
                insertUpTo(position + length - 1)
                position += length
            } else {
                symbols.addLiteral(data[position].toInt() and 0xFF)
                position++
                length = 1
            }

            if (symbols.size >= MAX_SYMBOLS_PER_BLOCK) {
                writeBlock(writer, symbols, data, blockStart, position, last = false)
                symbols.reset()
                blockStart = position
            }
        }

        writeBlock(writer, symbols, data, blockStart, size, last = true)
    }

    private fun hash3(data: ByteArray, position: Int): Int {
        val a = data[position].toInt() and 0xFF
        val b = data[position + 1].toInt() and 0xFF
        val c = data[position + 2].toInt() and 0xFF
        return ((a shl 10) xor (b shl 5) xor c) and HASH_MASK
    }

    /**
     * Best match for [position] along its hash chain, packed as
     * `(length shl 16) or distance`, or 0 when nothing reaches [MIN_MATCH].
     * The caller must have inserted [position] into the chain already.
     */
    private fun findMatch(
        data: ByteArray,
        position: Int,
        prev: IntArray,
        config: Config,
    ): Int {
        val size = data.size
        val maxLength = minOf(MAX_MATCH, size - position)
        if (maxLength < MIN_MATCH) return 0

        val floor = position - WINDOW_SIZE
        var candidate = prev[position and WINDOW_MASK]
        var chain = config.maxChain
        var bestLength = 0
        var bestDistance = 0

        while (candidate > floor && candidate >= 0 && candidate < position && chain > 0) {
            chain--
            // Cheap reject: a candidate that does not even match the byte
            // that would extend the current best cannot beat it.
            if (bestLength == 0 || data[candidate + bestLength] == data[position + bestLength]) {
                var length = 0
                while (length < maxLength &&
                    data[candidate + length] == data[position + length]
                ) {
                    length++
                }
                if (length > bestLength) {
                    bestLength = length
                    bestDistance = position - candidate
                    // Nothing longer is possible, and the reject test below
                    // would read past the end of the input.
                    if (length >= config.niceLength || length >= maxLength) break
                }
            }
            candidate = prev[candidate and WINDOW_MASK]
        }

        return if (bestLength >= MIN_MATCH) (bestLength shl 16) or bestDistance else 0
    }

    private fun lengthCode(length: Int): Int = LENGTH_CODE[length]

    private fun distanceCode(distance: Int): Int =
        if (distance <= 256) DIST_CODE_LOW[distance] else DIST_CODE_HIGH[(distance - 1) ushr 7]

    /** Literals and matches for one block, with their symbol frequencies. */
    private class SymbolBuffer(capacity: Int) {
        val literalOrLength = IntArray(capacity)
        val distance = IntArray(capacity)
        val litLenFreq = IntArray(LITLEN_SYMBOLS)
        val distFreq = IntArray(DIST_SYMBOLS)
        var size = 0
            private set

        fun addLiteral(byte: Int) {
            literalOrLength[size] = byte
            distance[size] = 0
            size++
            litLenFreq[byte]++
        }

        fun addMatch(length: Int, dist: Int) {
            literalOrLength[size] = length
            distance[size] = dist
            size++
            litLenFreq[257 + lengthCode(length)]++
            distFreq[distanceCode(dist)]++
        }

        fun reset() {
            size = 0
            litLenFreq.fill(0)
            distFreq.fill(0)
        }
    }

    private fun writeBlock(
        writer: BitWriter,
        symbols: SymbolBuffer,
        data: ByteArray,
        blockStart: Int,
        blockEnd: Int,
        last: Boolean,
    ) {
        if (symbols.size == 0 && blockEnd == blockStart) {
            if (last) writeStoredBlocks(writer, data, blockStart, blockEnd, last = true)
            return
        }

        symbols.litLenFreq[END_OF_BLOCK]++

        val litLenLengths = Huffman.codeLengths(symbols.litLenFreq, MAX_CODE_BITS)
        val distLengths = Huffman.codeLengths(symbols.distFreq, MAX_CODE_BITS)
        // A tree with a single code is legal but decoders differ on how they
        // take it; two codes is always unambiguous and costs a bit or two.
        ensureTwoCodes(litLenLengths)
        ensureTwoCodes(distLengths)

        val header = DynamicHeader(litLenLengths, distLengths)
        val dynamicBits = header.bits + symbolBits(symbols, litLenLengths, distLengths)
        val fixedBits = 3 + symbolBits(symbols, FIXED_LITLEN_LENGTHS, FIXED_DIST_LENGTHS)
        val storedBits = 3 + 7 + 32 + 8 * (blockEnd - blockStart)

        when {
            storedBits <= dynamicBits && storedBits <= fixedBits ->
                writeStoredBlocks(writer, data, blockStart, blockEnd, last)

            fixedBits <= dynamicBits -> {
                writer.writeBits(if (last) 1 else 0, 1)
                writer.writeBits(1, 2)
                writeSymbols(writer, symbols, FIXED_LITLEN_LENGTHS, FIXED_DIST_LENGTHS)
            }

            else -> {
                writer.writeBits(if (last) 1 else 0, 1)
                writer.writeBits(2, 2)
                header.write(writer)
                writeSymbols(writer, symbols, litLenLengths, distLengths)
            }
        }
    }

    private fun ensureTwoCodes(lengths: IntArray) {
        var used = 0
        for (length in lengths) if (length > 0) used++
        if (used >= 2) return
        for (i in lengths.indices) {
            if (lengths[i] == 0) {
                lengths[i] = 1
                used++
                if (used == 2) return
            }
        }
    }

    private fun symbolBits(
        symbols: SymbolBuffer,
        litLenLengths: IntArray,
        distLengths: IntArray,
    ): Int {
        var bits = litLenLengths[END_OF_BLOCK]
        for (i in 0 until symbols.size) {
            val dist = symbols.distance[i]
            if (dist == 0) {
                bits += litLenLengths[symbols.literalOrLength[i]]
            } else {
                val lc = lengthCode(symbols.literalOrLength[i])
                val dc = distanceCode(dist)
                bits += litLenLengths[257 + lc] + LENGTH_EXTRA[lc]
                bits += distLengths[dc] + DIST_EXTRA[dc]
            }
        }
        return bits
    }

    private fun writeSymbols(
        writer: BitWriter,
        symbols: SymbolBuffer,
        litLenLengths: IntArray,
        distLengths: IntArray,
    ) {
        val litLenCodes = Huffman.reversedCodes(litLenLengths, MAX_CODE_BITS)
        val distCodes = Huffman.reversedCodes(distLengths, MAX_CODE_BITS)

        for (i in 0 until symbols.size) {
            val dist = symbols.distance[i]
            if (dist == 0) {
                val literal = symbols.literalOrLength[i]
                writer.writeBits(litLenCodes[literal], litLenLengths[literal])
            } else {
                val length = symbols.literalOrLength[i]
                val lc = lengthCode(length)
                writer.writeBits(litLenCodes[257 + lc], litLenLengths[257 + lc])
                writer.writeBits(length - LENGTH_BASE[lc], LENGTH_EXTRA[lc])

                val dc = distanceCode(dist)
                writer.writeBits(distCodes[dc], distLengths[dc])
                writer.writeBits(dist - DIST_BASE[dc], DIST_EXTRA[dc])
            }
        }
        writer.writeBits(litLenCodes[END_OF_BLOCK], litLenLengths[END_OF_BLOCK])
    }

    /**
     * The run-length-encoded code length table that precedes a dynamic
     * block, built once so its exact cost can be compared against the fixed
     * and stored encodings before anything is written.
     */
    private class DynamicHeader(litLenLengths: IntArray, distLengths: IntArray) {

        val hlit: Int
        val hdist: Int
        val hclen: Int
        private val clLengths: IntArray
        private val rleCodes: IntArray
        private val rleExtra: IntArray
        private val rleSize: Int
        val bits: Int

        init {
            var lit = LITLEN_SYMBOLS
            while (lit > 257 && litLenLengths[lit - 1] == 0) lit--
            var dist = DIST_SYMBOLS
            while (dist > 1 && distLengths[dist - 1] == 0) dist--
            hlit = lit
            hdist = dist

            val combined = IntArray(hlit + hdist)
            litLenLengths.copyInto(combined, 0, 0, hlit)
            distLengths.copyInto(combined, hlit, 0, hdist)

            val codes = IntArray(combined.size * 2)
            val extra = IntArray(combined.size * 2)
            var count = 0
            fun emit(code: Int, extraBits: Int) {
                codes[count] = code
                extra[count] = extraBits
                count++
            }

            var i = 0
            while (i < combined.size) {
                val length = combined[i]
                var run = 1
                while (i + run < combined.size && combined[i + run] == length) run++

                if (length == 0) {
                    var remaining = run
                    while (remaining >= 11) {
                        val take = minOf(remaining, 138)
                        emit(18, take - 11)
                        remaining -= take
                    }
                    while (remaining >= 3) {
                        val take = minOf(remaining, 10)
                        emit(17, take - 3)
                        remaining -= take
                    }
                    repeat(remaining) { emit(0, 0) }
                } else {
                    emit(length, 0)
                    var remaining = run - 1
                    while (remaining >= 3) {
                        val take = minOf(remaining, 6)
                        emit(16, take - 3)
                        remaining -= take
                    }
                    repeat(remaining) { emit(length, 0) }
                }
                i += run
            }
            rleCodes = codes
            rleExtra = extra
            rleSize = count

            val clFreq = IntArray(19)
            for (j in 0 until count) clFreq[codes[j]]++
            clLengths = Huffman.codeLengths(clFreq, MAX_CODE_LENGTH_BITS)

            var written = CODE_LENGTH_ORDER.size
            while (written > 4 && clLengths[CODE_LENGTH_ORDER[written - 1]] == 0) written--
            hclen = written

            var total = 5 + 5 + 4 + 3 * hclen
            for (j in 0 until count) {
                total += clLengths[codes[j]] + extraBitsFor(codes[j])
            }
            bits = total
        }

        fun write(writer: BitWriter) {
            writer.writeBits(hlit - 257, 5)
            writer.writeBits(hdist - 1, 5)
            writer.writeBits(hclen - 4, 4)
            for (i in 0 until hclen) {
                writer.writeBits(clLengths[CODE_LENGTH_ORDER[i]], 3)
            }

            val clCodes = Huffman.reversedCodes(clLengths, MAX_CODE_LENGTH_BITS)
            for (i in 0 until rleSize) {
                val code = rleCodes[i]
                writer.writeBits(clCodes[code], clLengths[code])
                val extraBits = extraBitsFor(code)
                if (extraBits > 0) writer.writeBits(rleExtra[i], extraBits)
            }
        }

        private fun extraBitsFor(code: Int): Int = when (code) {
            16 -> 2
            17 -> 3
            18 -> 7
            else -> 0
        }
    }

    private fun writeStoredBlocks(
        writer: BitWriter,
        data: ByteArray,
        start: Int,
        end: Int,
        last: Boolean,
    ) {
        var offset = start
        do {
            val length = minOf(MAX_STORED_BLOCK, end - offset)
            val isFinal = last && offset + length >= end
            writer.writeBits(if (isFinal) 1 else 0, 1)
            writer.writeBits(0, 2)
            writer.alignToByte()
            writer.writeByte(length and 0xFF)
            writer.writeByte(length ushr 8)
            writer.writeByte(length.inv() and 0xFF)
            writer.writeByte((length.inv() ushr 8) and 0xFF)
            if (length > 0) writer.writeBytes(data, offset, length)
            offset += length
        } while (offset < end)
    }
}
