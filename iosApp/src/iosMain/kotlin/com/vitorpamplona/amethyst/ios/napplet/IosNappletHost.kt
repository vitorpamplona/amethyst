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
package com.vitorpamplona.amethyst.ios.napplet

import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NsiteNetworkChoices
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.iosNappletHttp
import com.vitorpamplona.amethyst.commons.napplet.buildAccountNappletBroker
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionServices
import com.vitorpamplona.amethyst.commons.napplet.webxdc.OkioWebxdcBundles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.ios.IosAppModules
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.WebKit.WKWebsiteDataStore
import kotlin.concurrent.Volatile

/**
 * What the iOS app keeps for napplets and nSites, one per process: the broker of each account
 * ([buildAccountNappletBroker], as Android and Desktop build theirs), the consent questions it asks
 * ([consent], answered by the shared consent cards over the app's screen), each site's Tor choice
 * ([networkChoices]), the storage and grants, the blob cache and the HTTP every fetch goes out on.
 * It mirrors the desktop's `DesktopNappletHost`, and runs each napplet as a [IosNappletSession]
 * screen ([screens]).
 *
 * Built lazily by [IosAppModules]: nothing here runs, reads or connects until a napplet opens.
 *
 * nSites reach the web through their account's data store, on Tor or the open web as Android decides
 * ([useTorFor], [stores]); webxdc apps run offline from an archive unpacked under Caches.
 *
 * Not offered yet, so a napplet's `window.napplet` leaves them out: uploads (the file picker is the
 * plan's N7), notifications and NAP-INTENT (a handler would open as a screen of its own).
 */
class IosNappletHost(
    private val modules: IosAppModules,
) {
    /** The questions every broker here asks, for the screen to show and answer. */
    val consent = NappletPromptQueues()

    /** Where each nSite goes: the per-site choice, else "Applets and Websites" in the Tor settings. */
    val networkChoices = NsiteNetworkChoices(modules.appStores.getDataStore("napplet_network"), modules.applicationIOScope) { appletsAndWebsitesViaTor() }

    /**
     * Every blob, resource and archive fetch: the app's Darwin clients, through Tor or not as each
     * request asks. The Tor route fails while Tor has no port, so it never falls back to direct.
     */
    val http: NappletHttp = iosNappletHttp(modules.httpClients)

    /** The verified blobs of every launch, per site and account; the system may purge it. */
    val blobCache: NappletBlobCache by lazy {
        NappletBlobCache(NappletBlobCache.dirFor(cachesDir()))
    }

    /** Where webxdc archives are unpacked; the system may purge it, and a launch downloads again. */
    val webxdcRoot: Path by lazy { OkioWebxdcBundles.dirFor(cachesDir()) }

    /** Each account's web storage and its sites' route. Main thread. */
    val stores = IosNappletStores { modules.torManager.activePortOrNull.value }

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val signerLedger by lazy { NostrSignerPermissionLedger(modules.signerPermissionStore) }

    // Each broker is tied to the Account it was built on: logging out and back in makes a new Account
    // (the old one's scope and signer are closed), so a broker for the old one must not be reused.
    private val lock = KmpLock()
    private val brokers = HashMap<String, Pair<Account, NappletBroker>>()

    /** What `theme.get` answers; the screen sets it from the app's theme as it changes ([updateTheme]). */
    @Volatile
    var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")
        private set

    private val attendance = NappletAttendance<IosNappletSession>()

    /** What every open napplet shares: attendance, live relay subscriptions and the NAP-INC bus. */
    val services =
        NappletSessionServices(
            attendance = attendance,
            liveSubscriptions = NappletLiveSubscriptions(modules.applicationIOScope, attendance),
            incBus = NappletIncBus(),
            intents = { null },
        )

    private val mutableScreens = MutableStateFlow<List<IosNappletSession>>(emptyList())

    /** The napplets open as [Route.NostrApp] screens. */
    val screens: StateFlow<List<IosNappletSession>> = mutableScreens.asStateFlow()

    // The blob cache is shared, so it is trimmed as screens close, in one pass per burst.
    private val trims = Channel<Unit>(Channel.CONFLATED)

    init {
        // While an app is open, the stores follow Tor's port: a restart elsewhere re-points them, Tor
        // going away blocks a Tor site's store. Watched only then, since watching Tor keeps it running.
        @OptIn(ExperimentalCoroutinesApi::class)
        modules.applicationIOScope.launch {
            screens
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { open -> if (open) modules.torManager.activePortOrNull else emptyFlow() }
                .distinctUntilChanged()
                .collect { withContext(Dispatchers.Main) { stores.reapplyAll() } }
        }
        // A screen signs as the account that opened it. When that account is no longer the one logged
        // in, its screens close rather than keep acting for someone who left, and its broker goes too.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey ->
                    screens.value.filter { it.accountPubKey != pubKey }.forEach { withContext(Dispatchers.Main) { closeScreen(it) } }
                    lock.withLock { brokers.keys.retainAll(setOfNotNull(pubKey)) }
                }
        }
        modules.applicationIOScope.launch {
            for (ignored in trims) blobCache.trimToSize(NappletBlobCache.DEFAULT_MAX_BYTES)
        }
        // Compiled request filters of launches that ended in a crash would otherwise stay in WebKit's store.
        modules.applicationIOScope.launch(Dispatchers.Main) {
            IosNappletWebEngine.sweepContentRules()
            // Stores of removed accounts that were still in use when they left: emptied then, gone now.
            IosNappletStores.removePendingStores()
        }
    }

    /** Tor's SOCKS port now, or null: a site on Tor waits for one, and never goes direct without it. */
    fun torPort(): Int? = modules.torManager.activePortOrNull.value

    /** Waits for [torPort], for as long as a site on Tor waits before it fails closed. */
    suspend fun awaitTorPort(): Boolean = withTimeoutOrNull(TOR_WAIT_MS) { modules.torManager.activePortOrNull.first { it != null } } != null

    /** Whether Tor-routed requests can go out now: Tor has a port, or the user skipped Tor this session. */
    fun torRunning(): Boolean = modules.torManager.activePortOrNull.value != null || modules.torManager.sessionBypass.value

    /** Waits for [torRunning], for as long as a launch waits for Tor before it fails closed. */
    suspend fun awaitTor(): Boolean =
        withTimeoutOrNull(TOR_WAIT_MS) {
            combine(modules.torManager.activePortOrNull, modules.torManager.sessionBypass) { port, bypass -> port != null || bypass }.first { it }
        } != null

    /**
     * The web storage of [pubKey]'s napplets: one persistent data store per account, named as Android
     * names the account's WebView profile ([NappletStorageProfile]). Main thread.
     */
    fun dataStoreFor(pubKey: HexKey): WKWebsiteDataStore = stores.storeFor(pubKey)

    /** Trims the shared blob cache soon, off the caller's thread. */
    fun trimBlobCache() {
        trims.trySend(Unit)
    }

    /**
     * Whether [event] can run as an app screen here: a valid napplet or nSite, with someone logged in
     * to act for it.
     */
    fun canRunAsScreen(event: Event): Boolean = modules.sessionManager.loggedInAccount() != null && launchOf(event).isSuccess

    private fun launchOf(event: Event): Result<NappletLaunch> =
        when (event) {
            is NappletManifest -> NappletLaunch.fromManifest(event)
            is RootSiteEvent, is NamedSiteEvent -> NappletLaunch.fromSite(event)
            else -> Result.failure(IllegalArgumentException("Not a napplet or an nSite"))
        }

    // Webxdc apps have no event of their own to find again: the launch waits here for its screen.
    private val pendingWebxdc = HashMap<String, Pair<HexKey, NappletLaunch>>()

    /**
     * Readies the webxdc [app] attached to post [postId] by [poster] for a [Route.NostrApp] screen and
     * returns that screen's id: offline, at an origin of its own for this account and app instance.
     * Main thread.
     */
    fun prepareWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ): Result<String> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to run webxdc apps"))
        return runCatching {
            val launch = NappletLaunch.fromWebxdc(app, postId, poster, account.pubKey, account.userProfile().toBestDisplayName())
            val routeId = WEBXDC_ROUTE_PREFIX + app.stageKey(poster, postId)
            pendingWebxdc[routeId] = account.pubKey to launch
            routeId
        }
    }

    /** Whether [routeId] names a webxdc screen rather than an event's coordinate. */
    fun isWebxdcRoute(routeId: String): Boolean = routeId.startsWith(WEBXDC_ROUTE_PREFIX)

    /** Starts the webxdc app [prepareWebxdc] readied for [routeId]. Main thread. */
    fun openWebxdcScreen(routeId: String): Result<IosNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to run webxdc apps"))
        // Readied for the account that is logged in now: its selfAddr is that account's.
        val launch = pendingWebxdc[routeId]?.takeIf { it.first == account.pubKey }?.second ?: return Result.failure(IllegalStateException("No webxdc app for $routeId"))
        return Result.success(startScreen(launch, account, routeId, useTor = false))
    }

    /**
     * Starts the app of a [Route.NostrApp] screen ([routeId], its coordinate) from its [event]. An nSite
     * goes through Tor when Tor is on, unless the user moved it to the open web, as on Android. Main thread.
     */
    fun openScreen(
        event: Event,
        routeId: String,
    ): Result<IosNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open apps"))
        return launchOf(event).map { launch ->
            val useTor = NappletLaunchPlan.useTorFor(launch.profile, torEnabled()) { networkChoices.useTor(launch.identity.coordinate) }
            startScreen(launch, account, routeId, useTor)
        }
    }

    private fun startScreen(
        launch: NappletLaunch,
        account: Account,
        routeId: String,
        useTor: Boolean,
    ): IosNappletSession =
        IosNappletSession(launch, account.pubKey, useTor, account, brokerFor(account), this, routeId).also { session ->
            mutableScreens.update { it + session }
            session.pushTheme(themeColors)
            session.start()
        }

    /** Starts a screen's app again, after a failure the user wants to retry. Main thread. */
    fun reopenScreen(session: IosNappletSession): IosNappletSession? {
        val account = modules.sessionManager.loggedInAccount()?.takeIf { it.pubKey == session.accountPubKey } ?: return null
        closeScreen(session)
        return startScreen(session.launch, account, session.routeId, session.useTor)
    }

    /**
     * Moves a screen's site to Tor or the open web: remembers the choice and starts it again there, as
     * Android relaunches its activity and Desktop its window. A live store is never re-pointed from
     * Tor to the open web under a page. Main thread.
     */
    fun setScreenNetworkMode(
        session: IosNappletSession,
        useTor: Boolean,
    ): IosNappletSession? {
        val account = modules.sessionManager.loggedInAccount()?.takeIf { it.pubKey == session.accountPubKey } ?: return null
        networkChoices.set(session.launch.identity.coordinate, useTor)
        closeScreen(session)
        return startScreen(session.launch, account, session.routeId, useTor && torEnabled())
    }

    /**
     * [pubKey]'s account was removed from this device: its screens close, then its web storage goes
     * (WebKit refuses while a web view still uses it, so the store retries while they tear down).
     */
    suspend fun removeAccount(pubKey: HexKey) {
        withContext(Dispatchers.Main) {
            screens.value.filter { it.accountPubKey == pubKey }.forEach(::closeScreen)
            stores.remove(pubKey)
        }
    }

    /** Closes a screen's napplet: its questions go with it, and the session's grants with the last one. Main thread. */
    fun closeScreen(session: IosNappletSession) {
        mutableScreens.update { it - session }
        session.close()
        val coordinate = session.launch.identity.coordinate
        if (screens.value.none { it.launch.identity.coordinate == coordinate }) consent.dismissApp(coordinate)
        // "Allow for this session" lasts while any napplet is open, as on the desktop.
        if (screens.value.isEmpty()) modules.nappletPermissionLedger.endSession()
    }

    /** The app's colors, for `theme.get` and the `theme.changed` push. */
    fun updateTheme(colors: NappletThemeColors) {
        if (colors == themeColors) return
        themeColors = colors
        screens.value.forEach { it.pushTheme(colors) }
    }

    /** Whether Tor is on for the app. */
    fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    /** [torEnabled] as it changes, for a site's onion switch to follow the app's Tor setting. */
    val torEnabledState: StateFlow<Boolean> by lazy {
        modules.torPrefs.torType
            .map { it != TorType.OFF }
            .stateIn(modules.applicationIOScope, SharingStarted.Eagerly, torEnabled())
    }

    private fun appletsAndWebsitesViaTor(): Boolean = modules.torPrefs.value.appletsAndWebsitesViaTor.value

    /** Whether a napplet's own files go through Tor ("Applets and Websites"), as on Desktop. */
    fun nappletBlobsUseTor(): Boolean = torEnabled() && appletsAndWebsitesViaTor()

    /**
     * Whether the app at [coordinate] reaches the network through Tor: its per-site choice, read once
     * the stored choices have loaded (Tor until then), and never while Tor is off for the app.
     */
    suspend fun useTor(coordinate: String): Boolean {
        networkChoices.awaitReady()
        return torEnabled() && networkChoices.useTor(coordinate)
    }

    /** The broker that answers [account]'s napplets, built on first use. */
    fun brokerFor(account: Account): NappletBroker =
        lock.withLock {
            brokers[account.pubKey]?.takeIf { it.first === account }?.second
                ?: buildBroker(account).also { brokers[account.pubKey] = account to it }
        }

    private fun buildBroker(account: Account): NappletBroker =
        buildAccountNappletBroker(
            account = account,
            browserIcons = modules.browserIcons,
            // The account's own grants and storage, whoever is logged in when it asks.
            ledger = modules.nappletPermissionLedger.forAccount(account.pubKey),
            signerLedger = signerLedger,
            storage = storage.forAccount(account.pubKey),
            http = http,
            useTor = ::useTor,
            prompts = consent,
            theme = NappletThemeGateway { themeColors },
        )

    private companion object {
        /** How long a launch whose files go through Tor waits for it before failing closed (the desktop's). */
        const val TOR_WAIT_MS = 30_000L

        /** A webxdc screen's [Route.NostrApp] id: its stage key, behind a prefix no coordinate has. */
        const val WEBXDC_ROUTE_PREFIX = "webxdc:"

        fun cachesDir(): Path = (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String).toPath()
    }
}
