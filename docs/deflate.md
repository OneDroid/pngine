# DEFLATE

PNG stores its image data as a zlib stream, and `java.util.zip.Deflater`
exists only on the JVM. Rather than reach for `expect`/`actual` and a
different compressor per platform — zlib via cinterop on iOS, an npm package
in the browser — Pngine implements DEFLATE itself, in common Kotlin.

That is what makes the library dependency-free and identical on every
target. It lives in `org.onedroid.pngine.internal` and is not part of the
public API.

## What it implements

- **RFC 1951** — DEFLATE: stored, fixed-Huffman and dynamic-Huffman blocks
- **RFC 1950** — the zlib container: 2-byte header, Adler-32 trailer
- **CRC-32** — for PNG's own per-chunk checksums

## How it compresses

A conventional LZ77 front end feeds a block writer.

**Matching.** A 32 KiB sliding window with hash chains over 3-byte hashes.
Each position is inserted once; the search walks the chain for that hash,
bounded by the level's chain limit, and stops early once a match reaches the
level's "nice length". Levels 4 and up also use lazy matching: before
committing to a match, the encoder checks whether the next position starts a
longer one, and if so emits the current byte as a literal instead.

**Block writing.** Symbols accumulate until 16 384 of them, then the block
is written. Before writing, all three encodings are costed exactly — stored,
fixed Huffman, and dynamic Huffman including its code-length table — and the
cheapest wins. Incompressible input therefore falls back to stored blocks
and cannot balloon.

**Huffman.** Code lengths come from repeated two-smallest merges. If the
resulting tree is deeper than the 15-bit limit the format allows,
frequencies are halved and the tree is rebuilt, which flattens it and always
terminates. Both trees are given at least two codes, so no decoder has to
deal with a single-code tree.

## Levels

`compressionLevel` maps to the chain length, nice length and lazy matching,
in the spirit of zlib's own table.

| Level | Max chain | Nice length | Lazy |
| --- | --- | --- | --- |
| 0 | — | — | stored blocks only |
| 1 | 4 | 8 | no |
| 2 | 8 | 16 | no |
| 3 | 32 | 32 | no |
| 4 | 16 | 16 | yes |
| 5 | 32 | 32 | yes |
| 6 | 128 | 128 | yes |
| 7 | 256 | 128 | yes |
| 8 | 1024 | 258 | yes |
| 9 | 4096 | 258 | yes |

## How it compares to zlib

Output is a valid zlib stream that any inflater reads. It is not
bit-identical to zlib's, but the ratio is competitive — measured at level 9:

| Input | Pngine | zlib | Raw |
| --- | --- | --- | --- |
| PNG-style gradient rows | 358 B | 361 B | 30 816 B |
| Repeated text | 154 B | 154 B | 84 000 B |
| Random bytes | 60 026 B | 60 026 B | 60 000 B |

zlib is still faster on the JVM, where it is native code. Speed is on the
[roadmap](roadmap.md); correctness and portability came first.

## How it is verified

Round-trip tests run in two independent ways:

- **Common tests** inflate the output with a decoder written for the test
  suite, so the check runs on JVM, Android, iOS, JS and Wasm.
- **JVM tests** repeat the same cases against `java.util.zip.Inflater`, so a
  shared misreading of RFC 1951 between the encoder and the test decoder
  cannot hide a bug.

Cases cover empty input, inputs shorter than the minimum match, long runs,
incompressible noise, data larger than one stored block, matches that run to
the final byte, and distances that span the whole window — at every level
from 0 to 9. See [Testing](testing.md).
