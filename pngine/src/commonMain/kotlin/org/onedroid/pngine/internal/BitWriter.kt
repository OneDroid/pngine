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
 * Growable little-endian bit sink, as DEFLATE wants it: bits fill each byte
 * from the least significant end, and multi-bit fields are written low bit
 * first. Huffman codes are the exception — they go out most significant bit
 * first, so callers pass them pre-reversed (see [Huffman.reverse]).
 */
internal class BitWriter(initialCapacity: Int = 1024) {

    private var buffer = ByteArray(if (initialCapacity < 16) 16 else initialCapacity)
    private var size = 0

    private var bitBuffer = 0
    private var bitCount = 0

    /** Bytes written so far, ignoring any partially filled byte. */
    val byteSize: Int get() = size

    /** Writes the low [count] bits of [value], least significant bit first. */
    fun writeBits(value: Int, count: Int) {
        if (count == 0) return
        bitBuffer = bitBuffer or ((value and ((1 shl count) - 1)) shl bitCount)
        bitCount += count
        while (bitCount >= 8) {
            append((bitBuffer and 0xFF).toByte())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    /** Pads the current byte with zero bits. */
    fun alignToByte() {
        if (bitCount > 0) {
            append((bitBuffer and 0xFF).toByte())
            bitBuffer = 0
            bitCount = 0
        }
    }

    /** Writes one byte; only valid on a byte boundary. */
    fun writeByte(value: Int) {
        check(bitCount == 0) { "writeByte on a bit boundary" }
        append((value and 0xFF).toByte())
    }

    /** Copies [length] bytes from [data] starting at [offset]. */
    fun writeBytes(data: ByteArray, offset: Int, length: Int) {
        check(bitCount == 0) { "writeBytes on a bit boundary" }
        ensure(length)
        data.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray {
        alignToByte()
        return buffer.copyOf(size)
    }

    private fun append(value: Byte) {
        ensure(1)
        buffer[size++] = value
    }

    private fun ensure(extra: Int) {
        val needed = size + extra
        if (needed <= buffer.size) return
        var capacity = buffer.size
        while (capacity < needed) capacity = capacity shl 1
        buffer = buffer.copyOf(capacity)
    }
}
