package dev.cocoa.speech

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform