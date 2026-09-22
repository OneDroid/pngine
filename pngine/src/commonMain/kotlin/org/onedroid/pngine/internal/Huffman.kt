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

package org.onedroid.pngine.internal

/** Canonical Huffman code construction, as specified in RFC 1951 §3.2.2. */
internal object Huffman {

    /**
     * Code lengths for [frequencies], with no code longer than [maxBits].
     *
     * Symbols with zero frequency get length 0. The tree is built by repeated
     * two-smallest merges; alphabets here are at most 288 symbols, so the
     * quadratic scan costs less than maintaining a heap. When the tree comes
     * out deeper than [maxBits] the frequencies are halved and it is rebuilt,
     * which flattens the tree and always terminates.
     */
    fun codeLengths(frequencies: IntArray, maxBits: Int): IntArray {
        val symbolCount = frequencies.size
        var freq = frequencies

        while (true) {
            var used = 0
            for (f in freq) if (f > 0) used++

            val lengths = IntArray(symbolCount)
            if (used == 0) return lengths
            if (used == 1) {
                for (i in 0 until symbolCount) {
                    if (freq[i] > 0) {
                        lengths[i] = 1
                        break
                    }
                }
                return lengths
            }

            val maxNodes = used * 2 - 1
            val weight = IntArray(maxNodes)
            val left = IntArray(maxNodes)
            val right = IntArray(maxNodes)
            val symbol = IntArray(maxNodes) { -1 }
            val active = BooleanArray(maxNodes)

            var nodes = 0
            for (i in 0 until symbolCount) {
                if (freq[i] > 0) {
                    weight[nodes] = freq[i]
                    symbol[nodes] = i
                    active[nodes] = true
                    nodes++
                }
            }

            var remaining = used
            while (remaining > 1) {
                var a = -1
                var b = -1
                for (i in 0 until nodes) {
                    if (!active[i]) continue
                    if (a == -1 || weight[i] < weight[a]) {
                        b = a
                        a = i
                    } else if (b == -1 || weight[i] < weight[b]) {
                        b = i
                    }
                }
                active[a] = false
                active[b] = false
                weight[nodes] = weight[a] + weight[b]
                left[nodes] = a
                right[nodes] = b
                active[nodes] = true
                nodes++
                remaining--
            }

            // Depth-first walk, explicit stack: no recursion on a tree whose
            // depth is only bounded by the alphabet size.
            val stackNode = IntArray(maxNodes)
            val stackDepth = IntArray(maxNodes)
            var top = 0
            stackNode[top] = nodes - 1
            stackDepth[top] = 0
            top++

            var deepest = 0
            while (top > 0) {
                top--
                val node = stackNode[top]
                val depth = stackDepth[top]
                if (symbol[node] >= 0) {
                    lengths[symbol[node]] = depth
                    if (depth > deepest) deepest = depth
                } else {
                    stackNode[top] = left[node]
                    stackDepth[top] = depth + 1
                    top++
                    stackNode[top] = right[node]
                    stackDepth[top] = depth + 1
                    top++
                }
            }

            if (deepest <= maxBits) return lengths

            val scaled = IntArray(symbolCount)
            for (i in 0 until symbolCount) {
                if (freq[i] > 0) scaled[i] = (freq[i] + 1) shr 1
            }
            freq = scaled
        }
    }

    /** Canonical codes for [lengths], most significant bit first. */
    fun canonicalCodes(lengths: IntArray, maxBits: Int): IntArray {
        val countPerLength = IntArray(maxBits + 1)
        for (length in lengths) if (length > 0) countPerLength[length]++

        val nextCode = IntArray(maxBits + 1)
        var code = 0
        for (bits in 1..maxBits) {
            code = (code + countPerLength[bits - 1]) shl 1
            nextCode[bits] = code
        }

        val codes = IntArray(lengths.size)
        for (i in lengths.indices) {
            val length = lengths[i]
            if (length > 0) codes[i] = nextCode[length]++
        }
        return codes
    }

    /** Reverses the low [length] bits of [code] for [BitWriter.writeBits]. */
    fun reverse(code: Int, length: Int): Int {
        var reversed = 0
        var remaining = code
        repeat(length) {
            reversed = (reversed shl 1) or (remaining and 1)
            remaining = remaining ushr 1
        }
        return reversed
    }

    /** Pre-reversed codes, ready to hand to [BitWriter.writeBits]. */
    fun reversedCodes(lengths: IntArray, maxBits: Int): IntArray {
        val codes = canonicalCodes(lengths, maxBits)
        for (i in codes.indices) codes[i] = reverse(codes[i], lengths[i])
        return codes
    }
}
