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

package au.com.mactechnology.pngine

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Palette quantizer and indexed-PNG encoder. Operates on a plain ARGB
 * `IntArray` so the pipeline stays free of platform image types.
 *
 * Pipeline: alpha normalisation, 5-5-5 histogram, median-cut palette
 * (Heckbert 1982), Lloyd/k-means refinement, then error-diffusion remap
 * (Floyd-Steinberg 1976 or Jarvis-Judice-Ninke 1976).
 */
internal object Png8Encoder {

    fun encode(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: PngineOptions,
    ): ByteArray {
        require(width > 0 && height > 0) { "width and height must be > 0" }
        require(pixels.size >= width * height) {
            "pixels array holds ${pixels.size} entries, need ${width * height}"
        }

        // Normalise alpha in place, and learn whether the image is
        // transparent anywhere. This sweep covers every pixel, so the
        // result is independent of `sampleStride`.
        val hasTransparency = normaliseAlpha(
            pixels = pixels,
            count = width * height,
            alphaThreshold = options.alphaThreshold,
            alphaLevels = options.alphaLevels,
        )

        val histogram = buildHistogram(pixels, width, height, options.sampleStride)

        var palette = quantizeMedianCut(histogram, options.maxColors, hasTransparency)
        palette = forceTransparentFirst(palette, hasTransparency)

        palette = if (options.preserveAlphaInPalette) {
            refineKMeansRgba(
                pixels = pixels,
                palette = palette,
                iterations = options.kmeansIterations,
                sampleRate = options.kmeansSampleRate,
                alphaWeight = options.alphaWeight,
            )
        } else {
            refineKMeansRgb(
                pixels = pixels,
                palette = palette,
                iterations = options.kmeansIterations,
                sampleRate = options.kmeansSampleRate,
            )
        }

        val indexed = when {
            !options.dithering || options.ditheringMethod == DitheringMethod.NONE ->
                remapNearest(pixels, width * height, palette, options)

            options.ditheringMethod == DitheringMethod.JARVIS_JUDICE_NINKE ->
                remapJarvisJudiceNinke(pixels, width, height, palette, options)

            else ->
                remapFloydSteinberg(pixels, width, height, palette, options)
        }

        return PngWriter.writeIndexedPng(
            width = width,
            height = height,
            palette = palette,
            indexedPixels = indexed,
            compressionLevel = options.compressionLevel,
        )
    }

    /**
     * Forces near-transparent pixels to fully transparent and snaps the
     * remaining alpha onto [PngineOptions.alphaLevels] steps.
     *
     * @return true when any pixel ended up fully transparent.
     */
    private fun normaliseAlpha(
        pixels: IntArray,
        count: Int,
        alphaThreshold: Int,
        alphaLevels: Int,
    ): Boolean {
        var hasTransparency = false
        val quantizeAlpha = alphaLevels < 256
        val step = 255f / (alphaLevels - 1)

        for (i in 0 until count) {
            val color = pixels[i]
            val alpha = color ushr 24
            if (alpha <= alphaThreshold) {
                pixels[i] = 0
                hasTransparency = true
                continue
            }
            if (quantizeAlpha) {
                val snapped = (round(alpha / step) * step).toInt().coerceIn(1, 255)
                pixels[i] = (snapped shl 24) or (color and 0x00FFFFFF)
            }
        }
        return hasTransparency
    }

    private fun buildHistogram(
        pixels: IntArray,
        width: Int,
        height: Int,
        stride: Int,
    ): ColorHistogram {
        val histogram = ColorHistogram()
        for (y in 0 until height step stride) {
            val rowStart = y * width
            for (x in 0 until width step stride) {
                val color = pixels[rowStart + x]
                if (color ushr 24 == 0) continue
                val r5 = ((color ushr 16) and 0xFF) ushr 3
                val g5 = ((color ushr 8) and 0xFF) ushr 3
                val b5 = (color and 0xFF) ushr 3
                histogram.add((r5 shl 10) or (g5 shl 5) or b5, color)
            }
        }
        return histogram
    }

    private class ColorHistogram {
        private val bins = HashMap<Int, Bin>(4096)

        class Bin(
            var count: Int = 0,
            var sumR: Long = 0,
            var sumG: Long = 0,
            var sumB: Long = 0,
            var sumA: Long = 0,
        )

        fun add(binIndex: Int, color: Int) {
            val bin = bins.getOrPut(binIndex) { Bin() }
            bin.count++
            bin.sumR += ((color ushr 16) and 0xFF).toLong()
            bin.sumG += ((color ushr 8) and 0xFF).toLong()
            bin.sumB += (color and 0xFF).toLong()
            bin.sumA += (color ushr 24).toLong()
        }

        fun toColorBins(): List<ColorBin> = bins.values.map { data ->
            ColorBin(
                count = data.count,
                r = (data.sumR / data.count).toInt(),
                g = (data.sumG / data.count).toInt(),
                b = (data.sumB / data.count).toInt(),
                a = (data.sumA / data.count).toInt(),
            )
        }
    }

    private fun quantizeMedianCut(
        histogram: ColorHistogram,
        maxColors: Int,
        hasTransparency: Boolean,
    ): IntArray {
        val bins = histogram.toColorBins()
        if (bins.isEmpty()) {
            return if (hasTransparency) {
                intArrayOf(0x00000000, 0xFF000000.toInt())
            } else {
                intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            }
        }

        val targetColors = if (hasTransparency) maxColors - 1 else maxColors
        val boxes = mutableListOf(ColorBox(bins.toMutableList()))

        while (boxes.size < targetColors && boxes.size < bins.size) {
            var bestIndex = -1
            var bestPriority = -1L
            for (i in boxes.indices) {
                val box = boxes[i]
                if (box.bins.size <= 1) continue
                val priority = box.priority()
                if (priority > bestPriority) {
                    bestPriority = priority
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break

            val (left, right) = boxes[bestIndex].split()
            boxes[bestIndex] = left
            boxes.add(right)
        }

        val colors = boxes.map { it.averageColor() }.toMutableList()
        if (hasTransparency) colors.add(0, 0x00000000)
        return colors.toIntArray()
    }

    private data class ColorBin(
        val count: Int,
        val r: Int,
        val g: Int,
        val b: Int,
        val a: Int,
    )

    private class ColorBox(val bins: MutableList<ColorBin>) {
        private var cachedPriority = -1L

        /**
         * Split priority: colour-space volume weighted by pixel count.
         *
         * Each axis contributes `range + 1` rather than `range`, so a box
         * that is flat on one axis still competes for splitting on the
         * strength of the others. Using the bare range collapses the whole
         * product to zero and starves such boxes, which is exactly what
         * happens on gradients confined to one or two channels.
         */
        fun priority(): Long {
            if (cachedPriority >= 0) return cachedPriority

            var rMin = 255; var rMax = 0
            var gMin = 255; var gMax = 0
            var bMin = 255; var bMax = 0
            var total = 0L
            for (bin in bins) {
                if (bin.r < rMin) rMin = bin.r
                if (bin.r > rMax) rMax = bin.r
                if (bin.g < gMin) gMin = bin.g
                if (bin.g > gMax) gMax = bin.g
                if (bin.b < bMin) bMin = bin.b
                if (bin.b > bMax) bMax = bin.b
                total += bin.count
            }
            val volume = (rMax - rMin + 1).toLong() *
                (gMax - gMin + 1).toLong() *
                (bMax - bMin + 1).toLong()
            cachedPriority = volume * total
            return cachedPriority
        }

        fun split(): Pair<ColorBox, ColorBox> {
            var rMin = 255; var rMax = 0
            var gMin = 255; var gMax = 0
            var bMin = 255; var bMax = 0
            var total = 0L
            for (bin in bins) {
                if (bin.r < rMin) rMin = bin.r
                if (bin.r > rMax) rMax = bin.r
                if (bin.g < gMin) gMin = bin.g
                if (bin.g > gMax) gMax = bin.g
                if (bin.b < bMin) bMin = bin.b
                if (bin.b > bMax) bMax = bin.b
                total += bin.count
            }

            val rRange = rMax - rMin
            val gRange = gMax - gMin
            val bRange = bMax - bMin

            when {
                rRange >= gRange && rRange >= bRange -> bins.sortBy { it.r }
                gRange >= bRange -> bins.sortBy { it.g }
                else -> bins.sortBy { it.b }
            }

            val half = total / 2
            var running = 0L
            var splitAt = 1
            for (i in bins.indices) {
                running += bins[i].count
                if (running >= half) {
                    splitAt = (i + 1).coerceIn(1, bins.size - 1)
                    break
                }
            }

            val left = ColorBox(ArrayList(bins.subList(0, splitAt)))
            val right = ColorBox(ArrayList(bins.subList(splitAt, bins.size)))
            return left to right
        }

        fun averageColor(): Int {
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var sumA = 0L; var total = 0L
            for (bin in bins) {
                val count = bin.count.toLong()
                total += count
                sumR += bin.r * count
                sumG += bin.g * count
                sumB += bin.b * count
                sumA += bin.a * count
            }
            if (total == 0L) return 0
            return argb(
                (sumA / total).toInt(),
                (sumR / total).toInt(),
                (sumG / total).toInt(),
                (sumB / total).toInt(),
            )
        }
    }

    /** Moves the fully transparent entry to index 0, inserting one if absent. */
    private fun forceTransparentFirst(palette: IntArray, hasTransparency: Boolean): IntArray {
        if (!hasTransparency) return palette
        if (palette.isNotEmpty() && palette[0] ushr 24 == 0) return palette

        val existing = palette.indexOfFirst { it ushr 24 == 0 }
        return if (existing >= 0) {
            val reordered = palette.toMutableList()
            reordered.add(0, reordered.removeAt(existing))
            reordered.toIntArray()
        } else {
            // Drop the least significant entry to make room rather than
            // growing past maxColors.
            intArrayOf(0x00000000) + palette.copyOfRange(0, palette.size - 1)
        }
    }

    private fun refineKMeansRgba(
        pixels: IntArray,
        palette: IntArray,
        iterations: Int,
        sampleRate: Int,
        alphaWeight: Int,
    ): IntArray {
        val result = palette.clone()

        repeat(iterations) {
            val sums = Array(result.size) { LongArray(5) }

            var i = 0
            while (i < pixels.size) {
                val pixel = pixels[i]
                val a = pixel ushr 24
                if (a != 0) {
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    val index = findNearestPerceptual(r, g, b, a, result, alphaWeight)
                    sums[index][0] += r.toLong()
                    sums[index][1] += g.toLong()
                    sums[index][2] += b.toLong()
                    sums[index][3] += a.toLong()
                    sums[index][4]++
                }
                i += sampleRate
            }

            var changed = false
            for (index in result.indices) {
                if (result[index] ushr 24 == 0) continue
                val count = sums[index][4]
                if (count == 0L) continue
                val newColor = argb(
                    (sums[index][3] / count).toInt().coerceIn(1, 255),
                    (sums[index][0] / count).toInt(),
                    (sums[index][1] / count).toInt(),
                    (sums[index][2] / count).toInt(),
                )
                if (newColor != result[index]) {
                    result[index] = newColor
                    changed = true
                }
            }
            if (!changed) return result
        }
        return result
    }

    private fun refineKMeansRgb(
        pixels: IntArray,
        palette: IntArray,
        iterations: Int,
        sampleRate: Int,
    ): IntArray {
        val result = palette.clone()

        repeat(iterations) {
            val sums = Array(result.size) { LongArray(4) }

            var i = 0
            while (i < pixels.size) {
                val pixel = pixels[i]
                if (pixel ushr 24 != 0) {
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    val index = findNearestPerceptual(r, g, b, 255, result, 0)
                    sums[index][0] += r.toLong()
                    sums[index][1] += g.toLong()
                    sums[index][2] += b.toLong()
                    sums[index][3]++
                }
                i += sampleRate
            }

            var changed = false
            for (index in result.indices) {
                if (result[index] ushr 24 == 0) continue
                val count = sums[index][3]
                if (count == 0L) continue
                val newColor = argb(
                    255,
                    (sums[index][0] / count).toInt(),
                    (sums[index][1] / count).toInt(),
                    (sums[index][2] / count).toInt(),
                )
                if (newColor != result[index]) {
                    result[index] = newColor
                    changed = true
                }
            }
            if (!changed) return result
        }
        return result
    }

    /**
     * Low-cost perceptual colour distance ("redmean"), after Thiadmer
     * Riemersma / CompuPhase. Cheaper than CIE94 and far closer to human
     * judgement than plain RGB Euclidean distance.
     */
    private fun perceptualDistance(
        r1: Int, g1: Int, b1: Int,
        r2: Int, g2: Int, b2: Int,
    ): Int {
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        val rMean = (r1 + r2) shr 1
        return (((512 + rMean) * dr * dr) shr 8) +
            (dg * dg shl 2) +
            (((767 - rMean) * db * db) shr 8)
    }

    private fun findNearestPerceptual(
        r: Int, g: Int, b: Int, a: Int,
        palette: IntArray,
        alphaWeight: Int,
    ): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val candidate = palette[i]
            val pa = candidate ushr 24
            if (pa == 0) continue
            val da = a - pa
            val distance = perceptualDistance(
                r, g, b,
                (candidate ushr 16) and 0xFF,
                (candidate ushr 8) and 0xFF,
                candidate and 0xFF,
            ) + alphaWeight * da * da
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
                if (distance == 0) break
            }
        }
        return best
    }

    private fun findNearestEuclidean(
        r: Int, g: Int, b: Int, a: Int,
        palette: IntArray,
        alphaWeight: Int,
    ): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val candidate = palette[i]
            val pa = candidate ushr 24
            if (pa == 0) continue
            val dr = r - ((candidate ushr 16) and 0xFF)
            val dg = g - ((candidate ushr 8) and 0xFF)
            val db = b - (candidate and 0xFF)
            val da = a - pa
            val distance = dr * dr + dg * dg + db * db + alphaWeight * da * da
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
                if (distance == 0) break
            }
        }
        return best
    }

    private fun findNearest(
        r: Int, g: Int, b: Int, a: Int,
        palette: IntArray,
        options: PngineOptions,
    ): Int = if (options.usePerceptualDistance) {
        findNearestPerceptual(r, g, b, a, palette, options.alphaWeight)
    } else {
        findNearestEuclidean(r, g, b, a, palette, options.alphaWeight)
    }

    private fun transparentIndex(palette: IntArray): Int {
        if (palette.isNotEmpty() && palette[0] ushr 24 == 0) return 0
        return palette.indexOfFirst { it ushr 24 == 0 }.coerceAtLeast(0)
    }

    /**
     * Non-dithered remap, memoised on the full ARGB value.
     *
     * The cache stores the key beside the value and compares it on lookup.
     * A hash slot alone is not enough: distinct colours collide, and
     * returning the previous occupant's palette index paints the wrong
     * colour. Alpha is part of the key because the distance function
     * weights it.
     */
    private fun remapNearest(
        pixels: IntArray,
        count: Int,
        palette: IntArray,
        options: PngineOptions,
    ): ByteArray {
        val cacheBits = 15
        val cacheSize = 1 shl cacheBits
        val cacheKeys = IntArray(cacheSize)
        val cacheValues = IntArray(cacheSize)
        // Key 0 is fully transparent black, which never reaches the cache
        // (it short-circuits below), so 0 is a safe "empty" marker.

        val out = ByteArray(count)
        val transparent = transparentIndex(palette).toByte()

        for (i in 0 until count) {
            val color = pixels[i]
            val a = color ushr 24
            if (a <= options.alphaThreshold) {
                out[i] = transparent
                continue
            }

            val slot = ((color * -0x61c88647) ushr (32 - cacheBits)) and (cacheSize - 1)
            if (cacheKeys[slot] == color) {
                out[i] = cacheValues[slot].toByte()
                continue
            }

            val index = findNearest(
                (color ushr 16) and 0xFF,
                (color ushr 8) and 0xFF,
                color and 0xFF,
                a,
                palette,
                options,
            )
            cacheKeys[slot] = color
            cacheValues[slot] = index
            out[i] = index.toByte()
        }
        return out
    }

    private fun remapFloydSteinberg(
        pixels: IntArray,
        width: Int,
        height: Int,
        palette: IntArray,
        options: PngineOptions,
    ): ByteArray {
        val errR = FloatArray(width + 2)
        val errG = FloatArray(width + 2)
        val errB = FloatArray(width + 2)
        val errA = FloatArray(width + 2)
        val nextR = FloatArray(width + 2)
        val nextG = FloatArray(width + 2)
        val nextB = FloatArray(width + 2)
        val nextA = FloatArray(width + 2)

        val out = ByteArray(width * height)
        val transparent = transparentIndex(palette).toByte()
        val useGamma = options.useGammaCorrectFS
        val alphaDamp = options.alphaDiffusionDamping

        for (y in 0 until height) {
            nextR.fill(0f); nextG.fill(0f); nextB.fill(0f); nextA.fill(0f)
            val rowStart = y * width

            for (x in 0 until width) {
                val color = pixels[rowStart + x]
                val a0 = color ushr 24

                if (a0 <= options.alphaThreshold) {
                    out[rowStart + x] = transparent
                    errR[x + 1] = 0f; errG[x + 1] = 0f
                    errB[x + 1] = 0f; errA[x + 1] = 0f
                    errR[x + 2] = 0f; errG[x + 2] = 0f
                    errB[x + 2] = 0f; errA[x + 2] = 0f
                    continue
                }

                val r0 = (color ushr 16) and 0xFF
                val g0 = (color ushr 8) and 0xFF
                val b0 = color and 0xFF

                val r = applyError(r0, errR[x + 1], useGamma)
                val g = applyError(g0, errG[x + 1], useGamma)
                val b = applyError(b0, errB[x + 1], useGamma)
                val a = (a0 + errA[x + 1]).toInt().coerceIn(0, 255)

                val index = findNearest(r, g, b, a, palette, options)
                out[rowStart + x] = index.toByte()

                val chosen = palette[index]
                val pr = (chosen ushr 16) and 0xFF
                val pg = (chosen ushr 8) and 0xFF
                val pb = chosen and 0xFF
                val pa = chosen ushr 24

                val eR = if (useGamma) toLinear(r) - toLinear(pr) else (r - pr).toFloat()
                val eG = if (useGamma) toLinear(g) - toLinear(pg) else (g - pg).toFloat()
                val eB = if (useGamma) toLinear(b) - toLinear(pb) else (b - pb).toFloat()
                val eA = (a - pa).toFloat() * alphaDamp

                val residual = abs(r0 - pr) + abs(g0 - pg) + abs(b0 - pb) + abs(a0 - pa) * 2
                val strength =
                    if (options.errorAdaptiveDither && residual < options.lowErrorThreshold) {
                        options.ditheringAmount * 0.3f
                    } else {
                        options.ditheringAmount
                    }

                val w7 = 0.4375f * strength
                val w3 = 0.1875f * strength
                val w5 = 0.3125f * strength
                val w1 = 0.0625f * strength

                errR[x + 2] += eR * w7
                errG[x + 2] += eG * w7
                errB[x + 2] += eB * w7
                errA[x + 2] += eA * w7 * 0.5f

                nextR[x] += eR * w3
                nextG[x] += eG * w3
                nextB[x] += eB * w3
                nextA[x] += eA * w3 * 0.5f

                nextR[x + 1] += eR * w5
                nextG[x + 1] += eG * w5
                nextB[x + 1] += eB * w5
                nextA[x + 1] += eA * w5 * 0.5f

                nextR[x + 2] += eR * w1
                nextG[x + 2] += eG * w1
                nextB[x + 2] += eB * w1
                nextA[x + 2] += eA * w1 * 0.5f
            }

            System.arraycopy(nextR, 0, errR, 0, width + 2)
            System.arraycopy(nextG, 0, errG, 0, width + 2)
            System.arraycopy(nextB, 0, errB, 0, width + 2)
            System.arraycopy(nextA, 0, errA, 0, width + 2)
        }
        return out
    }

    private fun remapJarvisJudiceNinke(
        pixels: IntArray,
        width: Int,
        height: Int,
        palette: IntArray,
        options: PngineOptions,
    ): ByteArray {
        val errR = Array(3) { FloatArray(width + 4) }
        val errG = Array(3) { FloatArray(width + 4) }
        val errB = Array(3) { FloatArray(width + 4) }
        val errA = Array(3) { FloatArray(width + 4) }

        val out = ByteArray(width * height)
        val transparent = transparentIndex(palette).toByte()
        val alphaDamp = options.alphaDiffusionDamping

        val w7 = 0.14583f * options.ditheringAmount
        val w5 = 0.10417f * options.ditheringAmount
        val w3 = 0.0625f * options.ditheringAmount
        val w1 = 0.02083f * options.ditheringAmount

        for (y in 0 until height) {
            shiftRows(errR); shiftRows(errG); shiftRows(errB); shiftRows(errA)

            val rowStart = y * width
            for (x in 0 until width) {
                val color = pixels[rowStart + x]
                val a0 = color ushr 24

                if (a0 <= options.alphaThreshold) {
                    out[rowStart + x] = transparent
                    errR[0][x + 2] = 0f; errG[0][x + 2] = 0f
                    errB[0][x + 2] = 0f; errA[0][x + 2] = 0f
                    continue
                }

                val r = ((color ushr 16 and 0xFF) + errR[0][x + 2]).toInt().coerceIn(0, 255)
                val g = ((color ushr 8 and 0xFF) + errG[0][x + 2]).toInt().coerceIn(0, 255)
                val b = ((color and 0xFF) + errB[0][x + 2]).toInt().coerceIn(0, 255)
                val a = (a0 + errA[0][x + 2]).toInt().coerceIn(0, 255)

                val index = findNearest(r, g, b, a, palette, options)
                out[rowStart + x] = index.toByte()

                val chosen = palette[index]
                val eR = (r - ((chosen ushr 16) and 0xFF)).toFloat()
                val eG = (g - ((chosen ushr 8) and 0xFF)).toFloat()
                val eB = (b - (chosen and 0xFF)).toFloat()
                val eA = (a - (chosen ushr 24)).toFloat() * alphaDamp

                // Row 0 is the current scanline, rows 1 and 2 are ahead.
                diffuse(errR, errG, errB, errA, x, 3, 0, eR, eG, eB, eA, w7)
                diffuse(errR, errG, errB, errA, x, 4, 0, eR, eG, eB, eA, w5)

                diffuse(errR, errG, errB, errA, x, 0, 1, eR, eG, eB, eA, w3)
                diffuse(errR, errG, errB, errA, x, 1, 1, eR, eG, eB, eA, w5)
                diffuse(errR, errG, errB, errA, x, 2, 1, eR, eG, eB, eA, w7)
                diffuse(errR, errG, errB, errA, x, 3, 1, eR, eG, eB, eA, w5)
                diffuse(errR, errG, errB, errA, x, 4, 1, eR, eG, eB, eA, w3)

                diffuse(errR, errG, errB, errA, x, 0, 2, eR, eG, eB, eA, w1)
                diffuse(errR, errG, errB, errA, x, 1, 2, eR, eG, eB, eA, w3)
                diffuse(errR, errG, errB, errA, x, 2, 2, eR, eG, eB, eA, w5)
                diffuse(errR, errG, errB, errA, x, 3, 2, eR, eG, eB, eA, w3)
                diffuse(errR, errG, errB, errA, x, 4, 2, eR, eG, eB, eA, w1)
            }
        }
        return out
    }

    private fun shiftRows(rows: Array<FloatArray>) {
        val head = rows[0]
        rows[0] = rows[1]
        rows[1] = rows[2]
        rows[2] = head
        rows[2].fill(0f)
    }

    @Suppress("LongParameterList")
    private fun diffuse(
        errR: Array<FloatArray>,
        errG: Array<FloatArray>,
        errB: Array<FloatArray>,
        errA: Array<FloatArray>,
        x: Int,
        dx: Int,
        row: Int,
        eR: Float,
        eG: Float,
        eB: Float,
        eA: Float,
        weight: Float,
    ) {
        errR[row][x + dx] += eR * weight
        errG[row][x + dx] += eG * weight
        errB[row][x + dx] += eB * weight
        errA[row][x + dx] += eA * weight
    }

    private fun applyError(channel: Int, error: Float, useGamma: Boolean): Int =
        if (useGamma) {
            fromLinear(toLinear(channel) + error)
        } else {
            (channel + error).toInt().coerceIn(0, 255)
        }

    private val LINEAR_LUT = FloatArray(256) { i ->
        val x = i / 255f
        if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
    }

    private val SRGB_LUT = IntArray(4096) { i ->
        val x = i / 4095f
        val srgb = if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
        (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun toLinear(value: Int): Float = LINEAR_LUT[value and 0xFF]

    private fun fromLinear(linear: Float): Int =
        SRGB_LUT[(linear.coerceIn(0f, 1f) * 4095f + 0.5f).toInt().coerceIn(0, 4095)]

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b
}
