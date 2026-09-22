package org.onedroid.sample

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform