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

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlEndpointResolver
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.amethyst.model.nip03Timestamp.BitcoinExplorerEndpoint
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
import com.vitorpamplona.amethyst.napplet.DataStoreNappletPermissionStore
import com.vitorpamplona.amethyst.napplet.DataStoreNostrSignerPermissionStore
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.service.cast.CastRegistry
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
import com.vitorpamplona.amethyst.service.okhttp.OnionLocationCache
import com.vitorpamplona.amethyst.service.okhttp.SurgeDns
import com.vitorpamplona.amethyst.service.okhttp.SurgeDnsStore
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.service.relayClient.TorCircuitHealthTracker
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.DataStoreRelayAuthPermissionStore
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.speedLogger.RelaySpeedLogger
import com.vitorpamplona.amethyst.service.safeCacheDir
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostWorker
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.LocalBlossomCacheProbe
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.resourceCacheInit
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.amethyst.ui.tor.TorService
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayLogger
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.stats.RelayReqStats
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.CompositeNamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxNameBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TOR_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraBackend
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    private val _trimLevelEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val trimLevelEvents = _trimLevelEvents.asSharedFlow()

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

    val torManager = TorManager(torPrefs, TorService(appContext), applicationIOScope)

    // Network identity change (wifi↔cellular, regained from offline, captive portal
    // cleared) — the old network's guards/circuits are dead, and Arti's in-memory
    // client + on-disk state/ both need a fresh start. onNetworkChange drops the
    // TorClient, clears the bypass + persisted approval, and triggers a full re-init.
    init {
        applicationIOScope.launch {
            connManager.status
                .map { (it as? ConnectivityStatus.Active)?.networkId }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { torManager.onNetworkChange() }
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
    // path on first lookup. Stored in cacheDir — pure perf data, OK if the OS evicts it.
    val dnsStore = SurgeDnsStore(File(appContext.safeCacheDir(), SurgeDnsStore.FILE_NAME), surgeDns)

    // Shared cache populated by OnionLocationInterceptor from any HTTP/WebSocket
    // response carrying an Onion-Location header. Consulted by OnionUrlRewriteInterceptor
    // on Tor-enabled clients to transparently redirect to .onion addresses.
    val onionLocationCache = OnionLocationCache()

    // manages all the other connections separately from relays.
    val okHttpClients: DualHttpClientManager =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = connManager.isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
            dns = surgeDns,
            // Transparently rewrites sha256-keyed HTTP requests to the local
            // Blossom cache when the master toggle is on, the profile-pictures-only
            // restriction is off, and the probe sees 127.0.0.1:24242 as available.
            shouldBridgeBlossomCache = {
                val settings = sessionManager.loggedInAccount()?.settings
                val master = settings?.useLocalBlossomCache?.value ?: false
                val profileOnly = settings?.localBlossomCacheProfilePicturesOnly?.value ?: false
                master && !profileOnly && localBlossomCacheProbe.available.value
            },
            onionCache = onionLocationCache,
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

    /**
     * Long-lived Namecoin Core JSON-RPC client. The current
     * [NamecoinCoreRpcConfig] is pushed in via [setConfig] each time the
     * user saves settings; this avoids reading SharedPreferences on the
     * hot lookup path.
     */
    val namecoinCoreRpcClient by lazy {
        Log.d("AppModules", "NamecoinCoreRpcClient Init")
        val client =
            NamecoinCoreRpcClient(
                httpClientForUrl = roleBasedHttpClientBuilder::okHttpClientForNip05,
            )
        // Bootstrap the active config and the pinned trust store from the
        // same shared SharedPreferences entry the ElectrumX client uses.
        // Mirrors the ElectrumXClient init path above so user-pinned certs
        // are available on both backends after process restart.
        applicationIOScope.launch {
            try {
                client.setConfig(namecoinPrefs.current.namecoinCoreRpc)
                val pinnedCerts = namecoinPrefs.loadPinnedCerts()
                if (pinnedCerts.isNotEmpty()) {
                    client.setDynamicCerts(pinnedCerts)
                }
            } catch (_: Exception) {
                // Non-fatal — user can re-pin via Settings.
            }
        }
        client
    }

    /**
     * Compose the active Namecoin lookup backend based on user settings.
     *
     * The returned [IElectrumXClient] is what the resolver actually calls.
     * It dispatches to either Namecoin Core RPC or ElectrumX (custom-only,
     * default-only, or both) and applies the user's fallback policy.
     *
     * The function builds a fresh composite per call so that settings
     * changes take effect immediately (no app restart required).
     */
    fun buildNamecoinBackend(): IElectrumXClient {
        val settings = namecoinPrefs.current
        val custom = settings.toElectrumxServers()
        val defaults =
            if (roleBasedHttpClientBuilder.shouldUseTorForNIP05("https://electrumx.example.com")) {
                TOR_ELECTRUMX_SERVERS
            } else {
                DEFAULT_ELECTRUMX_SERVERS
            }

        val customExBackend =
            custom?.let { servers -> ElectrumxNameBackend(electrumXClient) { servers } }
        val defaultExBackend = ElectrumxNameBackend(electrumXClient) { defaults }

        return when (settings.backend) {
            NamecoinBackend.NAMECOIN_CORE_RPC -> {
                // Refresh client config in case the user just saved it.
                namecoinCoreRpcClient.setConfig(settings.namecoinCoreRpc)
                CompositeNamecoinBackend(
                    primary = namecoinCoreRpcClient,
                    customElectrumx = customExBackend,
                    defaultElectrumx = defaultExBackend,
                    policy = settings.toFallbackPolicy(),
                    isPrimaryCoreRpc = true,
                )
            }

            NamecoinBackend.ELECTRUMX -> {
                // Custom servers first (if any). If the user only has the public
                // defaults configured, primary == defaultElectrumx and the
                // fallback toggle is moot.
                val primary: com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameBackend =
                    customExBackend ?: defaultExBackend
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = null,
                    defaultElectrumx = if (customExBackend != null) defaultExBackend else null,
                    policy = settings.toFallbackPolicy(),
                    isPrimaryCoreRpc = false,
                )
            }
        }
    }

    val namecoinResolver by
        lazy {
            Log.d("AppModules", "Namecoin Resolver Init")
            NamecoinNameResolver(
                electrumxClient =
                    object : IElectrumXClient {
                        override suspend fun nameShowWithFallback(
                            identifier: String,
                            servers: List<ElectrumxServer>,
                        ): NameShowResult? = buildNamecoinBackend().nameShowWithFallback(identifier, servers)
                    },
                serverListProvider = {
                    // Kept for compatibility with NamecoinNameResolver's API;
                    // the composite backend ignores this and consults user
                    // settings directly via buildNamecoinBackend().
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
            onionCache = onionLocationCache,
        )

    // Connects the INostrClient class with okHttp
    val websocketBuilder =
        OkHttpWebSocket.Builder(
            httpClient = { url ->
                val useTor = torEvaluatorFlow.shouldUseTorForRelay(url)
                okHttpClientForRelays.getHttpClient(useTor)
            },
            // Don't dial Tor-routed relays until Tor's SOCKS port is up. Otherwise the
            // whole Tor-routed relay set is hammered with doomed dials against the dead
            // proxy during bootstrap. RelayProxyClientConnector reconnects them (with
            // ignoreRetryDelays=true) the instant Tor flips to Active.
            canDial = { url ->
                !torEvaluatorFlow.shouldUseTorForRelay(url) || torManager.isSocksReady()
            },
        )

    // Caches all events in Memory
    val cache: LocalCache = LocalCache

    // NIP-BC onchain zap verification backend. Wired up once at app init so
    // LocalCache.consume(OnchainZapEvent) can sum the on-chain output values
    // that pay the recipient's derived Taproot address. Wrapped in a caching
    // decorator so a feed full of onchain zaps doesn't fan out into one HTTP
    // request per event.
    //
    // The explorer endpoint is shared with OpenTimestamps: it honours the same
    // user-configured server (OTS settings) and the same Tor-aware default
    // selection, via BitcoinExplorerEndpoint — onchain zaps must not silently
    // bypass the user's Tor preference.
    init {
        cache.onchainBackend =
            CachingOnchainBackend(
                EsploraBackend(
                    baseUrl = {
                        BitcoinExplorerEndpoint.resolveNormalized(
                            customExplorerUrl = otsPrefs.current.normalizedUrl(),
                            usingTor =
                                roleBasedHttpClientBuilder.shouldUseTorForMoneyOperations(
                                    OkHttpBitcoinExplorer.MEMPOOL_API_URL,
                                ),
                        )
                    },
                    client = roleBasedHttpClientBuilder.okHttpClientForMoney(OkHttpBitcoinExplorer.MEMPOOL_API_URL),
                ),
            )

        // NIP-57 Appendix F: validates incoming zap receipts against the
        // recipient's LNURL provider's advertised `nostrPubkey`. Reuses the
        // money-tier http client so Tor preferences and proxy settings apply.
        cache.lnurlEndpointResolver =
            OkHttpLnurlEndpointResolver(roleBasedHttpClientBuilder::okHttpClientForMoney)
    }

    // Provides a relay pool. The caching decoder skips re-parsing EVENT frames
    // that arrive again via another subscription or relay (14-57% of frames in
    // production measurements).
    //
    // Wrapped in BlockedRelayFilteringClient so the active account's NIP-51
    // kind:10006 blocked relay list is enforced centrally on every REQ, COUNT
    // and publish (relay targeting is otherwise distributed across dozens of
    // feed/loader/finder/broadcast sites, most of which don't subtract it).
    // The blocked set is read per-call from the logged-in account.
    val client: INostrClient =
        BlockedRelayFilteringClient(
            NostrClient(websocketBuilder, applicationIOScope, CachingEventDecoder()),
            blockedRelays = {
                sessionManager
                    .loggedInAccount()
                    ?.blockedRelayList
                    ?.flow
                    ?.value ?: emptySet()
            },
        )

    // Self-heals the "Tor Active but every circuit dead" state the lifecycle watchdogs can't
    // see (they only arm while Connecting). Watches Tor-routed relay outcomes and, when enough
    // fail with zero successes in the window, pokes TorManager to drop + re-init Arti.
    val torCircuitHealthTracker =
        TorCircuitHealthTracker(
            client = client,
            isTorRouted = { torEvaluatorFlow.shouldUseTorForRelay(it) },
            isTorActive = { torManager.isSocksReady() },
            isConnectivityActive = { connManager.status.value is ConnectivityStatus.Active },
            onCircuitsDead = { torManager.onTorCircuitsDead() },
        ).also { it.register() }

    // Watches for changes on Tor and Relay List Settings
    val relayProxyClientConnector =
        RelayProxyClientConnector(
            torEvaluatorFlow.flow,
            okHttpClientForRelays.defaultHttpClient,
            okHttpClientForRelays.defaultHttpClientWithoutProxy,
            connManager.status,
            torManager.status,
            client,
            applicationIOScope,
        )

    // Verifies and inserts in the cache from all relays, all subscriptions
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Show messages from the Relay and controls their dismissal
    val notifyCoordinator = NotifyCoordinator(client)

    // Persists per-relay NIP-42 ALLOW/DENY overrides across app restarts.
    val relayAuthPermissionStore by lazy {
        DataStoreRelayAuthPermissionStore(appContext)
    }

    // Singleton stores for napplet permissions — DataStore v1 enforces one instance per file.
    val nappletPermissionStore by lazy { DataStoreNappletPermissionStore(appContext) }
    val signerPermissionStore by lazy { DataStoreNostrSignerPermissionStore(appContext) }

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

    // Focused timeline for the DM / gift-wrap loading path (tag: DMPagination).
    // val dmDiagnostics = if (isDebug) DmRelayDiagnosticsLogger(client) else null

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
            cashuWalletFilterAssembler = { sources.cashuWallet },
            cashuMintDirectoryFilterAssembler = { sources.cashuMintDirectory },
            okHttpClientForMoney = roleBasedHttpClientBuilder::okHttpClientForMoney,
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

    val localBlossomCacheProbe by lazy {
        LocalBlossomCacheProbe(roleBasedHttpClientBuilder)
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
            useLocalBlossomCache = {
                sessionManager
                    .loggedInAccount()
                    ?.settings
                    ?.useLocalBlossomCache
                    ?.value ?: false
            },
            localCacheProbe = localBlossomCacheProbe,
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

    // Local store for posts the user has scheduled to publish later. Backed by a
    // single JSON file under the app's private filesDir; read by ScheduledPostWorker.
    val scheduledPostStore =
        ScheduledPostStore(File(appContext.filesDir, ScheduledPostStore.FILE_NAME))

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

    // LAN cast registry — Chromecast only (play flavor real, fdroid no-op
    // stub). Discovery starts only when the picker dialog opens; idle by
    // default to keep multicast traffic off.
    val castRegistry: CastRegistry by lazy {
        Log.d("AppModules", "CastRegistry Init")
        CastRegistry(appContext)
    }

    // local video cache with disk + memory
    val videoCache: VideoCache by lazy {
        Log.d("AppModules", "VideoCache Init")
        VideoCacheFactory.new(appContext)
    }

    // image cache in disk for coil
    val diskCache: DiskCache by lazy {
        Log.d("AppModules", "ImageCacheFactory Init")
        ImageCacheFactory.newDisk(appContext, applicationIOScope)
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

        // One-time hygiene: remove per-account directories (MLS/Marmot stores) left behind
        // by accounts that are no longer saved — account deletion historically didn't clean
        // them up, so they leaked disk across every add/remove.
        applicationIOScope.launch {
            val keep =
                LocalPreferences
                    .allSavedAccounts()
                    .mapNotNull { decodePublicKeyAsHexOrNull(it.npub) }
                    .toSet()
            accountsCache.pruneOrphanAccountDirs(keep)
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

        // Initialize napplet permission stores on an IO thread to avoid StrictMode violations
        // when ConnectedAppsScreen first accesses them on the main thread.
        applicationIOScope.launch {
            nappletPermissionStore
            signerPermissionStore
        }

        // registers to receive events
        pokeyReceiver.register(appContext)

        // starts observing LocalCache for notification-worthy events
        notificationDispatcher.start()

        // Schedule the scheduled-posts worker (periodic + one-time catch-up).
        // Runs independently of the always-on notification setting so scheduled
        // posts still fire when always-on notifications are disabled.
        ScheduledPostWorker.schedule(appContext)
        ScheduledPostWorker.scheduleCatchUp(appContext)

        // Periodic scan that posts "starting soon" notifications for NIP-52 appointments the
        // user has RSVP'd to as ACCEPTED. 15-minute cadence matches both the WorkManager
        // periodic minimum and the lead-time window.
        com.vitorpamplona.amethyst.service.calendar.CalendarReminderWorker
            .schedule(appContext)

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

        // Evict the BlossomServerResolver URL cache whenever either local-cache
        // toggle flips or the probe transitions up/down so stale entries don't
        // outlive the underlying decision.
        applicationIOScope.launch {
            sessionManager.accountContent.collectLatest { state ->
                if (state is AccountState.LoggedIn) {
                    merge(
                        state.account.settings.useLocalBlossomCache
                            .drop(1),
                        state.account.settings.localBlossomCacheProfilePicturesOnly
                            .drop(1),
                    ).collect {
                        blossomResolver.uriToUrlCache.evictAll()
                        blossomResolver.blossomHitCache.cache.evictAll()
                        localBlossomCacheProbe.invalidate()
                    }
                }
            }
        }
        applicationIOScope.launch {
            localBlossomCacheProbe.available.drop(1).collect {
                blossomResolver.uriToUrlCache.evictAll()
                blossomResolver.blossomHitCache.cache.evictAll()
            }
        }
        // Warm the local-cache probe so the very first image load doesn't pay
        // the loopback round-trip cost.
        applicationIOScope.launch {
            localBlossomCacheProbe.isAvailable()
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

    fun trim(level: Int) {
        _trimLevelEvents.tryEmit(level)
        applicationIOScope.launch {
            // Backgrounding is a natural moment to flush the DNS cache.
            dnsStore.save()
            val loggedIn = accountsCache.accounts.value.values
            trimmingService.run(loggedIn, LocalPreferences.allSavedAccounts(), level)
            // Trim in-process caches proportional to OS memory pressure.
            //
            // Since API 34 the OS only ever delivers two trim levels (the foreground
            // RUNNING_* levels and the deeper MODERATE/COMPLETE background tiers were
            // deprecated because apps are no longer notified of them):
            //   BACKGROUND(40) — process is on the system LRU list: real reclaim
            //                    pressure, and the strongest signal we still get.
            //   UI_HIDDEN (20) — just backgrounded, no pressure yet. Fires on EVERY
            //                    app switch.
            //
            // So we key off exactly those two. UI_HIDDEN is frequent, so it only trims
            // images (bitmaps are the largest allocations) and keeps the CPU-heavy
            // caches (Robohash SVG assembly, rich-text parsing) warm — clearing them
            // would force a full rebuild on every resume and cause visible jank.
            // BACKGROUND trims hard but keeps a small working set: it means "on the LRU
            // list" (real reclaim pressure), not the imminent kill that COMPLETE used to
            // signal — so leave just enough warm to redraw the screen the user left on.
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                    // On the LRU list under real pressure: trim hard, but keep a small
                    // working set so a returning user doesn't rebuild the visible screen
                    // from scratch. memoryCache is byte-sized (Coil), the rest are entry counts.
                    memoryCache.trimToSize(memoryCache.maxSize / 10)
                    CachedRichTextParser.trimToSize(10)
                    CachedRobohash.trimToSize(20)
                    nip11Cache.trimToSize(10)
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    // Just backgrounded, no pressure yet: trim images but keep the
                    // parsed-text and avatar caches warm so resuming is instant.
                    memoryCache.trimToSize(memoryCache.maxSize / 2)
                }
            }
        }
    }
}
