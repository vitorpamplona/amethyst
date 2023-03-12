package com.vitorpamplona.amethyst

import android.app.Application

class Amethyst : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
