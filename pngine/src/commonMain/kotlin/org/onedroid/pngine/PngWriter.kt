/*
 * Copyright 2026 MacTechnology
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


package org.onedroid.pngine

import org.onedroid.pngine.internal.BitWriter
import org.onedroid.pngine.internal.Crc32
import org.onedroid.pngine.internal.Deflate

/**
 * Minimal writer for colour-type 3 (indexed) PNG streams, per RFC 2083.
 *
 * Emits IHDR, PLTE, an optional tRNS, a single IDAT and IEND. Rows use
 * filter type 0; see the roadmap note in the README about per-row filter
 * selection and sub-8-bit depths.
 */
internal object PngWriter {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private const val BIT_DEPTH_8 = 8
    private const val COLOR_TYPE_INDEXED = 3
    private const val FILTER_NONE: Byte = 0

    fun writeIndexedPng(
        width: Int,
        height: Int,
        palette: IntArray,
        indexedPixels: ByteArray,
        compressionLevel: Int,
    ): ByteArray {
        val out = BitWriter(indexedPixels.size + 2048)
        out.writeBytes(SIGNATURE, 0, SIGNATURE.size)

        writeChunk(out, "IHDR", createIhdr(width, height))

        val (plte, trns) = createPaletteChunks(palette)
        writeChunk(out, "PLTE", plte)
        if (trns != null) writeChunk(out, "tRNS", trns)

        val filtered = applyNoneFilter(indexedPixels, width, height)
        writeChunk(out, "IDAT", Deflate.zlibCompress(filtered, compressionLevel))
        writeChunk(out, "IEND", ByteArray(0))

        return out.toByteArray()
    }

    private fun createIhdr(width: Int, height: Int): ByteArray {
        val ihdr = ByteArray(13)
        writeIntBigEndian(ihdr, 0, width)
        writeIntBigEndian(ihdr, 4, height)
        ihdr[8] = BIT_DEPTH_8.toByte()
        ihdr[9] = COLOR_TYPE_INDEXED.toByte()
        ihdr[10] = 0  // compression method: deflate
        ihdr[11] = 0  // filter method: adaptive
        ihdr[12] = 0  // interlace: none
        return ihdr
    }

    /**
     * Builds PLTE, and tRNS when any entry is not fully opaque.
     *
     * tRNS is truncated after the last non-opaque entry, since the spec
     * treats missing trailing entries as opaque.
     */
    private fun createPaletteChunks(palette: IntArray): Pair<ByteArray, ByteArray?> {
        val plte = ByteArray(palette.size * 3)
        val trns = ByteArray(palette.size)
        var lastNonOpaque = -1

        for (i in palette.indices) {
            val color = palette[i]
            plte[i * 3] = ((color ushr 16) and 0xFF).toByte()
            plte[i * 3 + 1] = ((color ushr 8) and 0xFF).toByte()
            plte[i * 3 + 2] = (color and 0xFF).toByte()

            val alpha = color ushr 24
            trns[i] = alpha.toByte()
            if (alpha != 255) lastNonOpaque = i
        }

        return plte to if (lastNonOpaque >= 0) trns.copyOf(lastNonOpaque + 1) else null
    }

    private fun applyNoneFilter(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(pixels.size + height)
        var writePos = 0
        var readPos = 0
        repeat(height) {
            out[writePos++] = FILTER_NONE
            pixels.copyInto(out, writePos, readPos, readPos + width)
            writePos += width
            readPos += width
        }
        return out
    }

    private fun writeChunk(out: BitWriter, type: String, data: ByteArray) {
        val typeBytes = ByteArray(type.length) { type[it].code.toByte() }
        out.writeBytes(intToBytes(data.size), 0, 4)
        out.writeBytes(typeBytes, 0, typeBytes.size)
        out.writeBytes(data, 0, data.size)
        out.writeBytes(intToBytes(Crc32.of(typeBytes, data)), 0, 4)
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun writeIntBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
