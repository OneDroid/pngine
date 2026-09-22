This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

It demonstrates the [Pngine](../README.md) PNG-8 encoder from the parent
directory.

### Using Pngine

Pngine is a Kotlin Multiplatform library with no dependencies, so the sample
calls it straight from `commonMain` and every target encodes for real:

```kotlin
val png = Pngine.encodePixels(pixels, width, height, PngineOptions(maxColors = 64))
```

The only `expect`/`actual` in the sample is
[PlatformPng](./shared/src/commonMain/kotlin/org/onedroid/sample/png/PlatformPng.kt),
which fetches the platform's own 32-bit PNG encoder purely as a size
baseline — `Bitmap.compress` on Android, `ImageIO` on desktop, nothing on
iOS or web.

The demo screen generates a gradient and a soft-edged disc (partial alpha),
encodes it, and reports the sizes. Measured at 64 colours:

| Target | PNG-8 | Baseline | Encode |
| --- | --- | --- | --- |
| Android emulator (Pixel 10 Pro XL) | 12.9 KB | 29.4 KB | 369 ms |
| Web (Wasm, Chrome) | 12.8 KB | — | 73 ms |

#### Build setup

The library lives in the parent directory and is wired in as a composite
build, so edits to it are picked up without publishing:

```kotlin
// settings.gradle.kts
includeBuild("..")
```

Gradle substitutes the `org.onedroid:pngine` dependency — declared in
[gradle/libs.versions.toml](./gradle/libs.versions.toml) and used from
`commonMain` in [shared/build.gradle.kts](./shared/build.gradle.kts) — with
that build's project. Both builds must stay on the same AGP version;
Gradle refuses a composite build that mixes two.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

[PngineSampleTest](./shared/src/commonTest/kotlin/org/onedroid/sample/PngineSampleTest.kt)
lives in `commonTest`, so each of those tasks exercises the sample's own
Pngine encoding path on that target.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).