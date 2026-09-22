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


@file:JvmName("PngineBitmaps")

package org.onedroid.pngine

import android.graphics.Bitmap

/**
 * Quantizes [bitmap] to at most [PngineOptions.maxColors] colours and
 * returns a complete PNG byte stream.
 *
 * [bitmap] is only read; the caller keeps ownership and is responsible for
 * recycling it.
 *
 * From Java this reads `PngineBitmaps.encode(Pngine.INSTANCE, bitmap)`.
 *
 * @throws IllegalArgumentException if [bitmap] is recycled, empty, or backed
 *   by [Bitmap.Config.HARDWARE].
 */
@JvmOverloads
public fun Pngine.encode(
    bitmap: Bitmap,
    options: PngineOptions = PngineOptions(),
): ByteArray {
    require(!bitmap.isRecycled) { "bitmap is recycled" }
    require(bitmap.width > 0 && bitmap.height > 0) {
        "bitmap is empty (${bitmap.width}x${bitmap.height})"
    }
    // getPixels throws on hardware bitmaps; fail with a message that says
    // what to do about it.
    require(bitmap.config != Bitmap.Config.HARDWARE) {
        "HARDWARE bitmaps cannot be read back. Decode with " +
            "ImageDecoder.ALLOCATOR_SOFTWARE, or copy to ARGB_8888 first."
    }

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    return encodePixels(pixels, width, height, options)
}
