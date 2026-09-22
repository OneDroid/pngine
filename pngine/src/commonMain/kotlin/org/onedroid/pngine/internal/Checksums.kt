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

/** CRC-32 (IEEE 802.3), the checksum PNG puts on every chunk. */
internal object Crc32 {

    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    fun of(vararg parts: ByteArray): Int {
        var crc = -1
        for (part in parts) {
            for (byte in part) {
                crc = TABLE[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
            }
        }
        return crc.inv()
    }
}

/** Adler-32, the checksum that terminates a zlib stream (RFC 1950). */
internal object Adler32 {

    private const val MODULO = 65521

    fun of(data: ByteArray): Int {
        var a = 1
        var b = 0
        var i = 0
        while (i < data.size) {
            // zlib defers the modulo for 5552 bytes, which is sized for
            // unsigned 32-bit. Kotlin's Int is signed, and b would overflow
            // it somewhere past 2048 bytes, so reduce twice as often.
            val end = minOf(i + 2048, data.size)
            while (i < end) {
                a += data[i].toInt() and 0xFF
                b += a
                i++
            }
            a %= MODULO
            b %= MODULO
        }
        return (b shl 16) or a
    }
}
