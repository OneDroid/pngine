# Prior art

Every algorithm in Pngine is published work. It claims no novelty; it claims
a licence and a platform. Full citations are in
[NOTICE](https://github.com/OneDroid/pngine/blob/main/NOTICE).

| Component | Source |
| --- | --- |
| Median-cut quantization | Heckbert (1982) |
| k-means / Lloyd refinement | Lloyd (1982) |
| Floyd-Steinberg error diffusion | Floyd & Steinberg (1976) |
| Jarvis-Judice-Ninke error diffusion | Jarvis, Judice & Ninke (1976) |
| Perceptual colour distance ("redmean") | Riemersma / CompuPhase |
| PNG container | RFC 2083 |
| DEFLATE | RFC 1951 |
| zlib container | RFC 1950 |

No code was taken from pngquant or libimagequant.

## Related projects

- [pngquant / libimagequant](https://pngquant.org/lib/) — the reference
  implementation of this idea. Better quality and much faster, but it needs
  JNI and is GPL-or-commercial.
- [zlib](https://zlib.net/) — the DEFLATE implementation everything else
  wraps.

## Licence

Pngine is Apache-2.0. See
[LICENSE](https://github.com/OneDroid/pngine/blob/main/LICENSE).
