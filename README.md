# Pngine

PNG-8 encoder for Kotlin Multiplatform — Android, JVM/desktop, iOS, and the
web (JS and Wasm). Palette quantization with full alpha support, written in
pure Kotlin: no NDK, no native binaries, no third-party dependencies, and no
`java.util.zip` — the DEFLATE compressor is part of the library.

ARGB pixels in, PNG `ByteArray` out. On Android, `android.graphics.Bitmap`
in as well.

## Why

`Bitmap.compress(PNG, …)` always writes 24/32-bit PNG and ignores the
quality argument, so it does not reduce colour depth at all. The usual
answer is [pngquant / libimagequant](https://pngquant.org/lib/), which is
excellent but needs JNI and is GPL-or-commercial.

Pngine fills the gap: Apache-2.0, pure Kotlin, runs on device — and, being
pure Kotlin all the way down to DEFLATE, runs unchanged on iOS, desktop and
in the browser.

It is not a reimplementation of anything novel — see
[Prior art](#prior-art). It is a permissively licensed, dependency-free
implementation of well-established algorithms.

## Targets

`androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `js` and
`wasmJs`.

## Install

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.onedroid:pngine:0.1.0")
        }
    }
}
```

## Usage

From common code, on every target:

```kotlin
val bytes = Pngine.encodePixels(argbPixels, width, height)
```

`argbPixels` is one packed `Int` per pixel, row-major, alpha in the high
byte. It is modified in place during alpha normalisation — pass a copy if
you still need the original.

Encoding is CPU-bound — run it off the main thread:

```kotlin
val bytes = withContext(Dispatchers.Default) {
    Pngine.encodePixels(argbPixels, width, height, PngineOptions(maxColors = 128))
}
```

Presets:

```kotlin
PngineOptions.MaxQuality  // slower, wider dither kernel
PngineOptions.Fast        // batch work, low memory
```

### Android bitmaps

The Android source set adds a `Bitmap` overload as an extension, so it needs
its own import:

```kotlin
import org.onedroid.pngine.Pngine
import org.onedroid.pngine.encode

val bytes = Pngine.encode(bitmap)
File(cacheDir, "out.png").writeBytes(bytes)
```

From Java it reads `PngineBitmaps.encode(Pngine.INSTANCE, bitmap)`.

### Hardware bitmaps

`Bitmap.getPixels` cannot read `Config.HARDWARE` bitmaps. Decode with
`ImageDecoder.ALLOCATOR_SOFTWARE`, or copy to `ARGB_8888` first. Pngine
throws `IllegalArgumentException` with that advice rather than letting the
platform surface a confusing error.

## Options

| Option | Default | Effect |
| --- | --- | --- |
| `maxColors` | 256 | Palette size, 2..256 |
| `dithering` | true | Diffuse quantization error |
| `ditheringAmount` | 0.75 | Global dither strength, 0..1 |
| `ditheringMethod` | `FLOYD_STEINBERG` | Error-diffusion kernel |
| `compressionLevel` | 9 | Deflate level, 0..9 |
| `sampleStride` | 1 | Histogram subsampling step |
| `alphaThreshold` | 16 | Alpha at or below this becomes fully transparent |
| `alphaLevels` | 16 | Alpha quantization steps; 256 leaves alpha alone |
| `alphaWeight` | 8 | Weight of alpha in palette distance |
| `usePerceptualDistance` | true | redmean instead of RGB Euclidean |
| `kmeansIterations` | 7 | Palette refinement passes |
| `kmeansSampleRate` | 8 | Refinement samples every Nth pixel |
| `useGammaCorrectFS` | true | Diffuse error in linear light |
| `alphaDiffusionDamping` | 0.2 | Alpha error scaling, 0..1 |
| `errorAdaptiveDither` | true | Attenuate dither where residual is small |
| `lowErrorThreshold` | 18 | Residual below which dither attenuates |

Soft edges suffer when alpha is pre-quantized. For icons and cutouts, raise
`alphaLevels` to 32 or 256.

## How it works

1. Normalise alpha — clamp near-transparent to zero, snap the rest onto
   `alphaLevels` steps
2. Build a 5-5-5 RGB histogram
3. Median-cut the histogram into a palette (Heckbert)
4. Refine with Lloyd/k-means relaxation in RGBA
5. Remap pixels with error diffusion in linear light
6. Write indexed PNG — IHDR, PLTE, optional tRNS, IDAT, IEND
7. Compress IDAT with the bundled DEFLATE encoder: LZ77 over a 32 KiB
   window with hash chains and lazy matching, then whichever of a stored,
   fixed-Huffman or dynamic-Huffman block is cheapest

## Prior art

Every algorithm here is published work. Pngine claims no novelty; it claims
a licence and a platform. Full citations are in [NOTICE](NOTICE).

- Median-cut quantization — Heckbert (1982)
- k-means / Lloyd refinement — Lloyd (1982)
- Floyd-Steinberg error diffusion — Floyd & Steinberg (1976)
- Jarvis-Judice-Ninke error diffusion — Jarvis, Judice & Ninke (1976)
- Perceptual colour distance ("redmean") — Riemersma / CompuPhase
- PNG container — RFC 2083
- DEFLATE and zlib containers — RFC 1951 and RFC 1950

No code was taken from pngquant or libimagequant.

## Roadmap

Ordered by expected size win:

- **Sub-8-bit depths.** Palettes of 16 or fewer colours can pack at 4bpp,
  halving IDAT. Currently always bit depth 8.
- **Per-row filter selection.** Only filter type 0 is emitted. Trying
  `Up`/`Sub` per row wins on flat and vertically repeating images.
- **Empty-cluster reseeding.** k-means clusters that lose all members keep
  their old value instead of being reseeded, wasting palette slots.
- **Output-size guard.** Fall back to the source when quantization does not
  actually save bytes.
- **Faster nearest-colour search.** Currently a linear scan over the
  palette per pixel.
- **Faster DEFLATE.** The bundled compressor is straightforward rather than
  tuned; zlib is still quicker on the JVM.

## Testing

```bash
./gradlew :pngine:allTests
```

That runs the common suite on every target — JVM, Android host, iOS
simulator, Node for JS and Wasm.

Tests decode the emitted bytes with a strict in-test PNG reader that
verifies chunk lengths, CRCs and row filters, rather than trusting a
platform decoder. The compressor is round-tripped through an inflater
written for the test suite, so the check also runs on JS, Wasm and native;
the JVM source set repeats the same cases against `java.util.zip.Inflater`,
so a shared misreading of RFC 1951 cannot hide a bug.

## Sample

[`sample/`](sample/README.md) is a Compose Multiplatform app — Android,
iOS, desktop and web — that encodes a generated image and reports the size
saved. It builds against this repository through a composite build.

## Licence

Apache-2.0. See [LICENSE](LICENSE).
