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

/** Error-diffusion kernel used when remapping pixels onto the palette. */
public enum class DitheringMethod {
    /** No error diffusion. Fastest, banding on gradients. */
    NONE,

    /** Floyd & Steinberg (1976). Default; best quality/speed balance. */
    FLOYD_STEINBERG,

    /** Jarvis, Judice & Ninke (1976). Wider kernel, smoother, slower. */
    JARVIS_JUDICE_NINKE,
}

/**
 * Encoder tuning. Defaults target photographic content at 256 colours.
 *
 * @property maxColors Palette size, 2..256.
 * @property dithering Whether to diffuse quantization error at all.
 * @property ditheringAmount Global dither strength, 0..1.
 * @property ditheringMethod Error-diffusion kernel.
 * @property compressionLevel Deflate level, 0..9.
 * @property sampleStride Histogram subsampling step. 1 samples every pixel.
 * @property alphaThreshold Alpha at or below this is forced fully transparent.
 * @property alphaLevels Alpha is pre-quantized to this many levels, 2..256.
 *   Set to 256 to leave alpha untouched.
 * @property alphaWeight Weight of the alpha term in palette distance.
 * @property preserveAlphaInPalette Refine the palette in RGBA rather than RGB.
 * @property usePerceptualDistance Use the redmean approximation instead of
 *   plain squared Euclidean distance.
 * @property kmeansIterations Lloyd refinement passes over the palette.
 * @property kmeansSampleRate Refinement samples every Nth pixel.
 * @property useGammaCorrectFS Diffuse error in linear light rather than sRGB.
 * @property alphaDiffusionDamping Scales alpha error before diffusion, 0..1.
 *   0 keeps alpha exact, 1 diffuses alpha as strongly as colour.
 * @property errorAdaptiveDither Reduce dither strength where the palette
 *   already matches the source closely.
 * @property lowErrorThreshold Residual below which dither is attenuated.
 */
public data class PngineOptions(
    val maxColors: Int = 256,
    val dithering: Boolean = true,
    val ditheringAmount: Float = 0.75f,
    val ditheringMethod: DitheringMethod = DitheringMethod.FLOYD_STEINBERG,
    val compressionLevel: Int = 9,
    val sampleStride: Int = 1,

    val alphaThreshold: Int = 16,
    val alphaLevels: Int = 16,
    val alphaWeight: Int = 8,
    val preserveAlphaInPalette: Boolean = true,

    val usePerceptualDistance: Boolean = true,
    val kmeansIterations: Int = 7,
    val kmeansSampleRate: Int = 8,

    val useGammaCorrectFS: Boolean = true,
    val alphaDiffusionDamping: Float = 0.2f,
    val errorAdaptiveDither: Boolean = true,
    val lowErrorThreshold: Int = 18,
) {
    init {
        require(maxColors in 2..256) { "maxColors must be 2..256, was $maxColors" }
        require(compressionLevel in 0..9) {
            "compressionLevel must be 0..9, was $compressionLevel"
        }
        require(sampleStride >= 1) { "sampleStride must be >= 1, was $sampleStride" }
        require(ditheringAmount in 0f..1f) {
            "ditheringAmount must be 0..1, was $ditheringAmount"
        }
        require(alphaLevels in 2..256) { "alphaLevels must be 2..256, was $alphaLevels" }
        require(alphaThreshold in 0..255) {
            "alphaThreshold must be 0..255, was $alphaThreshold"
        }
        require(alphaDiffusionDamping in 0f..1f) {
            "alphaDiffusionDamping must be 0..1, was $alphaDiffusionDamping"
        }
        require(kmeansSampleRate >= 1) {
            "kmeansSampleRate must be >= 1, was $kmeansSampleRate"
        }
    }

    public companion object {
        /** Highest quality, slowest. Full sampling, wide dither kernel. */
        public val MaxQuality: PngineOptions = PngineOptions(
            ditheringMethod = DitheringMethod.JARVIS_JUDICE_NINKE,
            kmeansIterations = 12,
            kmeansSampleRate = 4,
            alphaLevels = 32,
        )

        /** Fast path for batch work or low-memory conditions. */
        public val Fast: PngineOptions = PngineOptions(
            dithering = false,
            compressionLevel = 6,
            kmeansIterations = 3,
            kmeansSampleRate = 16,
            usePerceptualDistance = false,
        )
    }
}
