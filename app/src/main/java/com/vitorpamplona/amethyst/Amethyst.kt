package com.vitorpamplona.amethyst

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import coil.ImageLoader
import com.vitorpamplona.amethyst.service.playback.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.time.measureTimedValue

class Amethyst : Application() {
    val videoCache: VideoCache by lazy {
        val newCache = VideoCache()
        newCache.initFileCache(instance)
        newCache
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

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

        GlobalScope.launch(Dispatchers.IO) {
            val (value, elapsed) = measureTimedValue {
                // initializes the video cache in a thread
                videoCache
            }
            Log.d("Rendering Metrics", "VideoCache initialized in $elapsed")
        }
    }

    fun imageLoaderBuilder(): ImageLoader.Builder {
        return ImageLoader.Builder(applicationContext).diskCache {
            SingletonDiskCache.get(applicationContext)
        }
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
