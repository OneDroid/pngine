# Platforms

Pngine publishes these targets:

| Target | Artifact | Notes |
| --- | --- | --- |
| Android | `pngine-android` | `minSdk` 21, JVM target 11, adds the `Bitmap` overload |
| JVM / desktop | `pngine-jvm` | JVM target 11 |
| iOS arm64 | `pngine-iosarm64` | device |
| iOS simulator arm64 | `pngine-iossimulatorarm64` | Apple silicon simulator |
| iOS x64 | `pngine-iosx64` | Intel simulator |
| JS | `pngine-js` | browser and Node |
| Wasm | `pngine-wasm-js` | browser and Node |

Everything but the `Bitmap` overload lives in `commonMain`, so behaviour is
identical across targets: same palette, same dithering, same bytes out.

## Why it ports cleanly

The two things that usually pin an image encoder to the JVM are the pixel
source and the compressor. Pngine takes an `IntArray` for the first, and
[implements DEFLATE itself](deflate.md) for the second, so there is no
`expect`/`actual` in the library at all — only an extra Android-only file.

## Getting pixels on each platform

Pngine does not decode images; it takes pixels you already have.

=== "Android"

    ```kotlin
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    ```

    Or skip it and call `Pngine.encode(bitmap)`.

=== "JVM"

    ```kotlin
    val pixels = IntArray(image.width * image.height)
    image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
    ```

=== "Compose Multiplatform"

    ```kotlin
    val pixels = IntArray(imageBitmap.width * imageBitmap.height)
    imageBitmap.readPixels(pixels)
    ```

=== "Generated"

    ```kotlin
    val pixels = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        (255 shl 24) or (x * 255 / width shl 16) or (y * 255 / height shl 8)
    }
    ```

## Performance

Encoding is pure computation, so the ranking follows the platform's own
speed rather than anything Pngine does. On the sample's 320×320 image at 64
colours: 73 ms in Chrome via Wasm, 369 ms on an Android emulator. Expect
physical devices to sit between the two.

For large images, lower `kmeansIterations` or raise `sampleStride` before
reaching for a smaller palette — see [Options](options.md).
