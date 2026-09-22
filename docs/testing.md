# Testing

```bash
./gradlew :pngine:allTests
```

That runs the common suite on every target — JVM, iOS simulator, and Node
for JS and Wasm. Android host tests are a separate task:

```bash
./gradlew :pngine:testAndroidHostTest
```

## What the tests assert

**The encoder** is exercised through `Pngine.encodePixels`, and the emitted
bytes are decoded by a strict in-test PNG reader rather than a platform
decoder — a malformed chunk length, CRC or row filter fails the test instead
of being quietly tolerated. Cases cover structural validity, dimension
round-tripping, exact reproduction of flat colour, transparency survival,
tRNS presence and absence, palette size limits, both dither kernels, and
argument rejection.

**The compressor** is round-tripped at every level from 0 to 9 over empty
input, single bytes, long runs, repeated text, incompressible noise, data
larger than one stored block, matches ending on the final byte, and
distances spanning the whole window. It is also checked for the properties
that matter in practice: repetitive data must compress hard, incompressible
data must not balloon, and level 9 must not be worse than level 1.

## Two independent decoders

Common tests inflate with a decoder written for the test suite, so the
round-trip runs on JS, Wasm and native as well as the JVM. JVM tests repeat
the same cases against `java.util.zip.Inflater`. A bug that both the encoder
and the in-house decoder share would pass the first and fail the second.

That is not hypothetical: the pair caught two real bugs during development —
a match search that read one byte past the end of the input, and an Adler-32
accumulator that overflowed a signed `Int` because zlib's 5552-byte
deferral is sized for *unsigned* 32-bit arithmetic.

## Layout

| Source set | Contents |
| --- | --- |
| `commonTest` | Encoder tests, DEFLATE round-trips, the in-test PNG reader and inflater |
| `jvmTest` | The same DEFLATE cases against `java.util.zip.Inflater` |
