package com.vitorpamplona.amethyst

import android.app.Application
import com.vitorpamplona.amethyst.database.AppDatabase

class Amethyst : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
