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

/**
 * Encodes pixels as palette-based (PNG-8) images with full alpha support.
 *
 * ```kotlin
 * val bytes = Pngine.encodePixels(argbPixels, width, height)
 * ```
 *
 * On Android there is also `Pngine.encode(bitmap)`, an extension declared in
 * the Android source set; import `org.onedroid.pngine.encode` to use it.
 *
 * Encoding is CPU-bound and allocates arrays the size of the image. Call it
 * off the main thread.
 */
public object Pngine {

    /**
     * Encodes raw ARGB pixels, one packed `Int` per pixel in row-major
     * order, alpha in the high byte, quantized to at most
     * [PngineOptions.maxColors] colours.
     *
     * [pixels] is modified in place during alpha normalisation. Pass a copy
     * if you still need the original.
     */
    public fun encodePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: PngineOptions = PngineOptions(),
    ): ByteArray = Png8Encoder.encode(pixels, width, height, options)
}
