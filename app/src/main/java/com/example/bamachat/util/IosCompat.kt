package com.example.bamachat.util

object IosCompat {

    fun interface Supplier<T> {
        fun get(): T
    }

    fun interface Function<T, R> {
        fun apply(input: T): R
    }
}
