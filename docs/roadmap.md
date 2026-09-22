# Roadmap

Ordered by expected size win.

- **Sub-8-bit depths.** Palettes of 16 colours or fewer can pack at 4 bpp,
  halving IDAT. Currently the bit depth is always 8.
- **Per-row filter selection.** Only filter type 0 is emitted. Trying
  `Up`/`Sub` per row wins on flat and vertically repeating images.
- **Empty-cluster reseeding.** k-means clusters that lose all members keep
  their old value instead of being reseeded, wasting palette slots.
- **Output-size guard.** Fall back to the source when quantization does not
  actually save bytes.
- **Faster nearest-colour search.** Currently a linear scan over the palette
  per pixel.
- **Faster DEFLATE.** The bundled compressor is straightforward rather than
  tuned; zlib is still quicker on the JVM.

## Not planned

- **Decoding.** Pngine writes PNGs; every platform already reads them.
- **Other formats.** WebP and AVIF need entropy coders far larger than a
  dependency-free library can carry.
