# Usage

## Encoding pixels

The whole API from common code is one function:

```kotlin
import org.onedroid.pngine.Pngine

val bytes: ByteArray = Pngine.encodePixels(argbPixels, width, height)
```

`argbPixels` is one packed `Int` per pixel, row-major, alpha in the high
byte — the same layout as `Bitmap.getPixels` and
`BufferedImage.TYPE_INT_ARGB`:

```
0xAARRGGBB
```

!!! warning "The array is modified in place"

    `encodePixels` normalises alpha in the array you hand it. Pass
    `pixels.copyOf()` if you still need the original.

## Tuning the encode

Every knob lives on [`PngineOptions`](options.md):

```kotlin
val bytes = Pngine.encodePixels(
    pixels = argbPixels,
    width = width,
    height = height,
    options = PngineOptions(maxColors = 128),
)
```

Two presets cover the common cases:

```kotlin
PngineOptions.MaxQuality  // wider dither kernel, more refinement, slower
PngineOptions.Fast        // no dithering, lighter refinement, for batch work
```

## Off the main thread

Encoding is CPU-bound and allocates arrays the size of the image. It has no
business on a UI thread:

```kotlin
val bytes = withContext(Dispatchers.Default) {
    Pngine.encodePixels(pixels.copyOf(), width, height, PngineOptions(maxColors = 128))
}
```

## Android bitmaps

The Android source set adds a `Bitmap` overload. It is an extension on the
`Pngine` object, so it needs its own import:

```kotlin
import org.onedroid.pngine.Pngine
import org.onedroid.pngine.encode // (1)!

val bytes = Pngine.encode(bitmap)
File(cacheDir, "out.png").writeBytes(bytes)
```

1. Without this import the compiler will not find `Pngine.encode(bitmap)`.
   The pixel overload needs no extra import.

The bitmap is only read — you keep ownership and are responsible for
recycling it.

From Java the same call reads:

```java
byte[] bytes = PngineBitmaps.encode(Pngine.INSTANCE, bitmap);
```

### Hardware bitmaps

`Bitmap.getPixels` cannot read `Config.HARDWARE` bitmaps. Decode with
`ImageDecoder.ALLOCATOR_SOFTWARE`, or copy to `ARGB_8888` first:

```kotlin
val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
```

Pngine throws `IllegalArgumentException` with that advice rather than
letting the platform surface a confusing error.

## Errors

`encodePixels` throws `IllegalArgumentException` when:

- `pixels.size` does not equal `width * height`
- `width` or `height` is not positive

`Pngine.encode(bitmap)` additionally rejects a recycled bitmap, an empty
bitmap, and a `HARDWARE` bitmap. `PngineOptions` validates its own
arguments at construction, so a bad `maxColors` fails where you wrote it,
not deep inside the encoder.
