# How it works

Pngine turns ARGB pixels into an indexed (colour type 3) PNG in six steps.

```text
ARGB pixels
    │
    ├─▶ 1. Normalise alpha        clamp near-transparent, snap to levels
    ├─▶ 2. Build 5-5-5 histogram  32 768 bins, subsampled by sampleStride
    ├─▶ 3. Median cut             split boxes until maxColors remain
    ├─▶ 4. k-means refinement     move entries onto their centroids
    ├─▶ 5. Remap + dither         nearest entry, diffuse the residual
    └─▶ 6. Write PNG              IHDR, PLTE, tRNS?, IDAT, IEND
                                        │
                                        └─▶ DEFLATE the scanlines
```

## 1. Normalise alpha

Pixels at or below `alphaThreshold` are forced fully transparent, and their
colour is discarded — invisible pixels should not pull the palette around.
The rest are snapped onto `alphaLevels` steps, which collapses near-identical
alpha values so the palette does not waste entries distinguishing them.

This step writes into the caller's array, which is why `encodePixels`
documents that it modifies its input.

## 2. Build a histogram

Colours are binned into a 5-5-5 RGB cube — 32 768 bins. `sampleStride`
controls how many pixels are counted; the default counts all of them.
Transparency is detected across the whole image regardless of stride, so a
single transparent pixel is never missed.

## 3. Median cut

The populated bins are split repeatedly into boxes, each time along the axis
with the widest spread, until there are `maxColors` boxes. Each box
contributes its weighted average as one palette entry. This is Heckbert's
algorithm.

## 4. k-means refinement

Median cut gets close; Lloyd relaxation gets closer. Each pass assigns
sampled pixels to their nearest palette entry and moves that entry to the
centroid of what it captured, `kmeansIterations` times, sampling every
`kmeansSampleRate`-th pixel.

With `preserveAlphaInPalette` the centroid is computed in RGBA, so entries
that exist to represent semi-transparent pixels stay semi-transparent.

When the image has any transparency, entry 0 is reserved for fully
transparent black. If the palette has no transparent entry to promote, the
least significant one is dropped to make room rather than growing past
`maxColors`.

## 5. Remap with error diffusion

Every pixel is matched to its nearest palette entry. Distance uses the
redmean approximation when `usePerceptualDistance` is on, which tracks human
colour perception better than raw RGB distance at almost no cost.

The residual — the difference between the source colour and the entry
chosen — is pushed onto neighbouring pixels by the selected kernel. With
`useGammaCorrectFS` the arithmetic happens in linear light, which keeps
midtones from drifting. With `errorAdaptiveDither` the push is attenuated
where the residual is already small, which stops dithering from adding
noise to areas the palette represents well.

## 6. Write the PNG

The writer emits IHDR, PLTE, an optional tRNS, one IDAT and IEND, per
RFC 2083. Rows use filter type 0.

tRNS is written only when some palette entry is not fully opaque, and it is
truncated after the last non-opaque entry — the spec treats missing trailing
entries as opaque, so there is no reason to spend bytes on them.

IDAT is compressed by [the bundled DEFLATE encoder](deflate.md).

## What it does not do yet

- Sub-8-bit depths. Palettes of 16 colours or fewer could pack at 4 bpp.
- Per-row filter selection. Only filter type 0 is emitted.

Both are on the [roadmap](roadmap.md).
