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
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.images.ImageCacheFactory
import com.vitorpamplona.amethyst.service.images.ImageLoaderSetup
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.amethyst.service.notifications.PokeyReceiver
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.EncryptionKeyCache
import com.vitorpamplona.amethyst.service.okhttp.OkHttpWebSocket
import com.vitorpamplona.amethyst.service.ots.OtsBlockHeightCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class Amethyst : Application() {
    val appAgent = "Amethyst/${BuildConfig.VERSION_NAME}"

    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    // Key cache to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    // App services that should be run as soon as there are subscribers to their flows
    val locationManager = LocationState(this, applicationIOScope)
    val torManager = TorManager(this, applicationIOScope)
    val connManager = ConnectivityManager(this, applicationIOScope)

    // Service that will run at all times.
    val pokeyReceiver = PokeyReceiver()

    val okHttpClients =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
        )

    val factory =
        OkHttpWebSocket.BuilderFactory { _, useProxy ->
            okHttpClients.getHttpClient(useProxy)
        }

    val client: NostrClient = NostrClient(factory)

    val serviceManager = ServiceManager(client, applicationIOScope)

    val nip95cache: File by lazy { Nip95CacheFactory.new(this) }
    val videoCache: VideoCache by lazy { VideoCacheFactory.new(this) }
    val diskCache: DiskCache by lazy { ImageCacheFactory.newDisk(this) }
    val memoryCache: MemoryCache by lazy { ImageCacheFactory.newMemory(this) }

    val otsVerifCache by lazy { VerificationStateCache() }
    val otsBlockHeightCache by lazy { OtsBlockHeightCache() }

    override fun onCreate() {
        super.onCreate()
        Log.d("AmethystApp", "onCreate $this")

        instance = this

        if (isDebug()) {
            Logging.setup()
        }

        // initializes diskcache on an IO thread.
        applicationIOScope.launch { videoCache }

        // registers to receive events
        pokeyReceiver.register(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("AmethystApp", "onTerminate $this")

        pokeyReceiver.unregister(this)
        applicationIOScope.cancel("Application onTerminate $this")
    }

    fun contentResolverFn(): ContentResolver = contentResolver

    fun isDebug() = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark"

    fun setImageLoader(shouldUseTor: Boolean?) =
        ImageLoaderSetup.setup(this, diskCache, memoryCache, isDebug()) {
            shouldUseTor?.let { okHttpClients.getHttpClient(it) } ?: okHttpClients.getHttpClient(false)
        }

    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(instance, npub)

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AmethystApp", "onTrimMemory $level")
        applicationIOScope.launch(Dispatchers.Default) {
            serviceManager.trimMemory()
        }
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
