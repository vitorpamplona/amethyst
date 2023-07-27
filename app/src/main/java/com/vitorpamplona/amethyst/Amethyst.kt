package com.vitorpamplona.amethyst

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import com.vitorpamplona.amethyst.database.AppDatabase
import com.vitorpamplona.amethyst.database.EventMapping

class Amethyst : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val eventDatabase: EventMapping by lazy { EventMapping(database.eventDao()) }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        instance = this

        VideoCache.initFileCache(instance)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectFileUriExposure()
                    .detectContentUriWithoutPermission()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    // .penaltyDeath()
                    .build()
            )
        }
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
