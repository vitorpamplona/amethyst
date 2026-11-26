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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.account.AccountSessionHooks
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.EncryptedFileAccountStores
import com.vitorpamplona.amethyst.commons.account.StoreAccountSessionStore
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.cashu.DataStoreCashuCounterStore
import com.vitorpamplona.amethyst.commons.connectedApps.DataStoreNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.DataStoreNip46ClientStore
import com.vitorpamplona.amethyst.commons.cordn.SecretEncryptionCordnBlobCipher
import com.vitorpamplona.amethyst.commons.domain.nip46.RemoteSignerAuthPrompts
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.keystorage.KeyStoragePrompts
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorageVault
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.dock.DockLayoutPreferences
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.BitcoinExplorerEndpoint
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.PaneWidthPreferences
import com.vitorpamplona.amethyst.commons.model.preferences.TorSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.UiSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.DataStoreNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.RelayRouteLogger
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.relayFrameDecoder
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.relays.health.TorCircuitHealthTracker
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.service.connectivity.IosConnectivity
import com.vitorpamplona.amethyst.commons.service.connectivity.networkIdentityChanges
import com.vitorpamplona.amethyst.commons.service.http.ConnectException
import com.vitorpamplona.amethyst.commons.service.http.DualKtorClients
import com.vitorpamplona.amethyst.commons.service.http.EOFException
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.KtorEsploraTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorLnurlTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorMintTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorNip05Fetcher
import com.vitorpamplona.amethyst.commons.service.http.KtorWebSocket
import com.vitorpamplona.amethyst.commons.service.http.LoopbackProbe
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedKtorClients
import com.vitorpamplona.amethyst.commons.service.http.SSLHandshakeException
import com.vitorpamplona.amethyst.commons.service.http.SocketException
import com.vitorpamplona.amethyst.commons.service.http.SocketTimeoutException
import com.vitorpamplona.amethyst.commons.service.http.UnknownHostException
import com.vitorpamplona.amethyst.commons.service.http.darwinHttpClient
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.commons.service.upload.KtorBlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.AccountsTorStateConnector
import com.vitorpamplona.amethyst.commons.tor.TorManager
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.amethyst.commons.tor.TorService
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.socksReplyMessage
import com.vitorpamplona.amethyst.commons.tor.supportedOn
import com.vitorpamplona.amethyst.commons.ui.components.animated.AnimatedImageFetcher
import com.vitorpamplona.amethyst.ios.napplet.IosNappletHost
import com.vitorpamplona.amethyst.ios.napplet.IosNappletStores
import com.vitorpamplona.amethyst.ios.playback.IosPlaybackPlatform
import com.vitorpamplona.amethyst.ios.playback.NowPlaying
import com.vitorpamplona.amethyst.ios.playback.cache.IosVideoCache
import com.vitorpamplona.amethyst.ios.tor.IosArti
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip03Timestamp.BitcoinExplorerUrls
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.RemoteCalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraClient
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSOSStatusErrorDomain
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURLErrorBadServerResponse
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSUserDomainMask
import platform.posix.ECONNREFUSED
import platform.posix.ECONNRESET
import platform.posix.ENOTCONN
import platform.posix.EPIPE
import platform.posix.EPROTO
import platform.posix.ETIMEDOUT
import kotlin.coroutines.cancellation.CancellationException

/**
 * The iOS service graph: the same shared classes Android's `AppModules` and the desktop's
 * `DesktopAppModules` wire, with the iOS transports (Ktor on NSURLSession, the Keychain).
 *
 * Walking-skeleton scope (plans/2026-10-09-ios-app.md, B0): enough for a read-only `npub` login and
 * the feeds. What is not wired yet throws or stays off, and each such spot names the plan step that
 * fills it. This class is meant to shrink, not grow: A3b moves the wiring all three front ends
 * share into `commons`, and this file then keeps only what is iOS's own.
 */
class IosAppModules(
    val appVersion: String,
) {
    val appAgent = "Amethyst-iOS/$appVersion"

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("IosAppModules", "Uncaught exception in the app scope", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    /** Application Support: kept across launches and in backups, hidden from the Files app. */
    val filesPath: Path =
        (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String).toPath() / "amethyst"

    val appStores = AppPreferenceStores(rootFilesDir = { filesPath })

    private val sharedSettingsStore get() = appStores.sharedSettings()

    /** The UI preferences, read before the first frame so the theme and language do not blink. */
    val uiPrefs: UiSettingsFlow =
        UiSettingsFlow.build(runBlocking { UiSettingsStore(sharedSettingsStore).load() } ?: UiSettingsFlow().toSettings())

    // Tor settings, read before anything connects (as Android's blocking load does), so nothing
    // dials before the routing is known. A fresh install gets TorSettings()'s defaults: the built-in
    // Tor, with onion, DM and new relays through it. A build without Arti (Kotlin compiled off a
    // Mac, never an app) keeps Tor off: there is no engine to route through. "External" (Orbot's
    // SOCKS port) does not exist here, since Orbot on iOS is a VPN, so a stored one runs the built-in
    // Tor instead of nothing (TorType.supportedOn says why not OFF); IosAppPlatform hides the choice.
    val torPrefs: TorSettingsStore =
        TorSettingsStore(
            (runBlocking { TorSettingsStore.torPreferences(sharedSettingsStore) } ?: TorSettings())
                .supportedOn(hasInternalTor = IosArti.AVAILABLE, hasExternalTor = false),
            sharedSettingsStore,
            applicationIOScope,
        )

    // The built-in Tor: the shared lifecycle (TorService, TorManager) over Arti's C ABI, the same
    // engine and watchdogs Android runs (plans/2026-11-25-ios-tor.md, B12d). Arti keeps its state in
    // Application Support/amethyst/arti. TorManager starts it while its status is collected: the
    // relay pool's Tor route and the drawer's Tor icon do (IosAppRoot.AppEffects). The listener probe
    // is what lets a return from suspension find a reclaimed SOCKS listener (appLifecycle below).
    // Arti's directory is kept out of backups (ArtiCinteropBridge.initialize).
    private val torService =
        TorService(
            engine = IosArti.bridge,
            dataDir = filesPath / "arti",
            fileSystem = FileSystem.SYSTEM,
            scope = applicationIOScope,
            listenerProbe = { port -> LoopbackProbe.isSocks5Listening(port) },
        )
    val torManager = TorManager(torPrefs, torService, applicationIOScope)

    // The default network, from NWPathMonitor: what Android reads from ConnectivityManager. Its
    // metered flag drives the "on mobile data" media choices, and a move to another network
    // re-initializes Tor, as on Android (the old network's guards and circuits are dead).
    val connectivity = IosConnectivity(applicationIOScope)

    init {
        applicationIOScope.launch {
            connectivity.status
                .networkIdentityChanges()
                .collect {
                    Log.i("IosAppModules") { "Network changed (id $it): re-initializing Tor" }
                    torManager.onNetworkChange()
                }
        }
    }

    // Which relays go through Tor: the user's settings plus each account's relay lists
    // (accountsTorStateConnector below), as on Android.
    val torRelayState = TorRelayState(torPrefs.value, applicationIOScope)

    val namecoinPrefs by lazy { NamecoinSettingsStore(sharedSettingsStore, applicationIOScope) }
    val otsPrefs by lazy { OtsSettingsStore(sharedSettingsStore, runBlocking { OtsSettingsStore.load(sharedSettingsStore) }) }
    val drawerSectionCollapsePrefs = DrawerSectionCollapsePreferences(sharedSettingsStore, applicationIOScope)
    val paneWidthPrefs = PaneWidthPreferences(sharedSettingsStore, applicationIOScope)
    val dockLayoutPrefs = DockLayoutPreferences(sharedSettingsStore, applicationIOScope)

    val uiState = UiSettingsState(uiPrefs, connectivity.isMobileOrFalse, applicationIOScope)

    val keyCache = EncryptionKeyCache()

    /**
     * Every HTTP and WebSocket connection the app makes, on NSURLSession: a direct client, and a
     * client through Tor's SOCKS port that is rebuilt when the port moves. A Tor-routed request
     * while Tor has no port fails instead of going direct (DualKtorClients). Nothing gets a client
     * except through a role below or the relay builder, so no path can skip the Tor decision.
     */
    val httpClients =
        DualKtorClients(
            proxyPort = torManager.activePortOrNull,
            sessionBypass = torManager.sessionBypass,
            scope = applicationIOScope,
            build = ::darwinHttpClient,
            listenerGeneration = torManager.listenerRestarts,
        )

    /** Each role's client (images, video, previews, money, NIP-05, uploads), by the shared Tor table. */
    val roleBasedHttpClientBuilder = RoleBasedKtorClients(httpClients, torPrefs.value)

    // Apple's socket offers permessage-deflate and drops the connection with EPROTO when a relay
    // breaks the extension's rules (nostr.mom sets RSV1 on continuation frames). Such a relay is
    // dialed again without compression.
    val websocketBuilder =
        KtorWebSocket.Builder(
            httpClient = { url -> httpClients.get(torRelayState.shouldUseTorForRelay(url)) },
            scope = applicationIOScope,
            compression = KtorWebSocket.Compression(isFailure = ::isDarwinProtocolError),
            // Don't dial Tor-routed relays until Tor can carry them, as Android's builder does:
            // a bootstrap would otherwise meet the whole Tor-routed set with doomed dials.
            // RelayProxyClientConnector dials them the moment Tor is up. A session bypass ("use a
            // regular connection") sends them direct, so they may dial then.
            // NSURLSession reports a Tor refusal as -1000 "bad URL"; Arti's log has the SOCKS reply,
            // which becomes the SocketException Java's SOCKS client gives OkHttp on Android, so a
            // dead domain is parked for minutes there and here alike.
            // A relay that answered the upgrade with HTTP (403, 502, a Cloudflare page) is parked
            // for minutes on Android, where OkHttp hands the relay client its status; NSURLSession
            // only says the handshake failed (-1011), so it is reported with status 0 ("refused,
            // status unknown") to get the same treatment.
            refusedStatus = { e -> if (isDarwinWebSocketHandshakeRefusal(e)) UNKNOWN_REFUSAL_STATUS else null },
            // NSURLSession's errors are renamed to what OkHttp raises for the same event (TLS
            // alert -> SSLHandshakeException, a hang-up -> EOFException), so the backoff and the
            // diagnostics treat a relay the same on both.
            explainFailure = { url, e ->
                val torReason =
                    if (torRelayState.shouldUseTorForRelay(url)) {
                        val address = Url(url.url)
                        torService.connectFailures.explain(address.host, address.port)?.let { SocketException(socksReplyMessage(it.socksReply)) }
                    } else {
                        null
                    }
                torReason ?: jvmNamedFailure(url, e)
            },
            canDial = { url ->
                !torRelayState.shouldUseTorForRelay(url) || torManager.isTorReady() || torManager.sessionBypass.value
            },
        )

    /** Blossom uploads over the upload role; re-encoded pictures wait in the temp folder until they go up. */
    val mediaUploader by lazy {
        IosMediaUploader(
            blossom = KtorBlossomClient(roleBasedHttpClientBuilder::ktorClientForUploads),
            tempDir = (NSTemporaryDirectory().toPath() / "uploads"),
        )
    }

    /** A file's bytes over [client], or null when it can't be fetched. */
    private suspend fun downloadBytes(
        url: String,
        client: HttpClient,
    ): ByteArray? =
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) response.bodyAsBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("IosAppModules", "Download of $url failed", e)
            null
        }

    /** An image's bytes over the image role: encrypted pictures, animated images, artwork. */
    suspend fun downloadImageBytes(url: String): ByteArray? = downloadBytes(url, roleBasedHttpClientBuilder.ktorClientForImage(url))

    /** A video's or its captions' bytes over the video role: encrypted and saved videos, captions. */
    suspend fun downloadVideoBytes(url: String): ByteArray? = downloadBytes(url, roleBasedHttpClientBuilder.ktorClientForVideo(url))

    /** The shared player's iOS half. */
    val playbackPlatform by lazy {
        NowPlaying.artworkLoader = ::downloadImageBytes
        IosVideoCache.install(
            httpClient = roleBasedHttpClientBuilder::ktorClientForVideo,
            requiresTor = roleBasedHttpClientBuilder::shouldUseTorForVideo,
            torStreamClient = roleBasedHttpClientBuilder::ktorClientForTorVideoStream,
        )
        IosPlaybackPlatform(download = ::downloadVideoBytes, keyCache = keyCache)
    }

    val nip05Client by lazy { Nip05Client(fetcher = KtorNip05Fetcher(roleBasedHttpClientBuilder::ktorClientForNip05)) }

    val lnurlTransport = KtorLnurlTransport(roleBasedHttpClientBuilder::ktorClientForMoney)

    // Images load over the image role, each URL's client picked per request. The Caches folder is
    // the one iOS may purge.
    init {
        IosImageLoaderSetup.setup(
            httpClient = roleBasedHttpClientBuilder::ktorClientForImage,
            keyCache = keyCache,
            download = ::downloadImageBytes,
            cacheDir = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String).toPath() / "images",
        )
        // GIFs and animated WebPs play through the shared Skia player, which fetches them itself.
        AnimatedImageFetcher.installed = IosAnimatedImageFetcher(keyCache, ::downloadImageBytes)
    }

    val cache: LocalCache = LocalCache

    // The on-chain wallet and on-chain zaps read the chain through the explorer OpenTimestamps uses
    // (OTS settings), as on Android and the desktop: mempool.space when money goes through Tor,
    // blockstream.info when it does not, over the money role.
    init {
        cache.onchainBackend =
            CachingOnchainBackend(
                EsploraClient(
                    baseUrl = {
                        BitcoinExplorerEndpoint.resolveNormalized(
                            customExplorerUrl = otsPrefs.current.normalizedUrl(),
                            usingTor = roleBasedHttpClientBuilder.shouldUseTorForMoneyOperations(BitcoinExplorerUrls.MEMPOOL),
                        )
                    },
                    transport = KtorEsploraTransport(roleBasedHttpClientBuilder::ktorClientForMoney),
                ),
            )
    }

    // The relay pool. The active account's blocked relays (kind 10006) are subtracted here, once.
    val client: INostrClient =
        BlockedRelayFilteringClient(
            NostrClient(websocketBuilder, applicationIOScope, relayFrameDecoder()),
            blockedRelays = {
                sessionManager
                    .loggedInAccount()
                    ?.blockedRelayList
                    ?.flow
                    ?.value ?: emptySet()
            },
        )

    // Feeds every event the relays send into the cache, as on Android and the desktop.
    val cacheClientConnector = CacheClientConnector(client, cache)

    // Self-heals the "Tor up but every circuit dead" state the lifecycle watchdogs can't see, as
    // on Android: enough Tor-routed relay failures with no success restart Arti.
    val torCircuitHealthTracker =
        TorCircuitHealthTracker(
            client = client,
            isTorRouted = { torRelayState.shouldUseTorForRelay(it) },
            isTorActive = { torManager.isTorReady() },
            isConnectivityActive = { connectivity.status.value is ConnectivityStatus.Active },
            onCircuitsDead = { torManager.onTorCircuitsDead() },
        ).also { it.register() }

    // Re-dials the relays whose route changed (Tor's port, a Tor setting, a relay moving between
    // classifications) and connects the pool while the app is on screen. Collected by
    // IosAppRoot.AppEffects, which is also what keeps Tor running.
    val relayProxyClientConnector =
        RelayProxyClientConnector(
            torEvaluator = torRelayState.flow,
            torConnection = httpClients.torRoute,
            clearConnection = httpClients.directRoute,
            connectivityStatus = connectivity.status,
            torBootstrapped = torManager.status.map { it.isFullyBootstrapped },
            client = client,
            scope = applicationIOScope,
        )

    // One DEBUG line per relay dial with its route (tor:<port>, direct, bypass) and its outcome, to
    // compare against Android's from the logs.
    val relayRouteLogger =
        RelayRouteLogger(client) { url ->
            when {
                !torRelayState.shouldUseTorForRelay(url) -> "direct"
                torManager.sessionBypass.value -> "bypass"
                else -> httpClients.currentProxyPort()?.let { "tor:$it" } ?: "tor (no port: refused)"
            }
        }.also { it.register() }

    /**
     * UIApplication's background and foreground, bridged into the shared Tor lifecycle. iOS
     * suspends a backgrounded app and may reclaim its listening sockets meanwhile, so on the way back
     * Tor's SOCKS listener and the media relay are checked and bound again if they died
     * (TorManager.onReturnToForeground). The relay pool redials what dropped on its own
     * (RedialRelaysOnReturn), and a network that changed meanwhile arrives through [connectivity].
     */
    val appLifecycle =
        IosAppLifecycle(
            onReturnToForeground = {
                torManager.onReturnToForeground()
                applicationIOScope.launch { IosVideoCache.recheckRelay() }
            },
        ).also { it.start() }

    val relayStats by lazy { RelayStats(client) }

    val authCoordinator = AuthCoordinator(client, applicationIOScope)

    val failureTracker = RelayOfflineTracker(client)

    val sources: RelaySubscriptionsCoordinator =
        RelaySubscriptionsCoordinator(cache, client, authCoordinator.receiver, failureTracker, applicationIOScope)

    val signerPermissionStore by lazy { DataStoreNostrSignerPermissionStore(appStores) }

    val nip46ClientStore by lazy { DataStoreNip46ClientStore(appStores.getDataStore(DataStoreNip46ClientStore.FILE_NAME)) }

    val favoriteApps by lazy { FavoriteAppsRegistry(appStores.getDataStore(FavoriteAppsRegistry.FILE_NAME), applicationIOScope).also { it.init() } }

    val browserHistory by lazy { BrowserHistoryRegistry(appStores.getDataStore(BrowserHistoryRegistry.FILE_NAME), applicationIOScope) }

    val browserIcons by lazy { BrowserIconRegistry({ filesPath / BrowserIconRegistry.DIR }, applicationIOScope) }

    val nappletPermissionLedger by lazy {
        val accountScope = { sessionManager.loggedInAccount()?.pubKey ?: "" }
        NappletPermissionLedger(DataStoreNappletPermissionStore(appStores.getDataStore("napplet_permissions"), accountScope), accountScope)
    }

    /** Napplets' and nSites' brokers, consent, network choices and blob cache. Built when one first opens. */
    private val nappletHostLazy = lazy { IosNappletHost(this) }
    val nappletHost by nappletHostLazy

    // Scheduled posts are hidden on iOS (B13): the store exists, empty, and nothing publishes from it.
    val scheduledPostStore = ScheduledPostStore(filesPath / "scheduled_posts.json")

    val powPublishQueue by lazy {
        PoWPublishQueue(scope = applicationIOScope, maxConcurrent = 1, minerThreads = platformMinerWorkers())
    }

    val blossomMirrorQueue by lazy {
        BlossomMirrorQueue(scope = applicationIOScope, clientFor = { throw UnsupportedOperationException("Blossom on iOS comes with A2") })
    }

    /** What key storage asks the user. */
    private val keyPrompts = KeyStoragePrompts()

    /** Private keys in the Keychain, on this device only. */
    private val keyStorage = SecureKeyStorage.create()

    private fun cashuCountersFor(npub: String) = DataStoreCashuCounterStore(appStores.getDataStore("cashu_$npub"))

    val sessionStore =
        StoreAccountSessionStore(
            rootFilesDir = { filesPath },
            appStores = appStores,
            keyVault = SecureKeyStorageVault(keyStorage),
            scope = applicationIOScope,
            cashuCounters = ::cashuCountersFor,
            keyPrompts = keyPrompts,
        )

    private val noLocation = MutableStateFlow<LocationResult>(LocationResult.LackPermission)

    val remoteSignerAuthPrompts = RemoteSignerAuthPrompts()

    val accountsCache =
        AccountCacheState(
            geolocationFlow = { noLocation },
            nwcFilterAssembler = { sources.nwc },
            cashuMintDirectoryFilterAssembler = { sources.cashuMintDirectory },
            cashuMintTransport = KtorMintTransport(roleBasedHttpClientBuilder::ktorClientForMoney),
            // NIP-55 signer apps are Android's.
            externalSignerFactory = { _, _ -> null },
            cordnBlobCipher = { SecretEncryptionCordnBlobCipher() },
            otsResolverBuilder = { OtsResolver(UnavailableOts, UnavailableOts) },
            cache = cache,
            client = client,
            appVersion = appVersion,
            encryptionKeyCache = keyCache,
            saveSettings = { sessionStore.saveAccountSettings(it) },
            marmotNotifier = { MarmotGroupNotifier.None },
            // Serving other apps as a NIP-46 bunker comes with B8.
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = { pubKey -> sessionStore.geohashIdentityStore(pubKey) },
            rootFilesDir = { filesPath },
            accountStores = EncryptedFileAccountStores,
            powQueue = { powPublishQueue },
            meterSigner = { it },
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
            remoteSignerMetadata = null,
            trustNetworkClientBuilder = { NostrClient(websocketBuilder) },
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
            marmotHistoryBlobs = null,
            canDownloadLargeFiles = { !connectivity.isMeteredOrMobileNow() },
            accountSettingsStore = sharedSettingsStore,
        )

    val sessionManager =
        AccountSessionManager(
            accountsCache = accountsCache,
            nip05ClientBuilder = { nip05Client },
            clientBuilder = { client },
            localPreferences = sessionStore,
            scope = applicationIOScope,
            // A NIP-46 login's relays follow the signer-relay Tor setting, as on Android.
            signerRelayRouting = torRelayState,
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
            hooks = SessionHooks(),
        )

    /**
     * A removed account's napplet and nSite storage goes with it: its screens close, then its data
     * store is deleted (also when no app opened this run, since the store is on disk).
     */
    private inner class SessionHooks : AccountSessionHooks {
        override suspend fun onAccountRemoved(pubkey: HexKey) {
            if (nappletHostLazy.isInitialized()) {
                nappletHost.removeAccount(pubkey)
            } else {
                withContext(Dispatchers.Main) { IosNappletStores.removeStore(pubkey) }
            }
        }
    }

    // As accounts load, feeds their DM, trusted, money and signer relays into torRelayState, which
    // re-evaluates which relays go through Tor and re-dials those that moved.
    val accountsTorStateConnector = AccountsTorStateConnector(accountsCache, torRelayState, applicationIOScope)
}

/** OpenTimestamps over Ktor comes with A2; verifying a timestamp fails with this message. */
private object UnavailableOts : BitcoinExplorer, RemoteCalendar {
    override suspend fun block(hash: String): BlockHeader = fail()

    override suspend fun blockHash(height: Int): String = fail()

    override suspend fun submit(
        url: String,
        digest: ByteArray,
    ): Timestamp = fail()

    override suspend fun getTimestamp(
        url: String,
        commitment: ByteArray,
    ): Timestamp = fail()

    private fun fail(): Nothing = throw UnsupportedOperationException("OpenTimestamps on iOS comes with A2")
}

/** NSURLSession's "the server answered the WebSocket upgrade with something other than 101". */
private fun isDarwinWebSocketHandshakeRefusal(e: Throwable): Boolean {
    val origin = (e as? DarwinHttpRequestException)?.origin ?: return false
    return origin.domain == NSURLErrorDomain &&
        origin.code == NSURLErrorBadServerResponse &&
        origin.userInfo.containsKey("_NSURLErrorWebSocketHandshakeFailureReasonKey")
}

/**
 * The exception OkHttp raises for the event behind an NSURLSession [e], or null to keep [e].
 * Matched on the error code, never the localized text.
 */
private fun jvmNamedFailure(
    url: NormalizedRelayUrl,
    e: Throwable,
): Throwable? {
    val origin = (e as? DarwinHttpRequestException)?.origin ?: return null
    val code = origin.code
    val host = Url(url.url).host
    return when (origin.domain) {
        NSURLErrorDomain ->
            when {
                code == NSURLErrorCannotFindHost || code == NSURLErrorDNSLookupFailed -> UnknownHostException("Unable to resolve host \"$host\"")
                code == NSURLErrorTimedOut -> SocketTimeoutException("timeout")
                code == NSURLErrorCannotConnectToHost -> ConnectException("Failed to connect to $host")
                code == NSURLErrorNetworkConnectionLost -> EOFException()
                // -1200 secure connection failed, -1201..-1206 certificate problems.
                code in NSURLErrorClientCertificateRequired..NSURLErrorSecureConnectionFailed -> SSLHandshakeException(origin.localizedDescription)
                else -> null
            }
        NSPOSIXErrorDomain ->
            when (code) {
                ENOTCONN.toLong(), EPIPE.toLong() -> EOFException()
                ECONNRESET.toLong() -> SocketException("Connection reset")
                ECONNREFUSED.toLong() -> ConnectException("Failed to connect to $host")
                ETIMEDOUT.toLong() -> SocketTimeoutException("timeout")
                else -> null
            }
        // Secure Transport's errSSL* codes (-9800 to -9899).
        NSOSStatusErrorDomain -> if (code in -9899L..-9800L) SSLHandshakeException(origin.localizedDescription) else null
        else -> null
    }
}

/** The HTTP status of a refused upgrade whose real status NSURLSession does not report. */
private const val UNKNOWN_REFUSAL_STATUS = 0

/** The error Apple's networking reports for a frame it cannot parse: POSIX EPROTO. */
private fun isDarwinProtocolError(e: Throwable): Boolean {
    val origin = (e as? DarwinHttpRequestException)?.origin ?: return false
    return origin.domain == NSPOSIXErrorDomain && origin.code == EPROTO.toLong()
}
