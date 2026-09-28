import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlinMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

group = "org.onedroid"
version = "0.1.0"

kotlin {
    explicitApi()

    android {
        namespace = "org.onedroid.pngine"
        compileSdk = 36
        minSdk = 21

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js {
        browser()
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    // Sign whenever a key is configured. Without one, local publishing still
    // works for dry runs; Maven Central rejects unsigned uploads on its own.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "pngine", version.toString())

    pom {
        name.set("Pngine")
        description.set(
            "PNG-8 encoder for Kotlin Multiplatform: palette quantization with full " +
                "alpha support, in pure Kotlin with no dependencies.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/OneDroid/pngine")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("tawhidmonowar")
                name.set("Tawhid Monowar")
                url.set("https://github.com/OneDroid")
            }
        }
        scm {
            url.set("https://github.com/OneDroid/pngine")
            connection.set("scm:git:https://github.com/OneDroid/pngine.git")
            developerConnection.set("scm:git:ssh://git@github.com/OneDroid/pngine.git")
        }
    }
}
