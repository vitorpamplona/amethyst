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

import android.content.ContentResolver
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.amethyst.model.nip03Timestamp.IncomingOtsEventVerifier
import com.vitorpamplona.amethyst.model.nip03Timestamp.TorAwareOkHttpOtsResolverBuilder
import com.vitorpamplona.amethyst.model.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.model.preferences.TorSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences
import com.vitorpamplona.amethyst.model.privacyOptions.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.model.torState.AccountsTorStateConnector
import com.vitorpamplona.amethyst.model.torState.TorRelayState
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.crashreports.CrashReportCache
import com.vitorpamplona.amethyst.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.amethyst.service.eventCache.MemoryTrimmingService
import com.vitorpamplona.amethyst.service.images.ImageCacheFactory
import com.vitorpamplona.amethyst.service.images.ImageLoaderSetup
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.notifications.PokeyReceiver
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.EncryptionKeyCache
import com.vitorpamplona.amethyst.service.okhttp.OkHttpWebSocket
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.speedLogger.RelaySpeedLogger
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class AppModules(
    val appContext: Context,
) {
    val appAgent = "Amethyst/${BuildConfig.VERSION_NAME}"

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmethystCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    val applicationDefaultScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    // Blocking load of UI Preferences to avoid theme/language blinking
    val uiPrefs by lazy {
        UiSharedPreferences(appContext, applicationIOScope)
    }

    // Blocking load of Tor Settings to avoid connection leaks
    val torPrefs by lazy {
        TorSharedPreferences(appContext, applicationIOScope)
    }

    // App services that should be run as soon as there are subscribers to their flows
    val locationManager = LocationState(appContext, applicationIOScope)
    val connManager = ConnectivityManager(appContext, applicationIOScope)

    val uiState by lazy {
        UiSettingsState(uiPrefs.value, connManager.isMobileOrFalse, applicationIOScope)
    }

    val torManager = TorManager(torPrefs, appContext, applicationIOScope)

    // Service that will run at all times to receive events from Pokey
    val pokeyReceiver = PokeyReceiver()

    // Key cache service to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    // manages all the other connections separately from relays.
    val okHttpClients =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
        )

    // manages all relay connections
    val okHttpClientForRelays =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
        )

    // manages all relay connections
    val okHttpClientForRelaysForDms =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
        )

    // Offers easy methods to know when connections are happening through Tor or not
    val roleBasedHttpClientBuilder = RoleBasedHttpClientBuilder(okHttpClients, torPrefs.value)

    // Application-wide block height request cache
    val otsBlockHeightCache by lazy { OtsBlockHeightCache() }

    val otsResolverBuilder: TorAwareOkHttpOtsResolverBuilder =
        TorAwareOkHttpOtsResolverBuilder(
            roleBasedHttpClientBuilder::okHttpClientForMoney,
            roleBasedHttpClientBuilder::shouldUseTorForMoneyOperations,
            otsBlockHeightCache,
        )

    // Application-wide ots verification cache
    val otsVerifCache by lazy { VerificationStateCache(otsResolverBuilder) }

    val torEvaluatorFlow =
        TorRelayState(
            okHttpClients,
            torPrefs.value,
            applicationDefaultScope,
        )

    // Connects the NostrClient class with okHttp
    val websocketBuilder =
        OkHttpWebSocket.Builder { url ->
            val useTor = torEvaluatorFlow.flow.value.useTor(url)
            if (url in torEvaluatorFlow.flow.value.dmRelayList) {
                okHttpClientForRelaysForDms.getHttpClient(useTor)
            } else {
                okHttpClientForRelays.getHttpClient(useTor)
            }
        }

    // Caches all events in Memory
    val cache: LocalCache = LocalCache

    // Provides a relay pool
    val client: INostrClient = NostrClient(websocketBuilder, applicationDefaultScope)

    // Watches for changes on Tor and Relay List Settings
    val relayProxyClientConnector = RelayProxyClientConnector(torEvaluatorFlow.flow, okHttpClients, connManager, client, torManager, applicationDefaultScope)

    // Verifies and inserts in the cache from all relays, all subscriptions
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Show messages from the Relay and controls their dismissal
    val notifyCoordinator = NotifyCoordinator(client)

    // Authenticates with relays.
    val authCoordinator = AuthCoordinator(client, applicationDefaultScope)

    // Tries to verify new OTS events when they arrive.
    val otsEventVerifier = IncomingOtsEventVerifier(otsVerifCache, cache, applicationDefaultScope)

    val logger = if (isDebug) RelaySpeedLogger(client) else null

    // Coordinates all subscriptions for the Nostr Client
    val sources: RelaySubscriptionsCoordinator = RelaySubscriptionsCoordinator(LocalCache, client, applicationDefaultScope)

    // keeps all accounts live
    val accountsCache =
        AccountCacheState(
            geolocationFlow = locationManager.geohashStateFlow,
            nwcFilterAssembler = sources.nwc,
            contentResolverFn = ::contentResolverFn,
            otsResolverBuilder = otsResolverBuilder,
            cache = cache,
            client = client,
        )

    // Organizes cache clearing
    val trimmingService = MemoryTrimmingService(cache)

    // as new accounts are loaded, updates the state of the TorRelaySettings, which produces new TorRelayEvaluator
    // and reconnects relays if the configuration has been changed.
    val accountsTorStateConnector = AccountsTorStateConnector(accountsCache, torEvaluatorFlow, applicationDefaultScope)

    // saves the .content of NIP-95 blobs in disk to save memory
    val nip95cache: File by lazy { Nip95CacheFactory.new(appContext) }

    // local video cache with disk + memory
    val videoCache: VideoCache by lazy { VideoCacheFactory.new(appContext) }

    // image cache in disk for coil
    val diskCache: DiskCache by lazy { ImageCacheFactory.newDisk(appContext) }

    // image cache in memory for coil
    val memoryCache: MemoryCache by lazy { ImageCacheFactory.newMemory(appContext) }

    // crash report storage
    val crashReportCache: CrashReportCache by lazy { CrashReportCache(appContext) }

    // cache for NIP-11 documents
    val nip11Cache: Nip11CachedRetriever by lazy {
        Nip11CachedRetriever(torEvaluatorFlow::okHttpClientForRelay)
    }

    fun contentResolverFn(): ContentResolver = appContext.contentResolver

    fun setImageLoader() {
        ImageLoaderSetup.setup(appContext, diskCache, memoryCache) { url ->
            okHttpClients.getHttpClient(roleBasedHttpClientBuilder.shouldUseTorForImageDownload(url))
        }
    }

    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(appContext, npub)

    fun initiate(appContext: Context) {
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedCrashSaver(crashReportCache, applicationIOScope))

        // forces initialization of uiPrefs in the main thread to avoid blinking themes
        uiPrefs

        // initializes diskcache on an IO thread.
        applicationIOScope.launch {
            // preloads tor preferences
            torPrefs

            // Sets Coil - Tor - OkHttp link
            setImageLoader()

            // prepares coil's disk cache
            diskCache

            // prepares exoplayer's disk cache
            videoCache
        }

        // registers to receive events
        pokeyReceiver.register(appContext)
    }

    fun terminate(appContext: Context) {
        pokeyReceiver.unregister(appContext)
        applicationIOScope.cancel("Application onTerminate $appContext")
        applicationDefaultScope.cancel("Application onTerminate $appContext")
        accountsCache.clear()
    }

    fun trim() {
        applicationDefaultScope.launch {
            val loggedIn = accountsCache.accounts.value.values
            trimmingService.run(loggedIn, LocalPreferences.allSavedAccounts())
        }
    }
}
