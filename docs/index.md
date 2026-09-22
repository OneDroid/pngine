# Pngine

PNG-8 encoder for Kotlin Multiplatform — Android, JVM/desktop, iOS and the
web (JS and Wasm). Palette quantization with full alpha support, written in
pure Kotlin: no NDK, no native binaries, no third-party dependencies, and no
`java.util.zip` — the DEFLATE compressor is part of the library.

ARGB pixels in, PNG `ByteArray` out. On Android, `android.graphics.Bitmap`
in as well.

```kotlin
val bytes = Pngine.encodePixels(argbPixels, width, height)
```

## Why it exists

`Bitmap.compress(PNG, …)` always writes 24/32-bit PNG and ignores the
quality argument, so it does not reduce colour depth at all. The usual
answer is [pngquant / libimagequant](https://pngquant.org/lib/), which is
excellent but needs JNI and is GPL-or-commercial.

Pngine fills the gap: Apache-2.0, pure Kotlin, runs on device — and, being
pure Kotlin all the way down to DEFLATE, runs unchanged on iOS, desktop and
in the browser.

It is not a reimplementation of anything novel — see [Prior art](prior-art.md).
It is a permissively licensed, dependency-free implementation of
well-established algorithms.

## What it costs you

Measured on the sample app's 320×320 test image (a two-axis gradient behind
a soft-edged disc) at a 64-colour palette:

| Target | Pngine PNG-8 | Platform PNG | Encode time |
| --- | --- | --- | --- |
| Android emulator (Pixel 10 Pro XL) | 12.9 KB | 29.4 KB | 369 ms |
| Web (Wasm, Chrome) | 12.8 KB | — | 73 ms |

Encoding is CPU-bound. Run it off the main thread.

## Where to go next

<div class="grid cards" markdown>

- :material-download: **[Installation](installation.md)** — add the dependency
- :material-code-braces: **[Usage](usage.md)** — encode pixels, bitmaps, off-thread
- :material-tune: **[Options](options.md)** — every knob and what it does
- :material-cog: **[How it works](how-it-works.md)** — the pipeline, step by step

</div>

## Licence

Apache-2.0. See [LICENSE](https://github.com/OneDroid/pngine/blob/main/LICENSE).
