# Pngine

PNG-8 encoder for Android. Palette quantization with full alpha support,
written in pure Kotlin — no NDK, no native binaries, no third-party
dependencies.

`android.graphics.Bitmap` in, PNG `ByteArray` out.

## Why

`Bitmap.compress(PNG, …)` always writes 24/32-bit PNG and ignores the
quality argument, so it does not reduce colour depth at all. The usual
answer is [pngquant / libimagequant](https://pngquant.org/lib/), which is
excellent but needs JNI and is GPL-or-commercial.

Pngine fills the gap: Apache-2.0, pure Kotlin, runs on device.

It is not a reimplementation of anything novel — see
[Prior art](#prior-art). It is a permissively licensed, dependency-free
implementation of well-established algorithms.

## Install

```kotlin
dependencies {
    implementation("org.onedroid:pngine:0.1.0")
}
```

## Usage

```kotlin
val bytes = Pngine.encode(bitmap)
File(cacheDir, "out.png").writeBytes(bytes)
```

Encoding is CPU-bound — run it off the main thread:

```kotlin
val bytes = withContext(Dispatchers.Default) {
    Pngine.encode(bitmap, PngineOptions(maxColors = 128))
}
```

Presets:

```kotlin
Pngine.encode(bitmap, PngineOptions.MaxQuality)  // slower, wider dither kernel
Pngine.encode(bitmap, PngineOptions.Fast)        // batch work, low memory
```

Raw pixels, no `Bitmap` needed:

```kotlin
val bytes = Pngine.encodePixels(argbPixels, width, height)
```

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

## Prior art

Every algorithm here is published work. Pngine claims no novelty; it claims
a licence and a platform. Full citations are in [NOTICE](NOTICE).

- Median-cut quantization — Heckbert (1982)
- k-means / Lloyd refinement — Lloyd (1982)
- Floyd-Steinberg error diffusion — Floyd & Steinberg (1976)
- Jarvis-Judice-Ninke error diffusion — Jarvis, Judice & Ninke (1976)
- Perceptual colour distance ("redmean") — Riemersma / CompuPhase
- PNG container — RFC 2083

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
- **Kotlin Multiplatform.** Needs `expect`/`actual` for the pixel source
  and a non-JVM deflate.

## Testing

```bash
./gradlew :pngine:testDebugUnitTest
```

Tests decode the emitted bytes with a strict in-test PNG reader that
verifies chunk lengths, CRCs and row filters, rather than trusting a
platform decoder.

## Licence

Apache-2.0. See [LICENSE](LICENSE).
