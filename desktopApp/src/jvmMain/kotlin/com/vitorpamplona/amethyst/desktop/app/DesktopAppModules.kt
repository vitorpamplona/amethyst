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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.account.AccountSessionHooks
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.StoreAccountSessionStore
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.commons.browser.BrowserIconRegistry
import com.vitorpamplona.amethyst.commons.connectedApps.DataStoreNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.DataStoreNip46ClientStore
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorageVault
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.MemoryTrimmingService
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.BitcoinExplorerEndpoint
import com.vitorpamplona.amethyst.commons.model.nip03Timestamp.TorAwareOkHttpOtsResolverBuilder
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzAttestationStore
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzChannelStarStore
import com.vitorpamplona.amethyst.commons.model.preferences.BuzzWorkspaceStore
import com.vitorpamplona.amethyst.commons.model.preferences.ConcordDirectInviteDeclineStore
import com.vitorpamplona.amethyst.commons.model.preferences.DrawerSectionCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.NowPlayingSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.TorSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.UiSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.privacylock.DataStorePrivacyLockSettings
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.relays.health.FileRelayHealthPersistence
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencyMonitor
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.service.crashreports.CrashReportCache
import com.vitorpamplona.amethyst.commons.service.crashreports.ReportAssembler
import com.vitorpamplona.amethyst.commons.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManagerForRelays
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.OkHttpWebSocket
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlEndpointResolver
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinServices
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobStore
import com.vitorpamplona.amethyst.commons.service.pow.PoWPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.PowJobRestorer
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.AccountsTorStateConnector
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.desktop.network.runSleepResumeMonitor
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostScheduler
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostStore
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.OsScheduler
import com.vitorpamplona.amethyst.desktop.tor.DesktopTorManager
import com.vitorpamplona.marmotquic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraBackend
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Everything the shared app needs from a desktop process, built once: the preference files, the
 * HTTP and relay clients (Tor-aware), the event cache's host, the account cache and the session
 * manager. Mirrors Android's `AppModules`, minus what only a phone has (connectivity callbacks,
 * the battery ledger, foreground services, push).
 */
class DesktopAppModules(
    /** Where the shared stores keep their files. */
    val filesDir: File,
    val appVersion: String,
    val isDebug: Boolean = false,
) {
    val appAgent = "Amethyst-Desktop/$appVersion"

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("DesktopAppModules", "Uncaught exception in the app scope", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private val filesPath: Path = filesDir.toOkioPath()

    val appStores = AppPreferenceStores(rootFilesDir = { filesPath })

    private val sharedSettingsStore get() = appStores.sharedSettings()

    /** The UI preferences, read before the first frame so the theme and language do not blink. */
    val uiPrefs: UiSettingsFlow =
        UiSettingsFlow.build(runBlocking { UiSettingsStore(sharedSettingsStore).load() } ?: UiSettingsFlow().toSettings())

    @OptIn(FlowPreview::class)
    private val uiPrefsSaving =
        uiPrefs.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach { UiSettingsStore(sharedSettingsStore).save(it) }
            .launchIn(applicationIOScope)

    /** Saves the UI preferences now: a change made in the last second before quitting is still in the debounce. */
    fun flushUiPrefs() {
        runBlocking { UiSettingsStore(sharedSettingsStore).save(uiPrefs.toSettings()) }
    }

    val torPrefs: TorSettingsStore =
        TorSettingsStore(
            runBlocking { TorSettingsStore.torPreferences(sharedSettingsStore) } ?: TorSettings(),
            sharedSettingsStore,
            applicationIOScope,
        )

    val namecoinPrefs by lazy { NamecoinSettingsStore(sharedSettingsStore, applicationIOScope) }

    // Loaded before the window opens, so a locked app never shows a frame of its content.
    val privacyLockSettings: DataStorePrivacyLockSettings =
        runBlocking {
            val settings =
                DataStorePrivacyLockSettings(DataStorePrivacyLockSettings.load(sharedSettingsStore), sharedSettingsStore, applicationIOScope)
            if (DataStorePrivacyLockSettings.isEmpty(sharedSettingsStore)) importLegacyPrivacyLock(settings)
            settings
        }

    val otsPrefs by lazy { OtsSettingsStore(sharedSettingsStore, runBlocking { OtsSettingsStore.load(sharedSettingsStore) }) }

    val drawerSectionCollapsePrefs = DrawerSectionCollapsePreferences(sharedSettingsStore, applicationIOScope)

    // A desktop is never on a metered connection the app can see, and is always "connected":
    // relays find out about a lost network from their own sockets.
    private val isMobileOrNull = MutableStateFlow<Boolean?>(false)
    private val connectivity = MutableStateFlow<ConnectivityStatus>(ConnectivityStatus.Active(networkId = 0L, isMobile = false))

    val uiState = UiSettingsState(uiPrefs, MutableStateFlow(false), applicationIOScope)

    val torManager = DesktopTorManager(torPrefs.value.torType, torPrefs.value.externalSocksPort, applicationIOScope)

    val keyCache = EncryptionKeyCache()
    private val surgeDns = SurgeDns()
    private val onionLocationCache = OnionLocationCache()

    // Every connection that is not a relay: images, video, NIP-05, uploads, payments.
    val okHttpClients =
        DualHttpClientManager(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = isMobileOrNull,
            keyCache = keyCache,
            scope = applicationIOScope,
            dns = surgeDns,
            onionCache = onionLocationCache,
        )

    val roleBasedHttpClientBuilder = RoleBasedHttpClientBuilder(okHttpClients, torPrefs.value)

    val namecoinServices by lazy { NamecoinServices(namecoinPrefs, roleBasedHttpClientBuilder, applicationIOScope) }

    val nip05Client by lazy {
        Nip05Client(
            fetcher = OkHttpNip05Fetcher(roleBasedHttpClientBuilder::okHttpClientForNip05),
            namecoinResolverBuilder = { namecoinServices.resolver },
        )
    }

    val otsResolverBuilder by lazy {
        TorAwareOkHttpOtsResolverBuilder(
            roleBasedHttpClientBuilder::okHttpClientForMoney,
            roleBasedHttpClientBuilder::shouldUseTorForMoneyOperations,
            OtsBlockHeightCache(),
            customExplorerUrl = { otsPrefs.current.normalizedUrl() },
        )
    }

    val torEvaluatorFlow = TorRelayState(okHttpClients, torPrefs.value, applicationIOScope)

    // Relay sockets have their own pair of clients: longer timeouts, no per-request interceptors.
    val okHttpClientForRelays =
        DualHttpClientManagerForRelays(
            userAgent = appAgent,
            proxyPortProvider = torManager.activePortOrNull,
            isMobileDataProvider = isMobileOrNull,
            scope = applicationIOScope,
            dns = surgeDns,
            onionCache = onionLocationCache,
        )

    val websocketBuilder =
        OkHttpWebSocket.Builder(
            httpClient = { url -> okHttpClientForRelays.getHttpClient(torEvaluatorFlow.shouldUseTorForRelay(url)) },
            // Tor-routed relays wait for Tor's SOCKS port; RelayProxyClientConnector re-dials them
            // the moment Tor is up.
            canDial = { url -> !torEvaluatorFlow.shouldUseTorForRelay(url) || torManager.activePortOrNull.value != null },
        )

    val cache: LocalCache = LocalCache

    val relayStats by lazy { RelayStats(client) }

    // How fast each relay answers (a post's OK, a query's EOSE and first result) and which relays
    // are slow next to the others, for the relay screens. Measured off the relay client's traffic
    // and kept across restarts.
    val relayLatencyMonitor by lazy {
        RelayLatencyMonitor(
            client = client,
            persistence = FileRelayHealthPersistence(File(filesDir, RELAY_HEALTH_FILE)),
            scope = applicationIOScope,
            torEnabled = { torPrefs.torType.value != TorType.OFF },
        )
    }

    val nip11Cache by lazy { Nip11CachedRetriever(torEvaluatorFlow::okHttpClientForRelay) }

    init {
        cache.appHost = DesktopLocalCacheHost(this, isDebug)
        cache.onchainBackend =
            CachingOnchainBackend(
                EsploraBackend(
                    // The explorer OpenTimestamps uses, so onchain zaps honour the same server and Tor choice.
                    baseUrl = {
                        BitcoinExplorerEndpoint.resolveNormalized(
                            customExplorerUrl = otsPrefs.current.normalizedUrl(),
                            usingTor = roleBasedHttpClientBuilder.shouldUseTorForMoneyOperations(OkHttpBitcoinExplorer.MEMPOOL_API_URL),
                        )
                    },
                    client = roleBasedHttpClientBuilder.okHttpClientForMoney(OkHttpBitcoinExplorer.MEMPOOL_API_URL),
                ),
            )
        cache.lnurlEndpointResolver = OkHttpLnurlEndpointResolver(roleBasedHttpClientBuilder::okHttpClientForMoney)
    }

    // The relay pool. The active account's blocked relays (kind 10006) are subtracted from every
    // REQ, COUNT and publish here, once.
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

    // Reconnects relays when Tor or the relay classification changes.
    val relayProxyClientConnector =
        RelayProxyClientConnector(
            torEvaluatorFlow.flow,
            okHttpClientForRelays.defaultHttpClient,
            okHttpClientForRelays.defaultHttpClientWithoutProxy,
            connectivity,
            torManager.status.map { it.isFullyBootstrapped },
            client,
            applicationIOScope,
        )

    val cacheClientConnector = CacheClientConnector(client, cache)

    val notifyCoordinator = NotifyCoordinator(client) { pubkey -> accountsCache.accounts.value[pubkey] }

    val authCoordinator = AuthCoordinator(client, applicationIOScope)

    val failureTracker = RelayOfflineTracker(client)

    val sources: RelaySubscriptionsCoordinator =
        RelaySubscriptionsCoordinator(cache, client, authCoordinator.receiver, failureTracker, applicationIOScope)

    val signerPermissionStore by lazy { DataStoreNostrSignerPermissionStore(appStores) }

    val nip46ClientStore by lazy { DataStoreNip46ClientStore(appStores.getDataStore(DataStoreNip46ClientStore.FILE_NAME)) }

    val favoriteApps by lazy { FavoriteAppsRegistry(appStores.getDataStore(FavoriteAppsRegistry.FILE_NAME), applicationIOScope) }

    val browserHistory by lazy { BrowserHistoryRegistry(appStores.getDataStore(BrowserHistoryRegistry.FILE_NAME), applicationIOScope) }

    val browserIcons by lazy { BrowserIconRegistry({ filesPath / BrowserIconRegistry.DIR }, applicationIOScope) }

    // Napplets need a sandboxed web view, which the desktop does not host yet: nothing is granted,
    // and nothing is kept.
    val nappletPermissionLedger by lazy {
        NappletPermissionLedger(InMemoryNappletPermissionStore()) { sessionManager.loggedInAccount()?.pubKey ?: "" }
    }

    // The legacy app's store (~/.amethyst/scheduled): posts scheduled there still go out, and the
    // OS timer's headless `--publish-scheduled` run drains the same file.
    val scheduledPostStore = DesktopScheduledPostStore.create()

    /** Publishes the logged-in account's due posts while the window is open. */
    val scheduledPostScheduler by lazy { DesktopScheduledPostScheduler(scheduledPostStore, applicationIOScope) }

    /** The OS timer that relaunches the app headless to publish while it is closed. */
    val osScheduler by lazy { OsScheduler(OsScheduler.resolveAppLaunchCommand() ?: emptyList()) }

    val powJobStore by lazy { PoWJobStore(File(filesDir, PoWJobStore.FILE_NAME), applicationIOScope) }

    val powPublishQueue by lazy {
        PoWPublishQueue(
            scope = applicationIOScope,
            maxConcurrent = 1,
            minerThreads = PoWPolicy.minerWorkers(Runtime.getRuntime().availableProcessors()),
            persistence = powJobStore,
        )
    }

    val powJobRestorer by lazy { PowJobRestorer(powPublishQueue, powJobStore, scheduledPostStore) }

    fun blossomClient(serverBaseUrl: String) = BlossomClient(roleBasedHttpClientBuilder.okHttpClientForUploads(serverBaseUrl))

    val blossomMirrorQueue by lazy { BlossomMirrorQueue(scope = applicationIOScope, clientFor = ::blossomClient) }

    /** OS notifications: the native notifier, its settings, and what is worth a notification. */
    val notifications by lazy { DesktopNotifications(applicationIOScope) }

    /** The OS keyring (or its encrypted-file fallback). */
    private val keyStorage = SecureKeyStorage.create(null)

    val sessionStore =
        StoreAccountSessionStore(
            rootFilesDir = { filesPath },
            appStores = appStores,
            keyVault = SecureKeyStorageVault(keyStorage),
            scope = applicationIOScope,
        )

    // A desktop has no location provider yet: location chats and "around me" ask for a geohash.
    private val noLocation = MutableStateFlow<LocationResult>(LocationResult.LackPermission)

    private val legacyCustomFeedImport = LegacyCustomFeedImport(filesDir)

    // A legacy user who ran the deck gets it on here too.
    private val legacyDeckImport = LegacyDeckImport(filesDir) { uiPrefs.deckMode.tryEmit(true) }

    val accountsCache =
        AccountCacheState(
            geolocationFlow = { noLocation },
            nwcFilterAssembler = { sources.nwc },
            cashuMintDirectoryFilterAssembler = { sources.cashuMintDirectory },
            okHttpClientForMoney = roleBasedHttpClientBuilder::okHttpClientForMoney,
            marmotStreamTransportFactory = { scope ->
                QuicAgentTextStreamTransport(parentScope = scope, certificateValidator = JdkCertificateValidator())
            },
            otsResolverBuilder = { otsResolverBuilder.build() },
            cache = cache,
            client = client,
            appVersion = appVersion,
            encryptionKeyCache = keyCache,
            saveSettings = { sessionStore.saveAccountSettings(it) },
            marmotNotifier = { MarmotGroupNotifier.None },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = { pubKey -> sessionStore.geohashIdentityStore(pubKey) },
            rootFilesDir = { filesDir },
            powQueue = { powPublishQueue },
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
            remoteSignerMetadata = REMOTE_SIGNER_METADATA,
            // Per account, as on Android: joined Buzz workspaces and starred channels, NIP-OA
            // attestations, declined Concord invites and the now-playing status settings.
            startBuzzPersistence = { account ->
                BuzzWorkspaceStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzWorkspaces)
                BuzzChannelStarStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzChannelStars)
                BuzzAttestationStore(sharedSettingsStore, account.scope, account.pubKey, account.buzzAttestation)
                ConcordDirectInviteDeclineStore(sharedSettingsStore, account.scope, account.pubKey, account.concord.directInviteInbox)
                NowPlayingSettingsStore(sharedSettingsStore, account.scope, account.pubKey, account.nowPlayingSettings)
                // Not Buzz, but this is where each account starts: the legacy app's feeds go to the first.
                legacyCustomFeedImport.importInto(account.settings)
                legacyDeckImport.importInto(account.settings, account.signer.pubKey)
            },
        )

    val sessionManager =
        AccountSessionManager(
            accountsCache = accountsCache,
            nip05ClientBuilder = { nip05Client },
            clientBuilder = { client },
            localPreferences = sessionStore,
            scope = applicationIOScope,
            hooks = DesktopAccountSessionHooks(),
            remoteSignerMetadata = REMOTE_SIGNER_METADATA,
        )

    private inner class DesktopAccountSessionHooks : AccountSessionHooks {
        // What one account played must not keep playing, or show in the bar, for the next.
        override fun onSessionEnding() {
            GlobalMediaPlayer.stopVideo()
            GlobalMediaPlayer.stopAudio()
        }

        // A deleted account's parked posts and checkpointed mining jobs must not publish later.
        override suspend fun onAccountRemoved(pubkey: HexKey) {
            scheduledPostStore.removeForAccount(pubkey)
            powPublishQueue.cancelForOwner(pubkey)
            powJobStore.removeForAccount(pubkey)
        }
    }

    // Publishes, across every logged-in account, which relays are DM, trusted or money relays, so
    // the Tor policy can route each one.
    val accountsTorStateConnector = AccountsTorStateConnector(accountsCache, torEvaluatorFlow, applicationIOScope)

    /** The last crash's report, offered to the user on the next start as on Android. */
    val crashReportCache = CrashReportCache(filesDir)

    private val trimmingService = MemoryTrimmingService(cache)

    private val memoryPressureEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when the heap runs high; the account ViewModels drop what they can rebuild. */
    val memoryPressure: Flow<Unit> = memoryPressureEvents

    /**
     * The JVM has no memory-pressure callback, and a desktop session runs for days, so the heap is
     * watched instead, as Android does for the trim it is never sent: above [HEAP_HIGH_WATER] the
     * event cache is pruned and the ViewModels told, at most every [MIN_RECLAIM_INTERVAL_MS].
     */
    private fun startHeapWatchdog() {
        applicationIOScope.launch {
            var lastRunAt = 0L
            while (isActive) {
                delay(HEAP_CHECK_INTERVAL_MS)
                val runtime = Runtime.getRuntime()
                val max = runtime.maxMemory()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val ratio = used.toDouble() / max
                val now = System.nanoTime() / 1_000_000
                if (ratio >= HEAP_HIGH_WATER && now - lastRunAt >= MIN_RECLAIM_INTERVAL_MS) {
                    lastRunAt = now
                    Log.w("DesktopAppModules") { "Heap at ${(ratio * 100).toInt()}% (${used shr 20} MB of ${max shr 20} MB): reclaiming" }
                    memoryPressureEvents.tryEmit(Unit)
                    trimmingService.run(accountsCache.accounts.value.values, sessionStore.allSavedAccounts())
                }
            }
        }
    }

    /** Starts the process-wide work: the saved account's session and the PoW jobs left on disk. */
    fun initiate() {
        startHeapWatchdog()

        // Starts measuring relay response times from the first connection; it reads its file on
        // creation, so off the main thread.
        applicationIOScope.launch { relayLatencyMonitor }

        // After the computer sleeps, the relay sockets are dead though OkHttp still reports them
        // open: re-dial every relay when a wake is detected.
        applicationIOScope.launch {
            runSleepResumeMonitor { client.reconnect(onlyIfChanged = false, ignoreRetryDelays = true) }
        }

        // Resumes PoW mining jobs checkpointed before the last exit, for every loaded account.
        // Idempotent (the queue dedupes by job id), so re-emissions are safe.
        applicationIOScope.launch {
            accountsCache.accounts.collect { loaded ->
                loaded.values.forEach { powJobRestorer.restore(it) }
            }
        }

        applicationIOScope.launch {
            // Before the first login, so a user of the legacy app starts where they left off.
            LegacyDesktopAccountImport(sessionStore, keyStorage, filesDir).start()
            sessionManager.loginWithDefaultAccountIfLoggedOff()
        }
    }

    companion object {
        // As on Android (see its AppModules): 70% leaves real headroom; once a minute is cheap; a
        // prune that frees little must not spin.
        private const val HEAP_HIGH_WATER = 0.70
        private const val HEAP_CHECK_INTERVAL_MS = 60_000L
        private const val MIN_RECLAIM_INTERVAL_MS = 120_000L

        /** What a NIP-46 remote signer shows when this app asks to connect. */
        val REMOTE_SIGNER_METADATA = BunkerClientMetadata(name = "Amethyst Desktop", url = "https://amethyst.social")
    }
}

/**
 * Keeps a report of any uncaught exception in [filesDir], where [DesktopAppModules.crashReportCache]
 * hands it to the user on the next start. Installed before the modules are built, so a crash
 * while building them is kept too.
 */
fun installDesktopCrashReporter(
    filesDir: File,
    appVersion: String,
) {
    Thread.setDefaultUncaughtExceptionHandler(
        UnexpectedCrashSaver(
            CrashReportCache(filesDir),
            ReportAssembler(
                versionLine = "$appVersion-DESKTOP",
                deviceRows =
                    listOf(
                        "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
                        "Arch" to System.getProperty("os.arch").orEmpty(),
                        "Java" to "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
                    ),
            ),
        ),
    )
}

private const val RELAY_HEALTH_FILE = "relay_health.json"
