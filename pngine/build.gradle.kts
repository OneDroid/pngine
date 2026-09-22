import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "org.onedroid.pngine"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            // The Dokka bundled with AGP 8.13 cannot parse Java 24+ version
            // strings and fails the task. Skip the javadoc jar there.
            if (JavaVersion.current() <= JavaVersion.VERSION_23) {
                withJavadocJar()
            }
        }
    }
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

group = "org.onedroid"
version = "0.1.0"

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "pngine"
            afterEvaluate { from(components["release"]) }
        }
    }
}
