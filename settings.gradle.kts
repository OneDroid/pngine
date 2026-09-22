pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Left at the default PREFER_PROJECT: the Kotlin/JS and Wasm toolchains
    // register their own Node.js distribution repository, and the stricter
    // modes reject it.
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pngine"
include(":pngine")
