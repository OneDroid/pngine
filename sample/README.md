This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

It demonstrates the [Pngine](../README.md) PNG-8 encoder from the parent
directory.

### Using Pngine

Pngine is an **Android** library: it takes `android.graphics.Bitmap` and ships
as an AAR. So only the Android target encodes for real. The shared code hides
this behind an `expect object PngEncoder`
([commonMain](./shared/src/commonMain/kotlin/org/onedroid/sample/png/PngEncoder.kt)):

- [androidMain](./shared/src/androidMain/kotlin/org/onedroid/sample/png/PngEncoder.android.kt)
  calls `Pngine.encodePixels`, and `Bitmap.compress(PNG)` for a size baseline.
- The JVM, iOS, JS and Wasm actuals report `isSupported = false`, and the UI
  shows why instead of the demo.

The demo screen generates a gradient + soft-edged disc (alpha), encodes it,
and reports both sizes. On an emulator at 64 colours: 29.4 KB → 12.9 KB, 56%
smaller, ~360 ms.

#### Build setup

This sample is a **separate Gradle build** from the library — it runs AGP
9.1.1 while the library runs AGP 8.13.2, and Gradle refuses two AGP versions
in one build, so a composite build (`includeBuild`) is not an option. The
sample consumes the library from `mavenLocal` instead.

Publish the library before building the sample:

```bash
cd .. && ./gradlew publishToMavenLocal
```

Then build as usual. The dependency is declared in
[gradle/libs.versions.toml](./gradle/libs.versions.toml) as
`org.onedroid:pngine:0.1.0` and wired into `androidMain` in
[shared/build.gradle.kts](./shared/build.gradle.kts).

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

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).