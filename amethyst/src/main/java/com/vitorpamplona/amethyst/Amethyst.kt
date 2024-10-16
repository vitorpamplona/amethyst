/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vitorpamplona.amethyst.service.playback.VideoCache
import com.vitorpamplona.ammolite.service.HttpClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.measureTimedValue

class Amethyst : Application() {
    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Service Manager is only active when the activity is active.
    val serviceManager = ServiceManager(applicationIOScope)

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    fun nip95cache() = safeCacheDir.resolve("NIP95")

    val videoCache: VideoCache by lazy {
        val newCache = VideoCache()
        runBlocking {
            newCache.initFileCache(
                this@Amethyst,
                safeCacheDir.resolve("exoplayer"),
            )
        }
        newCache
    }

    val coilCache: DiskCache by lazy {
        DiskCache
            .Builder()
            .directory(safeCacheDir.resolve("image_cache"))
            .maxSizePercent(0.2)
            .maximumMaxSizeBytes(1024 * 1024 * 1024) // 1GB
            .build()
    }

    val coilMemCache: MemoryCache by lazy {
        MemoryCache
            .Builder(this)
            .build()
    }

    fun contentResolverFn(): ContentResolver = contentResolver

    override fun onCreate() {
        super.onCreate()

        instance = this

        HttpClientManager.setDefaultUserAgent("Amethyst/${BuildConfig.VERSION_NAME}")

        if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark") {
            StrictMode.setThreadPolicy(
                ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                VmPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            Looper.getMainLooper().setMessageLogging(LogMonitor())
            ChoreographerHelper.start()
        }

        GlobalScope.launch(Dispatchers.IO) {
            val (value, elapsed) =
                measureTimedValue {
                    // initializes the video cache in a thread
                    videoCache
                }
            Log.d("Rendering Metrics", "VideoCache initialized in $elapsed")
        }
    }

    fun imageLoaderBuilder(): ImageLoader.Builder =
        ImageLoader
            .Builder(this)
            .diskCache { coilCache }
            .memoryCache { coilMemCache }
            .crossfade(true)

    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(instance, npub)

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("Trim Memory $level")
        GlobalScope.launch(Dispatchers.Default) {
            println("Trim Memory Inside $level")
            serviceManager.trimMemory()
        }
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
