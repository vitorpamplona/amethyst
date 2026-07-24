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
import android.os.BatteryManager
import androidx.security.crypto.EncryptedSharedPreferences
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.amethyst.commons.service.lnurl.OkHttpLnurlEndpointResolver
import com.vitorpamplona.amethyst.commons.service.pow.PoWPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.connectedApps.DataStoreNostrSignerPermissionStore
import com.vitorpamplona.amethyst.connectedApps.nip46.DataStoreNip46ClientStore
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.amethyst.model.nip03Timestamp.BitcoinExplorerEndpoint
import com.vitorpamplona.amethyst.model.nip03Timestamp.IncomingOtsEventVerifier
import com.vitorpamplona.amethyst.model.nip03Timestamp.TorAwareOkHttpOtsResolverBuilder
import com.vitorpamplona.amethyst.model.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.model.preferences.BuzzAttestationPreferences
import com.vitorpamplona.amethyst.model.preferences.BuzzChannelStarPreferences
import com.vitorpamplona.amethyst.model.preferences.BuzzWorkspacePreferences
import com.vitorpamplona.amethyst.model.preferences.NamecoinSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.OtsSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.TorSharedPreferences
import com.vitorpamplona.amethyst.model.preferences.UiSharedPreferences
import com.vitorpamplona.amethyst.model.privacyOptions.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.model.torState.AccountsTorStateConnector
import com.vitorpamplona.amethyst.model.torState.TorRelayState
import com.vitorpamplona.amethyst.napplet.DataStoreNappletPermissionStore
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderPrefs
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderWorker
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
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCacheFactory
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.amethyst.service.pow.PowJobRestorer
import com.vitorpamplona.amethyst.service.pow.PowJobStore
import com.vitorpamplona.amethyst.service.pow.PowMiningForegroundService
import com.vitorpamplona.amethyst.service.relayClient.CacheClientConnector
import com.vitorpamplona.amethyst.service.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.service.relayClient.TorCircuitHealthTracker
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.diagnostics.BootRelayDiagnostics
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.speedLogger.RelaySpeedLogger
import com.vitorpamplona.amethyst.service.resourceusage.BatteryDrainSampler
import com.vitorpamplona.amethyst.service.resourceusage.ForegroundTimeIntegrator
import com.vitorpamplona.amethyst.service.resourceusage.ForegroundTracker
import com.vitorpamplona.amethyst.service.resourceusage.HttpUsageMeter
import com.vitorpamplona.amethyst.service.resourceusage.MeteringNostrSigner
import com.vitorpamplona.amethyst.service.resourceusage.ProcessCpuSampler
import com.vitorpamplona.amethyst.service.resourceusage.RadioBurstEstimator
import com.vitorpamplona.amethyst.service.resourceusage.RelayConnectionTimeIntegrator
import com.vitorpamplona.amethyst.service.resourceusage.RelayUsageListener
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageAccountant
import com.vitorpamplona.amethyst.service.resourceusage.ResourceUsageStore
import com.vitorpamplona.amethyst.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.service.resourceusage.SessionTimeIntegrator
import com.vitorpamplona.amethyst.service.resourceusage.UsageCountingInterceptor
import com.vitorpamplona.amethyst.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.service.safeCacheDir
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostWorkGate
import com.vitorpamplona.amethyst.service.scheduledposts.ScheduledPostWorker
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomSyncForegroundService
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.service.uploads.blossom.bud10.LocalBlossomCacheProbe
import com.vitorpamplona.amethyst.service.uploads.nip95.Nip95CacheFactory
import com.vitorpamplona.amethyst.ui.resourceCacheInit
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.amethyst.ui.tor.TorService
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayLogger
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.limits.RelayLimitsTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.stats.RelayReqStats
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDnsStore
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
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.CachingOnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.EsploraBackend
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
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
import kotlinx.coroutines.flow.conflate
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
        LocationState(appContext, applicationIOScope, onListening = { locationSession.setActive(it) })
    }
    val connManager = ConnectivityManager(appContext, applicationIOScope)

    val uiState by lazy {
        Log.d("AppModules", "UiSettingsState Init")
        UiSettingsState(uiPrefs.value, connManager.isMobileOrFalse, applicationIOScope)
    }

    private val torService = TorService(appContext)
    val torManager = TorManager(torPrefs, torService, applicationIOScope)

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

    // Restore + persist held NIP-OA attestations across restarts (device-global). Eager (not
    // lazy) so it loads before the first Buzz-relay AUTH and mirrors later changes to disk.
    val buzzAttestationPrefs = BuzzAttestationPreferences(appContext, applicationIOScope)

    // Restore + persist the joined Buzz workspace relays across restarts (device-global). Eager so
    // the app knows which relays to sync as workspaces on cold start (Buzz membership is
    // server-side; there is no join event to rebuild the set from).
    val buzzWorkspacePrefs = BuzzWorkspacePreferences(appContext, applicationIOScope)

    // Restore + persist the user's starred Buzz workspace channels across restarts (device-global).
    val buzzChannelStarPrefs = BuzzChannelStarPreferences(appContext, applicationIOScope)

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

    // Network identity change (same trigger as Tor's onNetworkChange above), but for DNS
    // the response is deliberately SOFT: most cached answers are still correct on the new
    // network, so staleAll() keeps serving every one of them and merely re-verifies —
    // positives revalidate in the background on next use, negatives (e.g. hosts that only
    // failed because the OLD network / captive portal couldn't resolve them) get re-tried
    // on first touch instead of waiting out their TTL. Nothing is dropped, nothing blocks.
    init {
        applicationIOScope.launch {
            connManager.status
                .map { (it as? ConnectivityStatus.Active)?.networkId }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { surgeDns.staleAll() }
        }
    }

    // Shared cache populated by OnionLocationInterceptor from any HTTP/WebSocket
    // response carrying an Onion-Location header. Consulted by OnionUrlRewriteInterceptor
    // on Tor-enabled clients to transparently redirect to .onion addresses.
    val onionLocationCache = OnionLocationCache()

    // ---- Resource-usage ledger (battery/data accounting) ----
    // Passive on-device counters (bytes per subsystem x network x visibility,
    // relay connection-time, wakelock time, worker runs). Never transmitted;
    // the user can review them in Settings and explicitly DM a report to the
    // developers. See amethyst/plans/2026-07-12-resource-usage-ledger.md.
    val foregroundTracker = ForegroundTracker()

    val resourceUsageStore = ResourceUsageStore(File(appContext.filesDir, ResourceUsageStore.FILE_NAME))

    val resourceUsage = ResourceUsageAccountant(resourceUsageStore, applicationIOScope)

    // Estimates radio wake-ups from HTTP burst patterns — bytes alone don't
    // predict battery; scattered small requests each pay the radio ramp+tail.
    private val radioBurstEstimator =
        RadioBurstEstimator(
            accountant = resourceUsage,
            isMobile = { connManager.isMobileOrFalse.value },
            isForeground = { foregroundTracker.isForeground.value },
        )

    // Single catch-all counter on the shared non-relay HTTP client: role
    // wrappers only relabel via request tags, so no HTTP traffic (including
    // direct getHttpClient users like the napplet broker) escapes the ledger.
    private val httpUsageInterceptor =
        UsageCountingInterceptor(
            accountant = resourceUsage,
            isMobile = { connManager.isMobileOrFalse.value },
            isForeground = { foregroundTracker.isForeground.value },
            bursts = radioBurstEstimator,
        )

    private val httpUsageMeter = HttpUsageMeter()

    // Session-time counters for the app's long-running battery consumers.
    // All timer-free segment integrators: services and status flows flip them
    // on/off, so tracking costs one counter write per transition.
    val alwaysOnSession = SessionTimeIntegrator(resourceUsage, UsageKeys.ALWAYS_ON_MS, UsageKeys.ALWAYS_ON_STARTS).also { it.registerFlushHook() }
    val callSession = SessionTimeIntegrator(resourceUsage, UsageKeys.CALL_MS, UsageKeys.CALL_SESSIONS).also { it.registerFlushHook() }
    val nestsSession = SessionTimeIntegrator(resourceUsage, UsageKeys.NESTS_MS, UsageKeys.NESTS_SESSIONS).also { it.registerFlushHook() }
    private val powSession = SessionTimeIntegrator(resourceUsage, UsageKeys.POW_MS, UsageKeys.POW_SESSIONS).also { it.registerFlushHook() }
    private val torSession = SessionTimeIntegrator(resourceUsage, UsageKeys.TOR_MS, UsageKeys.TOR_STARTS).also { it.registerFlushHook() }
    private val locationSession = SessionTimeIntegrator(resourceUsage, UsageKeys.LOCATION_MS).also { it.registerFlushHook() }

    // Time-per-screen (route base names only — arguments never reach the
    // ledger). Fed by the navigation listener in AppNavigation; foreground
    // gating means backgrounding on a screen closes its segment.
    val screenTime = ScreenTimeIntegrator(resourceUsage)

    init {
        screenTime.start(applicationIOScope, foregroundTracker.isForeground)
    }

    // In-app (Arti) Tor uptime. Watches the raw TorService status — NOT
    // TorManager.status, whose upstream is WhileSubscribed and calls
    // service.start() when collected, so a permanent ledger subscription
    // there would keep Tor's control flow alive on its own. External Tor
    // (Orbot) is deliberately untracked: its battery belongs to Orbot.
    init {
        applicationIOScope.launch {
            torService.status
                .map { it is TorServiceStatus.Active }
                .distinctUntilChanged()
                .collect { torSession.setActive(it) }
        }
    }

    // Measured battery drain (percent while discharging, fg/bg) — the ground
    // truth the app counters get correlated against. One binder read per
    // ledger flush, nothing while idle.
    init {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (batteryManager != null) {
            BatteryDrainSampler(
                accountant = resourceUsage,
                capacityPct = { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 } },
                isCharging = { batteryManager.isCharging },
                isForeground = { foregroundTracker.isForeground.value },
            ).register()
        }
    }

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
            usageInterceptor = httpUsageInterceptor,
        )

    // Offers easy methods to know when connections are happening through Tor or not
    val roleBasedHttpClientBuilder = RoleBasedHttpClientBuilder(okHttpClients, torPrefs.value, httpUsageMeter)

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

    // Show messages from the Relay and controls their dismissal. Attributes each NOTIFY to the
    // account whose AUTH the relay rejected (accountsCache is declared below; the lambda reads it
    // lazily at NOTIFY time, long after init).
    val notifyCoordinator = NotifyCoordinator(client) { pubkey -> accountsCache.accounts.value[pubkey] }

    // Per-relay NIP-42 ALLOW/DENY overrides are now per-account (Account.relayAuthPermissions,
    // backed by a file under accounts/<pubkey>/), so there is no app-wide store here anymore.

    /**
     * The account every napplet/web-app grant and byte of storage is scoped to. Read lazily on each
     * call (never captured) so an account switch immediately moves embedded apps to the new account's
     * namespace: an app authorized by one npub is never authorized under another.
     */
    val nappletAccountScope: () -> String = { sessionManager.loggedInAccount()?.pubKey ?: "" }

    // Singleton stores for napplet permissions — DataStore v1 enforces one instance per file.
    val nappletPermissionStore by lazy { DataStoreNappletPermissionStore(appContext, nappletAccountScope) }

    /**
     * The one napplet permission ledger for the main process. Its persistent half is just the store
     * above, but it also holds the in-memory ALLOW_SESSION grants — and *those* only work if every
     * caller shares this instance. The broker service and the Connected Apps screens used to build
     * a ledger each, so a "Forget"/revoke tapped in the UI cleared the screen's own (always empty)
     * session map while the grants the broker was actually consulting lived on untouched.
     *
     * Session lifetime is bounded by [com.vitorpamplona.amethyst.napplet.NappletBrokerService]'s
     * onDestroy (all applet/browser surfaces gone), which calls `endSession()`.
     */
    val nappletPermissionLedger by lazy { NappletPermissionLedger(nappletPermissionStore, nappletAccountScope) }

    // NOT account-scoped here on purpose: this store is shared with NIP-46, whose coordinates already
    // carry their owning account (`nip46:<signer>:<client>`) and whose sessions run for a specific
    // account rather than the active one. The napplet path namespaces its own coordinate the same way
    // (see NappletBroker.signerCoordinateFor) instead.
    val signerPermissionStore by lazy { DataStoreNostrSignerPermissionStore(appContext) }

    // Display + relay info for connected NIP-46 remote-signer clients.
    val nip46ClientStore by lazy { DataStoreNip46ClientStore(appContext) }

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

    // Caches the latest LIMITS (rights + limits) each relay advertises.
    val relayLimits = RelayLimitsTracker(client)

    // Resource-usage ledger: relay traffic/reconnect + connection-time,
    // foreground-time, process-CPU, and signature-verification collectors.
    init {
        client.addConnectionListener(
            RelayUsageListener(
                accountant = resourceUsage,
                isMobile = { connManager.isMobileOrFalse.value },
                isForeground = { foregroundTracker.isForeground.value },
            ),
        )
        RelayConnectionTimeIntegrator(
            connectedCount = client.connectedRelaysFlow().map { it.size },
            isMobile = connManager.isMobileOrNull,
            isForeground = foregroundTracker.isForeground,
            accountant = resourceUsage,
        ).start(applicationIOScope)
        ForegroundTimeIntegrator(
            isForeground = foregroundTracker.isForeground,
            accountant = resourceUsage,
        ).start(applicationIOScope)
        ProcessCpuSampler(resourceUsage).register()
        cache.verifyMeter = { elapsedNanos, _ ->
            resourceUsage.add(UsageKeys.VERIFY_COUNT, 1)
            resourceUsage.add(UsageKeys.VERIFY_US, elapsedNanos / 1_000)
        }
    }

    // Logs debug messages when needed
    val detailedLogger = if (isDebug) RelayLogger(client, debugSending = false, debugReceiving = false) else null
    val relayReqStats = if (isDebug) RelayReqStats(client) else null
    val logger = if (isDebug) RelaySpeedLogger(client) else null

    // Focused timeline for the DM / gift-wrap loading path (tag: DMPagination).
    // val dmDiagnostics = if (isDebug) DmRelayDiagnosticsLogger(client) else null

    // Per-relay cold-start census: connection outcome by cause, REQ/EOSE/CLOSED accounting,
    // and which relays actually carried the boot (tag: BootRelayDiag).
    val bootDiagnostics = if (isDebug) BootRelayDiagnostics(client) else null

    // Coordinates all subscriptions for the Nostr Client
    val sources: RelaySubscriptionsCoordinator =
        RelaySubscriptionsCoordinator(
            LocalCache,
            client,
            authCoordinator.receiver,
            failureTracker,
            applicationIOScope,
        )

    // fire-and-forget NIP-13 mining: posts queue here and publish when mined.
    // One job mines at a time, racing half the cores over disjoint nonce
    // slices — same total CPU budget as the old 2-job pool, but each post
    // finishes ~minerThreads× sooner and the other half of the cores stays
    // free for the UI. Template jobs checkpoint to disk (restored on login)
    // and every enqueue raises the shortService shield so backgrounding
    // doesn't freeze a miner.
    val powJobStore by lazy {
        PowJobStore(File(appContext.filesDir, PowJobStore.FILE_NAME), applicationIOScope)
    }

    val powPublishQueue by lazy {
        PoWPublishQueue(
            scope = applicationIOScope,
            maxConcurrent = 1,
            minerThreads = PoWPolicy.minerWorkers(Runtime.getRuntime().availableProcessors()),
            persistence = powJobStore,
            onQueueActive = { PowMiningForegroundService.start(appContext) },
        ).also { queue ->
            // Resource ledger: mining burns half the cores flat-out for as
            // long as it runs — without this, PoW shows up in cpu.ms as an
            // unattributed mystery. Wired inside the lazy so the ledger never
            // forces the queue to initialize.
            applicationIOScope.launch {
                queue.jobs
                    .map { jobs -> jobs.any { it.isMining } }
                    .distinctUntilChanged()
                    .collect { powSession.setActive(it) }
            }
        }
    }

    /** App-level BUD-04 mirror sweep, so "sync all" keeps running as the user navigates. */
    val blossomMirrorQueue by lazy {
        BlossomMirrorQueue(
            scope = applicationIOScope,
            onActive = { BlossomSyncForegroundService.start(appContext) },
        )
    }

    val powJobRestorer by lazy {
        PowJobRestorer(powPublishQueue, powJobStore, scheduledPostStore)
    }

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
            powQueue = { powPublishQueue },
            meterSigner = { MeteringNostrSigner(it, resourceUsage) },
            signerPermissionStore = signerPermissionStore,
            nip46ClientStore = nip46ClientStore,
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
    val notificationDispatcher =
        NotificationDispatcher(appContext, applicationIOScope) { heldMs ->
            resourceUsage.add(UsageKeys.WAKELOCK_NOTIF_MS, heldMs)
            resourceUsage.add(UsageKeys.WAKELOCK_NOTIF_COUNT, 1)
        }

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
            // Through the role builder (not raw getHttpClient) so Coil's image
            // traffic carries the "image" ledger tag. Same Tor decision inside.
            callFactory = { roleBasedHttpClientBuilder.okHttpClientForImage(it) },
            thumbnailCache = thumbnailDiskCache,
            backgroundScope = applicationIOScope,
        )
    }

    // androidx.security.crypto's EncryptedSharedPreferences is deprecated with no drop-in successor.
    @Suppress("DEPRECATION")
    fun encryptedStorage(npub: String? = null): EncryptedSharedPreferences = EncryptedStorage.preferences(appContext, npub)

    fun initiate(appContext: Context) {
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedCrashSaver(crashReportCache, applicationIOScope))

        // Ledger: count process starts — high counts reveal WorkManager/restart
        // churn that cold-starts the whole app graph repeatedly.
        resourceUsage.add(UsageKeys.APP_STARTS, 1)

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

        // LRUCache should not be instanciated in the Main thread due to blocking.
        // trimToSize is a no-op on the empty cache; it just forces the object's
        // (blocking) initialization to happen off the main thread.
        applicationIOScope.launch {
            CachedRobohash.trimToSize(100)
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

        // Keep the scheduled-posts worker (15-min periodic + one-time catch-up)
        // enqueued exactly while the store holds a PENDING post — see
        // ScheduledPostWorkGate. Runs independently of the always-on
        // notification setting so scheduled posts still fire when always-on
        // notifications are disabled.
        ScheduledPostWorkGate(
            store = scheduledPostStore,
            scope = applicationIOScope,
            onPendingWork = {
                ScheduledPostWorker.schedule(appContext)
                ScheduledPostWorker.scheduleCatchUp(appContext)
            },
            onNoPendingWork = { ScheduledPostWorker.cancelPeriodic(appContext) },
        ).start()

        // "Starting soon" reminders for NIP-52 appointments the user RSVP'd to as
        // ACCEPTED. The 15-min periodic scanner is only scheduled while it can
        // plausibly fire: this observer enqueues it when an accepted RSVP lands in
        // LocalCache, and the worker cancels its own chain when the cache holds
        // nothing that could still start. LocalCache is memory-only, so the
        // unconditional schedule this replaces could never fire from a WorkManager
        // cold start anyway — it only cost battery.
        applicationIOScope.launch {
            LocalCache
                .observeNewEvents<CalendarRSVPEvent>(Filter(kinds = listOf(CalendarRSVPEvent.KIND)))
                .collect { rsvp ->
                    if (rsvp.status() == RSVPStatusTag.STATUS.ACCEPTED) {
                        CalendarReminderWorker.schedule(appContext)
                    }
                }
        }

        // A rescheduled appointment must also re-arm the chain: the worker
        // cancels itself when every known target is in the past, and a
        // kind-31922/31923 update (the organizer moving the event) arrives
        // WITHOUT any new RSVP — the user's existing RSVP still points at the
        // same address, and observeNewEvents never re-fires for it. conflate +
        // delay bounds the cache rescan to one per 30s while event feeds
        // stream slot events; the scan only runs for users with reminders on.
        applicationIOScope.launch {
            LocalCache
                .observeNewEvents<Event>(
                    Filter(kinds = listOf(CalendarDateSlotEvent.KIND, CalendarTimeSlotEvent.KIND)),
                ).conflate()
                .collect {
                    if (CalendarReminderPrefs(appContext).isEnabled() &&
                        CalendarReminderWorker.couldStillFire(CalendarReminderWorker.acceptedRsvpsInCache(), TimeUtils.now())
                    ) {
                        CalendarReminderWorker.schedule(appContext)
                    }
                    delay(30_000)
                }
        }

        // Watch for account login and start/stop always-on notification service.
        // The manager gates on the global master switch + each account's participation
        // (not the active account), so it only needs to run while someone is logged in.
        applicationIOScope.launch {
            sessionManager.accountContent.collectLatest { state ->
                if (state is AccountState.LoggedIn) {
                    alwaysOnNotificationServiceManager.start()
                } else {
                    alwaysOnNotificationServiceManager.stop()
                }
            }
        }

        // Resume PoW mining jobs that were checkpointed before a process death,
        // for EVERY loaded account (the always-on service preloads non-active
        // accounts, whose pending posts must not stay stranded on disk).
        // Idempotent (the queue dedupes by job id), so re-emissions are safe.
        applicationIOScope.launch {
            accountsCache.accounts.collect { loaded ->
                loaded.values.forEach { powJobRestorer.restore(it) }
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
        // Backgrounding is a natural moment to flush the usage ledger too.
        resourceUsage.flushAsync()
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
