package com.example.bamachat

import android.content.ComponentName
import android.content.Intent

internal object TestAppIdentity {
    const val APPLICATION_ID = "de.bamachat.app"
    const val MAIN_ACTIVITY = "com.example.bamachat.MainActivity"

    val mainActivityComponent: ComponentName
        get() = ComponentName(APPLICATION_ID, MAIN_ACTIVITY)

    fun mainActivityIntent(): Intent = Intent.makeMainActivity(mainActivityComponent)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
}
