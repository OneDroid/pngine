# Installation

!!! note "Not on Maven Central yet"

    Pngine is at version `0.1.0` and has not been published to a public
    repository. Until it is, build it from source — see
    [from source](#from-source) below — or consume it through a
    [composite build](#composite-build), which is what the
    [sample app](sample.md) does.

## Gradle

Kotlin Multiplatform, from common code:

```kotlin title="build.gradle.kts"
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.onedroid:pngine:0.1.0")
        }
    }
}
```

Android-only project:

```kotlin title="build.gradle.kts"
dependencies {
    implementation("org.onedroid:pngine:0.1.0")
}
```

## From source

Publish to your local Maven repository:

```bash
git clone https://github.com/OneDroid/pngine.git
cd Pngine
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to the consuming build, scoped to this group so it
does not slow down or shadow anything else:

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        mavenLocal {
            mavenContent {
                includeGroup("org.onedroid")
            }
        }
        mavenCentral()
        google()
    }
}
```

## Composite build

If Pngine sits next to your project, skip publishing entirely — edits to
the library are picked up on the next build:

```kotlin title="settings.gradle.kts"
includeBuild("../Pngine")
```

Gradle substitutes the `org.onedroid:pngine` dependency with that build's
project. Both builds must use the same Android Gradle Plugin version;
Gradle refuses a composite build that mixes two.

## Requirements

| | |
| --- | --- |
| Kotlin | 2.4.20 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.5.1 |
| Android `minSdk` | 21 |
| JVM target | 11 |

Pngine has no runtime dependencies beyond the Kotlin standard library.
