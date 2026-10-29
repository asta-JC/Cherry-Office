package com.asta.cherryoffice

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CherryOfficeApplication : Application() {
    private var launchIntroShown = false

    /** Only a fresh normal launch can consume the intro; never replay during this process. */
    fun consumeLaunchIntro(): Boolean {
        if (launchIntroShown) return false
        launchIntroShown = true
        return true
    }
}
