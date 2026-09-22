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

import android.graphics.Bitmap

/**
 * Encodes bitmaps as palette-based (PNG-8) images with full alpha support.
 *
 * ```kotlin
 * val bytes = Pngine.encode(bitmap)
 * File(dir, "out.png").writeBytes(bytes)
 * ```
 *
 * Encoding is CPU-bound and allocates an `IntArray` the size of the bitmap.
 * Call it off the main thread.
 */
public object Pngine {

    /**
     * Quantizes [bitmap] to at most [PngineOptions.maxColors] colours and
     * returns a complete PNG byte stream.
     *
     * [bitmap] is only read; the caller keeps ownership and is responsible
     * for recycling it.
     *
     * @throws IllegalArgumentException if [bitmap] is recycled, empty, or
     *   backed by [Bitmap.Config.HARDWARE].
     */
    @JvmStatic
    @JvmOverloads
    public fun encode(
        bitmap: Bitmap,
        options: PngineOptions = PngineOptions(),
    ): ByteArray {
        require(!bitmap.isRecycled) { "bitmap is recycled" }
        require(bitmap.width > 0 && bitmap.height > 0) {
            "bitmap is empty (${bitmap.width}x${bitmap.height})"
        }
        // getPixels throws on hardware bitmaps; fail with a message that
        // says what to do about it.
        require(bitmap.config != Bitmap.Config.HARDWARE) {
            "HARDWARE bitmaps cannot be read back. Decode with " +
                "ImageDecoder.ALLOCATOR_SOFTWARE, or copy to ARGB_8888 first."
        }

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return Png8Encoder.encode(pixels, width, height, options)
    }

    /**
     * Encodes raw ARGB pixels, one packed `Int` per pixel in row-major
     * order, alpha in the high byte.
     *
     * [pixels] is modified in place during alpha normalisation. Pass a copy
     * if you still need the original.
     */
    @JvmStatic
    @JvmOverloads
    public fun encodePixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        options: PngineOptions = PngineOptions(),
    ): ByteArray = Png8Encoder.encode(pixels, width, height, options)
}
