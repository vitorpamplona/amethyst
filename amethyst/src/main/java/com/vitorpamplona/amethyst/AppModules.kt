/*
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

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.amethyst.model.nip03Timestamp.IncomingOtsEventVerifier
import com.vitorpamplona.amethyst.model.nip03Timestamp.TorAwareOkHttpOtsResolverBuilder
import com.vitorpamplona.amethyst.model.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.model.preferences.NamecoinSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.OtsSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.TorSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences
import com.vitorpamplona.amethyst.model.privacyOptions.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.model.torState.AccountsTorStateConnector
import com.vitorpamplona.amethyst.model.torState.TorRelayState
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityManager
import com.vitorpamplona.amethyst.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.service.crashreports.CrashReportCache
import com.vitorpamplona.amethyst.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.amethyst.service.eventCache.MemoryTrimmingService
import com.vitorpamplona.amethyst.service.images.ImageCacheFactory
import com.vitorpamplona.amethyst.service.images.ImageLoaderSetup
import com.vitorpamplona.amethyst.service.images.ThumbnailDiskCache
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.notifications.AlwaysOnNotificationServiceManager
import com.vitorpamplona.amethyst.service.notifications.NotificationDispatcher
import com.vitorpamplona.amethyst.service.notifications.PokeyReceiver
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManagerForRelays
import com.vitorpamplona.amethyst.service.okhttp.EncryptionKeyCache
import com.vitorpamplona.amethyst.service.okhttp.OkHttpWebSocket
import com.vitorpamplona.amethyst.service.okhttp.SurgeDns
import com.vitorpamplona.amethyst.service.okhttp.SurgeDnsStore
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.speedLogger.RelaySpeedLogger
import com.vitorpamplona.amethyst.service.safeCacheDir
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.resourceCacheInit
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayLogger
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.stats.RelayReqStats
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TOR_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    // Pre-load both preference DataStores in parallel on IO threads.
    // Both constructors use runBlocking internally, so starting them concurrently
    // reduces total blocking time from (torPrefs + uiPrefs) to ~max(torPrefs, uiPrefs).
    private val uiPrefsDeferred =
        applicationIOScope.async {
            val prefs = UiSharedPreferences.uiPreferences(appContext) ?: UiSettings()
            UiSharedPreferences(prefs, appContext, applicationIOScope)
        }

    private val torPrefsDeferred =
        applicationIOScope.async {
            val prefs = TorSharedPreferences.torPreferences(appContext) ?: TorSettings()
            TorSharedPreferences(prefs, appContext, applicationIOScope)
        }

    // Blocking load of UI Preferences to avoid theme/language blinking
    val uiPrefs by lazy {
        Log.d("AppModules", "UiSharedPreferences Init")
        runBlocking { uiPrefsDeferred.await() }
    }

    // Blocking load of Tor Settings to avoid connection leaks
    val torPrefs by lazy {
        Log.d("AppModules", "TorSharedPreferences Init")
        runBlocking { torPrefsDeferred.await() }
    }

    // Namecoin ElectrumX server preferences (global, like Tor settings)
    val namecoinPrefs by lazy {
        Log.d("AppModules", "NamecoinSharedPreferences Init")
        NamecoinSharedPreferences(appContext, applicationIOScope)
    }

    // OTS blockchain explorer preferences (global, like Tor settings)
    val otsPrefs by lazy {
        Log.d("AppModules", "OtsSharedPreferences Init")
        OtsSharedPreferences(appContext, applicationIOScope)
    }

    // App services that should be run as soon as there are subscribers to their flows
    val locationManager by lazy {
        Log.d("AppModules", "LocationManager Init")
        LocationState(appContext, applicationIOScope)
    }
    val connManager = ConnectivityManager(appContext, applicationIOScope)

    val uiState by lazy {
        Log.d("AppModules", "UiSettingsState Init")
        UiSettingsState(uiPrefs.value, connManager.isMobileOrFalse, applicationIOScope)
    }

    val torManager = TorManager(torPrefs, appContext, applicationIOScope)

    // Whenever the underlying network identity changes (wifi↔cellular, regained from
    // offline, etc.) we clear any active Tor session bypass so the manager re-attempts
    // bootstrap on the new network. The remembered-approval window is unaffected: if Tor
    // stays stuck we will silently bypass again after the timeout fires.
    init {
        applicationIOScope.launch {
            connManager.status
                .map { (it as? ConnectivityStatus.Active)?.networkId }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { torManager.clearSessionBypass() }
        }
    }

    // Service that will run at all times to receive events from Pokey
    val pokeyReceiver = PokeyReceiver()

    // Key cache service to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    // Concurrent, caching DNS resolver shared by every OkHttp client built below — a host
    // resolved for an image fetch is reused when a relay handshake or NIP-05 lookup hits the
    // same host.
    val surgeDns = SurgeDns()

    // Persists [surgeDns]'s positive cache across process restarts so cold starts don't pay
    // ~700 sync getaddrinfo calls. Restored entries fall through to the stale-while-revalidate
    // path on first lookup.
    val dnsStore = SurgeDnsStore(appContext, surgeDns)

    // manages all the other connections separately from relays.
    val okHttpClients =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
            dns = surgeDns,
        )

    // Offers easy methods to know when connections are happening through Tor or not
    val roleBasedHttpClientBuilder = RoleBasedHttpClientBuilder(okHttpClients, torPrefs.value)

    val electrumXClient by lazy {
        Log.d("AppModules", "ElectrumXClient Init")
        val client =
            ElectrumXClient(
                socketFactory = { roleBasedHttpClientBuilder.socketFactoryForNip05() },
            )
        applicationIOScope.launch {
            try {
                val pinnedCerts = namecoinPrefs.loadPinnedCerts()
                if (pinnedCerts.isNotEmpty()) {
                    client.setDynamicCerts(pinnedCerts)
                }
            } catch (_: Exception) {
                // Non-fatal — defaults will still work
            }
        }
        client
    }

    val namecoinResolver by
        lazy {
            Log.d("AppModules", "Namecoin Resolver Init")
            NamecoinNameResolver(
                electrumxClient = electrumXClient,
                serverListProvider = {
                    // User-configured custom servers take priority
                    namecoinPrefs.customServersOrNull
                        ?: if (roleBasedHttpClientBuilder.shouldUseTorForNIP05("https://electrumx.example.com")) {
                            TOR_ELECTRUMX_SERVERS
                        } else {
                            DEFAULT_ELECTRUMX_SERVERS
                        }
                },
            )
        }

    val nip05Client by
        lazy {
            Log.d("AppModules", "NIP05Client Init")
            Nip05Client(
                fetcher = OkHttpNip05Fetcher(roleBasedHttpClientBuilder::okHttpClientForNip05),
                namecoinResolverBuilder = { namecoinResolver },
            )
        }

    val otsResolverBuilder by
        lazy {
            Log.d("AppModules", "OtsResolverBuilder Init")
            TorAwareOkHttpOtsResolverBuilder(
                roleBasedHttpClientBuilder::okHttpClientForMoney,
                roleBasedHttpClientBuilder::shouldUseTorForMoneyOperations,
                OtsBlockHeightCache(),
                customExplorerUrl = { otsPrefs.current.normalizedUrl() },
            )
        }

    // Application-wide ots verification cache
    val otsVerifCache by lazy {
        Log.d("AppModules", "OtsCache Init")
        VerificationStateCache(otsResolverBuilder)
    }

    val torEvaluatorFlow =
        TorRelayState(
            okHttpClients,
            torPrefs.value,
            applicationIOScope,
        )

    // manages all relay connections
    val okHttpClientForRelays =
        DualHttpClientManagerForRelays(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            scope = applicationIOScope,
            dns = surgeDns,
        )

    // Connects the INostrClient class with okHttp
    val websocketBuilder =
        OkHttpWebSocket.Builder { url ->
            val useTor = torEvaluatorFlow.flow.value.useTor(url)
            okHttpClientForRelays.getHttpClient(useTor)
        }

    // Caches all events in Memory
    val cache: LocalCache = LocalCache

    // Provides a relay pool
    val client: INostrClient = NostrClient(websocketBuilder, applicationIOScope)

    // Watches for changes on Tor and Relay List Settings
    val relayProxyClientConnector =
        RelayProxyClientConnector(
            torEvaluatorFlow.flow,
            okHttpClientForRelays,
            connManager,
            torManager,
            client,
            applicationIOScope,
        )

    // Verifies and inserts in the cache from all relays, all subscriptions
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Show messages from the Relay and controls their dismissal
    val notifyCoordinator = NotifyCoordinator(client)

    // Authenticates with relays.
    val authCoordinator = AuthCoordinator(client, applicationIOScope)

    // Tries to verify new OTS events when they arrive.
    val otsEventVerifier =
        IncomingOtsEventVerifier(
            otsVerifCache = { otsVerifCache },
            cache = cache,
            scope = applicationIOScope,
        )

    // Tracks if it is possible to connect to relays.
    val failureTracker = RelayOfflineTracker(client)

    // Captures statistics about relays
    val relayStats = RelayStats(client)

    // Logs debug messages when needed
    val detailedLogger = if (isDebug) RelayLogger(client, debugSending = false, debugReceiving = false) else null
    val relayReqStats = if (isDebug) RelayReqStats(client) else null
    val logger = if (isDebug) RelaySpeedLogger(client) else null

    // Coordinates all subscriptions for the Nostr Client
    val sources: RelaySubscriptionsCoordinator =
        RelaySubscriptionsCoordinator(
            LocalCache,
            client,
            authCoordinator.receiver,
            failureTracker,
            applicationIOScope,
        )

    // keeps all accounts live
    val accountsCache =
        AccountCacheState(
            geolocationFlow = { locationManager.geohashStateFlow },
            nwcFilterAssembler = { sources.nwc },
            contentResolverFn = { appContext.contentResolver },
            otsResolverBuilder = { otsResolverBuilder.build() },
            cache = cache,
            client = client,
            rootFilesDir = { appContext.filesDir },
        )

    val sessionManager =
        AccountSessionManager(
            accountsCache = accountsCache,
            nip05ClientBuilder = { nip05Client },
            clientBuilder = { client },
            localPreferences = LocalPreferences,
            scope = applicationIOScope,
        )

    fun subscribedFlow(
        address: Address,
        account: Account,
    ): Flow<NoteState> {
        val note = cache.getOrCreateAddressableNote(address)

        val userSub = UserFinderQueryState(note.author ?: cache.getOrCreateUser(address.pubKeyHex), account)
        val noteSub = EventFinderQueryState(note, account)

        return note
            .flow()
            .metadata.stateFlow
            .onStart {
                sources.userFinder.subscribe(userSub)
                sources.eventFinder.subscribe(noteSub)
            }.onCompletion {
                sources.eventFinder.unsubscribe(noteSub)
                sources.userFinder.unsubscribe(userSub)
            }
    }

    val blossomResolver by lazy {
        Log.d("AppModules", "BlossomServerResolver Init")
        BlossomServerResolver(
            loggedInUsers = { listOfNotNull(sessionManager.loggedInAccount()?.pubKey) },
            blossomServers = { addressesToSubscribe ->
                val account = sessionManager.loggedInAccount() ?: return@BlossomServerResolver listOf()
                addressesToSubscribe.map { address ->
                    subscribedFlow(address, account).transform {
                        val event = it.note.event as? BlossomServersEvent
                        if (event != null) {
                            emit(event)
                        }
                    }
                }
            },
            httpClientBuilder = roleBasedHttpClientBuilder,
        )
    }

    // Manages always-on notification service lifecycle. Preloads every saved
    // writable account while enabled so GiftWraps for non-active accounts still
    // get unwrapped by their owning account's newNotesPreProcessor.
    val alwaysOnNotificationServiceManager =
        AlwaysOnNotificationServiceManager(
            context = appContext,
            scope = applicationIOScope,
            accountsCache = accountsCache,
            localPreferences = LocalPreferences,
            activePubKeyProvider = { sessionManager.loggedInAccount()?.pubKey },
        )

    // Observes LocalCache for notification-relevant events and routes them to
    // EventNotificationConsumer. Sources: FCM, UnifiedPush, Pokey, active relay
    // subscriptions, and NotificationRelayService.
    val notificationDispatcher = NotificationDispatcher(appContext, applicationIOScope)

    // Organizes cache clearing
    val trimmingService by
        lazy {
            MemoryTrimmingService(cache)
        }

    // as new accounts are loaded, updates the state of the TorRelaySettings, which produces new TorRelayEvaluator
    // and reconnects relays if the configuration has been changed.
    val accountsTorStateConnector = AccountsTorStateConnector(accountsCache, torEvaluatorFlow, applicationIOScope)

    // saves the .content of NIP-95 blobs in disk to save memory
    val nip95cache: File by lazy {
        Log.d("AppModules", "NIP95 Cache Init")
        Nip95CacheFactory.new(appContext)
    }

    // local video cache with disk + memory
    val videoCache: VideoCache by lazy {
        Log.d("AppModules", "VideoCache Init")
        VideoCacheFactory.new(appContext)
    }

    // image cache in disk for coil
    val diskCache: DiskCache by lazy {
        Log.d("AppModules", "ImageCacheFactory Init")
        ImageCacheFactory.newDisk(appContext)
    }

    // image cache in memory for coil
    val memoryCache: MemoryCache by lazy {
        Log.d("AppModules", "MemoryCache Init")
        ImageCacheFactory.newMemory(appContext)
    }

    // thumbnail disk cache for profile pictures
    val thumbnailDiskCache: ThumbnailDiskCache by lazy {
        Log.d("AppModules", "ThumbnailDiskCache Init")
        // One-shot reclaim of the v1 cache dir, which held squashed thumbnails.
        appContext.safeCacheDir().resolve("profile_thumbnails").deleteRecursively()
        ThumbnailDiskCache(appContext.safeCacheDir().resolve("profile_thumbnails_v2"))
    }

    // crash report storage
    val crashReportCache = CrashReportCache(appContext)

    // cache for NIP-11 documents
    val nip11Cache: Nip11CachedRetriever by lazy {
        Log.d("AppModules", "Nip11CachedRetriever Init")
        Nip11CachedRetriever(torEvaluatorFlow::okHttpClientForRelay)
    }

    fun setImageLoader() {
        Log.d("AppModules", "ImageLoaderSetup Init")
        ImageLoaderSetup.setup(
            app = appContext,
            diskCache = { diskCache },
            memoryCache = { memoryCache },
            blossomServerResolver = { blossomResolver },
            callFactory = { okHttpClients.getHttpClient(roleBasedHttpClientBuilder.shouldUseTorForImageDownload(it)) },
            thumbnailCache = thumbnailDiskCache,
            backgroundScope = applicationIOScope,
        )
    }

    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(appContext, npub)

    fun initiate(appContext: Context) {
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedCrashSaver(crashReportCache, applicationIOScope))

        // Restore the persisted DNS cache before any networking starts. Lookups that fire
        // before this completes fall through to the sync resolver path (existing behavior);
        // once restored, every previously-seen host hits the stale-while-revalidate path
        // instead of blocking on getaddrinfo.
        applicationIOScope.launch {
            dnsStore.load()
        }

        // Periodically flush the DNS cache. Saves are skipped when nothing has changed.
        applicationIOScope.launch {
            while (true) {
                delay(5 * 60 * 1000L)
                dnsStore.save()
            }
        }

        applicationIOScope.launch {
            // loads main account quickly.
            LocalPreferences.loadAccountConfigFromEncryptedStorage()
            sessionManager.loginWithDefaultAccountIfLoggedOff()
        }

        // forces initialization of uiPrefs in the main thread to avoid blinking themes
        uiPrefs

        // initializes diskcache on an IO thread.
        applicationIOScope.launch {
            // Sets Coil - Tor - OkHttp link
            setImageLoader()
        }

        // initializes diskcache on an IO thread.
        applicationIOScope.launch {
            // Sets Coil - Tor - OkHttp link
            uiState
        }

        // LRUCache should not be instanciated in the Main thread due to blocking
        applicationIOScope.launch {
            CachedRobohash
            resourceCacheInit()
        }

        // registers to receive events
        pokeyReceiver.register(appContext)

        // starts observing LocalCache for notification-worthy events
        notificationDispatcher.start()

        // Watch for account login and start/stop always-on notification service
        applicationIOScope.launch {
            sessionManager.accountContent.collectLatest { state ->
                if (state is AccountState.LoggedIn) {
                    alwaysOnNotificationServiceManager.watchAccount(state.account)
                } else {
                    alwaysOnNotificationServiceManager.stop()
                }
            }
        }

        // Warms the video cache off the main thread. SimpleCache's constructor opens a SQLite
        // index over StandaloneDatabaseProvider and walks every cached span on disk — up to a
        // few hundred ms on a populated 4 GB cache — so leaving it for the first session's
        // onGetSession would do that work on the main thread. The short delay keeps the IO
        // dispatcher free for the urgent first-paint work above (account load, image loader,
        // ui state, robohash) while still landing the warmup well before a typical user can
        // scroll to and tap a video. The previous 10 s delay was long enough that a fast user
        // (or a deep link) could lose the lazy { } race and trigger main-thread init.
        applicationIOScope.launch {
            delay(1_500)
            videoCache
        }
    }

    fun terminate(appContext: Context) {
        pokeyReceiver.unregister(appContext)
        notificationDispatcher.stop()
        BackgroundMedia.removeBackgroundControllerAndReleaseIt()
        PlaybackServiceClient.shutdown()
        alwaysOnNotificationServiceManager.stop()
        // Best-effort flush before the scope is cancelled. Android rarely calls onTerminate in
        // production, but when it does we get one last chance to persist the cache.
        runCatching { dnsStore.save() }
        applicationIOScope.cancel("Application onTerminate $appContext")
        accountsCache.clear()
    }

    fun trim() {
        applicationIOScope.launch {
            // Backgrounding is a natural moment to flush the DNS cache.
            dnsStore.save()
            val loggedIn = accountsCache.accounts.value.values
            trimmingService.run(loggedIn, LocalPreferences.allSavedAccounts())
        }
    }
}
