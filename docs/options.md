# Options

`PngineOptions` is a data class with defaults tuned for photographic content
at 256 colours. It validates its arguments in `init`, so a bad value throws
where you construct it.

```kotlin
val options = PngineOptions(
    maxColors = 128,
    ditheringMethod = DitheringMethod.JARVIS_JUDICE_NINKE,
    alphaLevels = 32,
)
```

## Presets

```kotlin
PngineOptions.MaxQuality
PngineOptions.Fast
```

| | `MaxQuality` | `Fast` |
| --- | --- | --- |
| `ditheringMethod` | `JARVIS_JUDICE_NINKE` | — (dithering off) |
| `dithering` | true | **false** |
| `compressionLevel` | 9 | **6** |
| `kmeansIterations` | **12** | **3** |
| `kmeansSampleRate` | **4** | **16** |
| `alphaLevels` | **32** | 16 |
| `usePerceptualDistance` | true | **false** |

Bold values differ from the defaults.

## Palette

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `maxColors` | `Int` | 256 | 2..256 | Palette size. The single biggest lever on output size. |
| `sampleStride` | `Int` | 1 | ≥ 1 | Histogram subsampling step. 1 samples every pixel; raise it to speed up palette building on large images. |
| `usePerceptualDistance` | `Boolean` | true | | Use the redmean approximation instead of plain squared Euclidean distance. |
| `kmeansIterations` | `Int` | 7 | ≥ 0 | Lloyd refinement passes over the palette. 0 leaves the median-cut result alone. |
| `kmeansSampleRate` | `Int` | 8 | ≥ 1 | Refinement samples every Nth pixel. |

## Dithering

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `dithering` | `Boolean` | true | | Diffuse quantization error at all. Off is faster and produces visible banding on gradients. |
| `ditheringMethod` | `DitheringMethod` | `FLOYD_STEINBERG` | | Error-diffusion kernel. |
| `ditheringAmount` | `Float` | 0.75 | 0..1 | Global dither strength. |
| `useGammaCorrectFS` | `Boolean` | true | | Diffuse error in linear light rather than sRGB. |
| `errorAdaptiveDither` | `Boolean` | true | | Reduce dither strength where the palette already matches the source closely. |
| `lowErrorThreshold` | `Int` | 18 | | Residual below which dither is attenuated. |

### `DitheringMethod`

| Value | Kernel | When |
| --- | --- | --- |
| `NONE` | — | Fastest. Flat art and screenshots, where banding does not arise. |
| `FLOYD_STEINBERG` | Floyd & Steinberg (1976) | Default. Best quality-to-speed balance. |
| `JARVIS_JUDICE_NINKE` | Jarvis, Judice & Ninke (1976) | Wider kernel, smoother gradients, slower. |

## Alpha

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `alphaThreshold` | `Int` | 16 | 0..255 | Alpha at or below this is forced fully transparent. |
| `alphaLevels` | `Int` | 16 | 2..256 | Alpha is pre-quantized to this many levels. 256 leaves alpha untouched. |
| `alphaWeight` | `Int` | 8 | | Weight of the alpha term in palette distance. |
| `preserveAlphaInPalette` | `Boolean` | true | | Refine the palette in RGBA rather than RGB. |
| `alphaDiffusionDamping` | `Float` | 0.2 | 0..1 | Scales alpha error before diffusion. 0 keeps alpha exact; 1 diffuses it as strongly as colour. |

!!! tip "Soft edges"

    Soft edges suffer when alpha is pre-quantized. For icons and cutouts,
    raise `alphaLevels` to 32 or 256.

## Compression

| Option | Type | Default | Range | Effect |
| --- | --- | --- | --- | --- |
| `compressionLevel` | `Int` | 9 | 0..9 | DEFLATE level. 0 stores without compressing; 9 is slowest and smallest. See [DEFLATE](deflate.md). |
