/**
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.eventCache.MemoryTrimmingService
import com.vitorpamplona.amethyst.service.images.ImageCacheFactory
import com.vitorpamplona.amethyst.service.images.ImageLoaderSetup
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.amethyst.service.notifications.PokeyReceiver
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.EncryptionKeyCache
import com.vitorpamplona.amethyst.service.okhttp.OkHttpWebSocket
import com.vitorpamplona.amethyst.service.okhttp.ProxySettingsAnchor
import com.vitorpamplona.amethyst.service.ots.OtsBlockHeightCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelaySpeedLogger
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
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

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    // Key cache service to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    // App services that should be run as soon as there are subscribers to their flows
    val locationManager = LocationState(this, applicationIOScope)
    val torManager = TorManager(this, applicationIOScope)
    val connManager = ConnectivityManager(this, applicationIOScope)

    // Service that will run at all times to receive events from Pokey
    val pokeyReceiver = PokeyReceiver()

    // creates okHttpClients based on the conditions of the connection and tor status
    val okHttpClients =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
        )

    val torProxySettingsAnchor = ProxySettingsAnchor()

    // Connects the NostrClient class with okHttp
    val websocketBuilder =
        OkHttpWebSocket.Builder { url ->
            okHttpClients.getHttpClient(torProxySettingsAnchor.useProxy(url))
        }

    // Caches all events in Memory
    val cache: LocalCache = LocalCache

    // Organizes cache clearing
    val trimmingService = MemoryTrimmingService(cache)

    // Provides a relay pool
    val client: NostrClient = NostrClient(websocketBuilder, applicationIOScope)

    // Watches for changes on Tor and Relay List Settings
    val relayProxyClientConnector = RelayProxyClientConnector(torProxySettingsAnchor, okHttpClients, connManager, client, applicationIOScope)

    // Verifies and inserts in the cache from all relays, all subscriptions
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Show messages from the Relay and controls their dismissal
    val notifyCoordinator = NotifyCoordinator(client)

    // Authenticates with relays.
    val authCoordinator = AuthCoordinator(client)

    val logger = if (isDebug) RelaySpeedLogger(client) else null

    // Coordinates all subscriptions for the Nostr Client
    val sources: RelaySubscriptionsCoordinator = RelaySubscriptionsCoordinator(LocalCache, client, applicationIOScope)

    // saves the .content of NIP-95 blobs in disk to save memory
    val nip95cache: File by lazy { Nip95CacheFactory.new(this) }

    // local video cache with disk + memory
    val videoCache: VideoCache by lazy { VideoCacheFactory.new(this) }

    // image cache in disk for coil
    val diskCache: DiskCache by lazy { ImageCacheFactory.newDisk(this) }

    // image cache in memory for coil
    val memoryCache: MemoryCache by lazy { ImageCacheFactory.newMemory(this) }

    // Application-wide ots verification cache
    val otsVerifCache by lazy { VerificationStateCache() }

    // Application-wide block height request cache
    val otsBlockHeightCache by lazy { OtsBlockHeightCache() }

    override fun onCreate() {
        super.onCreate()
        Log.d("AmethystApp", "onCreate $this")

        instance = this

        if (isDebug) {
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

    fun setImageLoader(shouldUseTor: (String) -> Boolean?) {
        ImageLoaderSetup.setup(this, diskCache, memoryCache) { url ->
            shouldUseTor(url)?.let { okHttpClients.getHttpClient(it) } ?: okHttpClients.getHttpClient(false)
        }
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
            trimmingService.run(null, LocalPreferences.allSavedAccounts())
        }
    }

    companion object {
        lateinit var instance: Amethyst
            private set
    }
}
