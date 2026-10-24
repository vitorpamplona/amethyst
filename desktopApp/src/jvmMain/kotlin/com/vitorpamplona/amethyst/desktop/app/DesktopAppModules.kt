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
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.account.StoreAccountSessionStore
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateManager
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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.MemoryTrimmingService
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.navigation.Route
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
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPanelCollapsePreferences
import com.vitorpamplona.amethyst.commons.model.preferences.NowPlayingSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.OtsSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.PaneWidthPreferences
import com.vitorpamplona.amethyst.commons.model.preferences.TorSettingsStore
import com.vitorpamplona.amethyst.commons.model.preferences.UiSettingsStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.privacylock.DataStorePrivacyLockSettings
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.auth.AuthCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.diagnostics.relayFrameDecoder
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.relays.health.FileRelayHealthPersistence
import com.vitorpamplona.amethyst.commons.relays.health.RelayLatencyMonitor
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.service.crashreports.CrashReportCache
import com.vitorpamplona.amethyst.commons.service.crashreports.ReportAssembler
import com.vitorpamplona.amethyst.commons.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManagerForRelays
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlEndpointResolver
import com.vitorpamplona.amethyst.commons.service.namecoin.NamecoinServices
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobStore
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.PowJobRestorer
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegEncoders
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegLocator
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.AccountsTorStateConnector
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.desktop.network.runSleepResumeMonitor
import com.vitorpamplona.amethyst.desktop.service.calendar.DesktopCalendarReminders
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostScheduler
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.DesktopScheduledPostStore
import com.vitorpamplona.amethyst.desktop.service.scheduledposts.OsScheduler
import com.vitorpamplona.amethyst.desktop.tor.DesktopTorManager
import com.vitorpamplona.amethyst.desktop.update.DesktopAppUpdates
import com.vitorpamplona.amethyst.desktop.update.InstallChannel
import com.vitorpamplona.marmotquic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraBackend
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
    val paneWidthPrefs = PaneWidthPreferences(sharedSettingsStore, applicationIOScope)
    val notificationPanelCollapsePrefs = NotificationPanelCollapsePreferences(sharedSettingsStore, applicationIOScope)

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
        BasicOkHttpWebSocket.Builder(
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
            isTorRouted = { torEvaluatorFlow.shouldUseTorForRelay(it) },
            nip11 = { nip11Cache.getFromCache(it) },
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
        // Resolve ffmpeg/ffprobe off the UI thread now, so the composer reads availability without probing.
        applicationIOScope.launch {
            FfmpegLocator.default.warmUp()
            FfmpegLocator.default.ffmpeg?.let { FfmpegEncoders.listAvailable(it) }
        }
    }

    // The relay pool. The active account's blocked relays (kind 10006) are subtracted from every
    // REQ, COUNT and publish here, once.
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

    /** Signs the BUD-01 retry when a gated Blossom host answers an image fetch with 401. */
    val blossomReadAuthTokens =
        BlossomReadAuthTokenProvider(
            signerProvider = { sessionManager.loggedInAccount()?.signer },
            scope = applicationIOScope,
        )

    /**
     * BUD-10: resolves a `blossom:` URI to a server that holds the blob, from the author's and the
     * user's Blossom server lists. Android's also tries the local Blossom cache, which the desktop
     * does not probe.
     */
    val blossomResolver by lazy {
        BlossomServerResolver(
            loggedInUsers = { listOfNotNull(sessionManager.loggedInAccount()?.pubKey) },
            blossomServers = { addresses ->
                val account = sessionManager.loggedInAccount() ?: return@BlossomServerResolver listOf()
                addresses.map { address ->
                    subscribedFlow(address, account).transform {
                        (it.note.event as? BlossomServersEvent)?.let { event -> emit(event) }
                    }
                }
            },
            httpClientBuilder = roleBasedHttpClientBuilder,
            scope = applicationIOScope,
        )
    }

    /** The note at [address], kept loaded from relays while the flow is collected. */
    private fun subscribedFlow(
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

    /**
     * Where mining jobs are checkpointed and resumed from: only in the primary copy (see
     * DesktopInstance). Every copy logs into the same accounts, so a job a later copy saved would be
     * resumed and published again by the primary. The one place that decides it: the queue saves
     * and the restorer resumes only through this.
     */
    private val powCheckpoints: PoWJobStore? by lazy { powJobStore.takeIf { DesktopInstance.isPrimary } }

    val powPublishQueue by lazy {
        PoWPublishQueue(
            scope = applicationIOScope,
            maxConcurrent = 1,
            minerThreads = platformMinerWorkers(),
            persistence = powCheckpoints,
        )
    }

    /** Resumes checkpointed mining jobs at login; null where nothing is checkpointed ([powCheckpoints]). */
    val powJobRestorer: PowJobRestorer? by lazy { powCheckpoints?.let { PowJobRestorer(powPublishQueue, it, scheduledPostStore) } }

    fun blossomClient(serverBaseUrl: String) = BlossomClient(roleBasedHttpClientBuilder.okHttpClientForUploads(serverBaseUrl))

    val blossomMirrorQueue by lazy { BlossomMirrorQueue(scope = applicationIOScope, clientFor = ::blossomClient) }

    /** OS notifications: the native notifier, its settings, and what is worth a notification. */
    val notifications by lazy { DesktopNotifications(applicationIOScope) }

    /** "Starting soon" notifications for the appointments the user is attending. */
    val calendarReminders by lazy { DesktopCalendarReminders(appStores, notifications, { accountsCache.accounts.value.keys }, applicationIOScope) }

    /** What key storage asks the user (the key-file password, a key this device does not keep). */
    private val keyPrompts = KeyStoragePrompts()

    /** The OS keyring, or without one the NIP-49 password file in [filesDir]. */
    private val keyStorage = SecureKeyStorage.create(filesDir).also { it.prompts = keyPrompts }

    /** Each account's NUT-13 counters, in `cashu_<npub>`: the file name Android uses. */
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

    // A desktop has no location provider yet: location chats and "around me" ask for a geohash.
    private val noLocation = MutableStateFlow<LocationResult>(LocationResult.LackPermission)

    private val legacyCustomFeedImport = LegacyCustomFeedImport(filesDir)

    // A legacy user who ran the deck gets it on here too.
    private val legacyDeckImport = LegacyDeckImport(filesDir) { uiPrefs.deckMode.tryEmit(true) }

    // A NIP-46 signer's auth_url challenges, from logins and from the accounts' signers alike.
    val remoteSignerAuthPrompts = RemoteSignerAuthPrompts()

    val accountsCache =
        AccountCacheState(
            geolocationFlow = { noLocation },
            nwcFilterAssembler = { sources.nwc },
            cashuMintDirectoryFilterAssembler = { sources.cashuMintDirectory },
            okHttpClientForMoney = roleBasedHttpClientBuilder::okHttpClientForMoney,
            // NIP-55 signer apps are Android's: an account that used one is read-only here.
            externalSignerFactory = { _, _ -> null },
            marmotStreamTransportFactory = { scope ->
                QuicAgentTextStreamTransport(parentScope = scope, certificateValidator = JdkCertificateValidator())
            },
            cordnBlobCipher = { SecretEncryptionCordnBlobCipher() },
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
            // The resource-usage ledger is Android's; nothing reads signer counts here.
            meterSigner = { it },
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
            remoteSignerMetadata = REMOTE_SIGNER_METADATA,
            // Same sockets as the app (Tor, blocked relays), but never filed into LocalCache.
            trustNetworkClientBuilder = { NostrClient(websocketBuilder) },
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
            // No metered networks to wait out on a desktop.
            canDownloadLargeFiles = { true },
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
            signerRelayRouting = torEvaluatorFlow,
            remoteSignerAuthPrompts = remoteSignerAuthPrompts,
        )

    private val _appUpdates = MutableStateFlow<AppUpdateManager?>(null)

    /**
     * The updater for this install, once [initiate] has built it off the main thread (reading the
     * install asks dpkg and rpm, and clears old update files); null until then, and for a
     * development run.
     */
    val appUpdates: StateFlow<AppUpdateManager?> = _appUpdates

    /** The updater's "quit so the new version can take over": the window collects it and exits as Quit does. */
    val quitRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Key Backup, the key prompts and the keyring alert, for the shared UI. */
    val keyStorageController by lazy {
        DesktopKeyStorage(
            storage = keyStorage,
            prompts = keyPrompts,
            scope = applicationIOScope,
            keepDeclinedKeys = sessionStore::keepDeclinedKeys,
            reopen = ::openKeysAndLogIn,
        )
    }

    // Before the first login, so a user of the legacy app starts where they left off; again after
    // the user retries the keyring or unlocks the password file. Then, an account already open
    // without the key it keeps (its password skipped, the keychain refused) loads again with it,
    // on [returnTo]: a new session starts a new back stack, which would otherwise open on Home.
    private suspend fun openKeysAndLogIn(returnTo: Route? = null) {
        LegacyDesktopAccountImport(sessionStore, keyStorage, filesDir).start()
        val readOnly = sessionManager.loggedInAccount()?.takeIf { !it.isWriteable() }
        if (readOnly == null) {
            sessionManager.loginWithDefaultAccountIfLoggedOff()
        } else {
            sessionStore
                .reloadWithKey(
                    readOnly.settings.keyPair.pubKey
                        .toNpub(),
                )?.let { sessionManager.startUI(it) { returnTo } }
        }
    }

    /**
     * Saves every loaded account's settings now, as [flushUiPrefs] does the UI's: a last-read marker
     * or a setting changed in the last second before quitting is still in the account's debounce.
     * Bounded, so a stuck keychain cannot hold up the exit.
     */
    fun flushAccountSettings() {
        runBlocking {
            withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
                accountsCache.accounts.value.values
                    .forEach { sessionStore.saveAccountSettings(it.settings) }
            }
        }
    }

    private inner class DesktopAccountSessionHooks : AccountSessionHooks {
        override fun cashuCountersFor(npub: String) = this@DesktopAppModules.cashuCountersFor(npub)

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
            // A login imported from the legacy app may still own a bunker transport key in the keyring.
            try {
                keyStorage.deletePrivateKey(LegacyDesktopAccountImport.bunkerTransportKeyAlias(pubkey.hexToByteArray().toNpub()))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("DesktopAppModules", "Could not delete the bunker transport key of a removed account", e)
            }
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
        powJobRestorer?.let { restorer ->
            applicationIOScope.launch {
                accountsCache.accounts.collect { loaded ->
                    loaded.values.forEach { restorer.restore(it) }
                }
            }
        }

        applicationIOScope.launch { openKeysAndLogIn() }

        calendarReminders.start()

        // Web of Trust: Android brings the active account's network index up to date each time
        // the app comes to the foreground, and once a day from a worker. A desktop window stays
        // open for days, so check on login and then every hour; a no-op unless a sync is due.
        applicationIOScope.launch {
            sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account }
                .distinctUntilChanged()
                .collectLatest { account ->
                    while (account != null) {
                        account.trustNetwork.syncIfStale()
                        delay(TRUST_NETWORK_CHECK_INTERVAL_MS)
                    }
                }
        }

        applicationIOScope.launch {
            val updates =
                DesktopAppUpdates.create(
                    appVersion = appVersion,
                    channel = InstallChannel.detect(),
                    client = client,
                    downloadClient = roleBasedHttpClientBuilder::okHttpClientForVideo,
                    apiClient = roleBasedHttpClientBuilder::okHttpClientForPreview,
                    settings = sharedSettingsStore,
                    updatesDir = File(filesDir, "updates"),
                    scope = applicationIOScope,
                    quit = { quitRequests.tryEmit(Unit) },
                )
            _appUpdates.value = updates
            updates?.start()
        }
    }

    companion object {
        // As on Android (see its AppModules): 70% leaves real headroom; once a minute is cheap; a
        // prune that frees little must not spin.
        private const val HEAP_HIGH_WATER = 0.70
        private const val HEAP_CHECK_INTERVAL_MS = 60_000L
        private const val MIN_RECLAIM_INTERVAL_MS = 120_000L
        private const val FLUSH_TIMEOUT_MS = 3_000L
        private const val TRUST_NETWORK_CHECK_INTERVAL_MS = 60 * 60 * 1000L

        /** What a NIP-46 remote signer shows when this app asks to connect. */
        val REMOTE_SIGNER_METADATA = BunkerClientMetadata(name = "Amethyst Desktop", url = "https://amethyst.social", image = "https://raw.githubusercontent.com/vitorpamplona/amethyst/refs/heads/main/icon.png")
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
