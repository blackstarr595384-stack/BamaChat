package com.example.bamachat.util

enum class PlatformTarget { ANDROID, IOS, DESKTOP }

object Platform {
    val current: PlatformTarget get() = PlatformTarget.ANDROID
}
