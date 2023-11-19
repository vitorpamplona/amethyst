package com.vitorpamplona.amethyst

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import com.vitorpamplona.amethyst.service.playback.VideoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.measureTimedValue

class Amethyst : Application() {
    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    val videoCache: VideoCache by lazy {
        val newCache = VideoCache()
        newCache.initFileCache(this)
        newCache
    }

    private val imageCache: DiskCache by lazy {
        DiskCache.Builder()
            .directory(applicationContext.safeCacheDir.resolve("image_cache"))
            .maxSizePercent(0.2)
            .maximumMaxSizeBytes(500L * 1024 * 1024) // 250MB
            .build()
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
                    .detectAll()
                    .penaltyLog()
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
        return ImageLoader.Builder(applicationContext).diskCache { imageCache }
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}

internal val Context.safeCacheDir: File
    get() {
        val cacheDir = checkNotNull(cacheDir) { "cacheDir == null" }
        return cacheDir.apply { mkdirs() }
    }
