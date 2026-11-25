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
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.relayFrameDecoder
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.KtorEsploraTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorLnurlTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorMintTransport
import com.vitorpamplona.amethyst.commons.service.http.KtorNip05Fetcher
import com.vitorpamplona.amethyst.commons.service.http.KtorWebSocket
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.commons.service.upload.KtorBlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
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
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSPOSIXErrorDomain
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.posix.EPROTO

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

    // Tor comes with B12; until then the stored settings say off and nothing routes through it.
    val torPrefs: TorSettingsStore = TorSettingsStore(TorSettings(), sharedSettingsStore, applicationIOScope)

    val namecoinPrefs by lazy { NamecoinSettingsStore(sharedSettingsStore, applicationIOScope) }
    val otsPrefs by lazy { OtsSettingsStore(sharedSettingsStore, runBlocking { OtsSettingsStore.load(sharedSettingsStore) }) }
    val drawerSectionCollapsePrefs = DrawerSectionCollapsePreferences(sharedSettingsStore, applicationIOScope)
    val paneWidthPrefs = PaneWidthPreferences(sharedSettingsStore, applicationIOScope)
    val dockLayoutPrefs = DockLayoutPreferences(sharedSettingsStore, applicationIOScope)

    val uiState = UiSettingsState(uiPrefs, MutableStateFlow(false), applicationIOScope)

    val keyCache = EncryptionKeyCache()

    /** One NSURLSession-backed client for relays and HTTP alike; per-role clients come with A2. */
    val httpClient: HttpClient =
        HttpClient(Darwin) {
            install(WebSockets)
        }

    // Apple's socket offers permessage-deflate and drops the connection with EPROTO when a relay
    // breaks the extension's rules (nostr.mom sets RSV1 on continuation frames). Such a relay is
    // dialed again without compression.
    val websocketBuilder =
        KtorWebSocket.Builder(
            httpClient = { httpClient },
            scope = applicationIOScope,
            compression = KtorWebSocket.Compression(isFailure = ::isDarwinProtocolError),
        )

    /** Blossom uploads over [httpClient]; re-encoded pictures wait in the temp folder until they go up. */
    val mediaUploader by lazy {
        IosMediaUploader(
            blossom = KtorBlossomClient { httpClient },
            tempDir = (NSTemporaryDirectory().toPath() / "uploads"),
        )
    }

    val nip05Client by lazy { Nip05Client(fetcher = KtorNip05Fetcher { httpClient }) }

    val lnurlTransport = KtorLnurlTransport { httpClient }

    // Images load through the same client. The Caches folder is the one iOS may purge.
    init {
        IosImageLoaderSetup.setup(
            httpClient = httpClient,
            cacheDir = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String).toPath() / "images",
        )
    }

    val cache: LocalCache = LocalCache

    // The on-chain wallet and on-chain zaps read the chain through the explorer OpenTimestamps uses
    // (OTS settings), as on Android and the desktop. No Tor on iOS until B12, so the default is
    // blockstream.info.
    init {
        cache.onchainBackend =
            CachingOnchainBackend(
                EsploraClient(
                    baseUrl = { BitcoinExplorerEndpoint.resolveNormalized(customExplorerUrl = otsPrefs.current.normalizedUrl(), usingTor = false) },
                    transport = KtorEsploraTransport { httpClient },
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
            cashuMintTransport = KtorMintTransport { httpClient },
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
            canDownloadLargeFiles = { true },
            accountSettingsStore = sharedSettingsStore,
        )

    val sessionManager =
        AccountSessionManager(
            accountsCache = accountsCache,
            nip05ClientBuilder = { nip05Client },
            clientBuilder = { client },
            localPreferences = sessionStore,
            scope = applicationIOScope,
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
        )
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

/** The error Apple's networking reports for a frame it cannot parse: POSIX EPROTO. */
private fun isDarwinProtocolError(e: Throwable): Boolean {
    val origin = (e as? DarwinHttpRequestException)?.origin ?: return false
    return origin.domain == NSPOSIXErrorDomain && origin.code == EPROTO.toLong()
}
