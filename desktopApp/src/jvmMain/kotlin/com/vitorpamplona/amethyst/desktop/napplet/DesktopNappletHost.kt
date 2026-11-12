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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentCatalog
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentDispatcher
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NsiteNetworkChoices
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobPrefetcher
import com.vitorpamplona.amethyst.commons.napplet.buildAccountNappletBroker
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletUploadGateway
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcBundles
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.note.InlineNappletPosters
import com.vitorpamplona.amethyst.desktop.app.DesktopAppModules
import com.vitorpamplona.amethyst.desktop.app.DesktopInstance
import com.vitorpamplona.amethyst.desktop.service.media.ChromiumMediaSession
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The desktop's napplet and nSite host: opens each NIP-5D napplet or NIP-5A site in its own window
 * on [DesktopCefEngine], with the broker every platform builds ([buildAccountNappletBroker]) for the
 * logged-in account. Consent questions wait in [consent] under their app, and each app's window
 * shows its own; grants persist in the app's napplet and signer ledgers.
 *
 * An nSite goes through Tor or the open web, as the user chose for that site ([networkChoices], the
 * same store Android keeps); [DesktopNappletRoutes] applies it to Chromium.
 *
 * Upload, notifications and the inc bus are not offered here yet; NIP-5D says a host simply leaves
 * them out of `window.napplet`, and these domains are not in Amethyst's supported set anyway.
 */
class DesktopNappletHost(
    private val modules: DesktopAppModules,
) {
    val runtime =
        DesktopNappletRuntime(
            engine = DesktopCefEngine(File(modules.filesDir, "jcef"), DesktopInstance.ownFolder(DesktopInstance.PerCopyFolder.BROWSER_PROFILE), onQuit = { modules.quitRequests.tryEmit(Unit) }),
            blobCache = NappletBlobCache(NappletBlobCache.dirFor(modules.filesDir)),
            httpClient = { useTor -> modules.okHttpClients.getHttpClient(useTor) },
            scope = modules.applicationIOScope,
            torPort = modules.torManager.activePortOrNull,
            nappletBlobsUseTor = { torEnabled() },
            webxdcBundles = WebxdcBundles(WebxdcBundles.dirFor(modules.filesDir)),
        )

    val engine get() = runtime.engine

    val consent = NappletPromptQueues()

    val networkChoices = NsiteNetworkChoices(modules.appStores.getDataStore("napplet_network"), modules.applicationIOScope)

    private val accountScope: () -> String = { modules.sessionManager.loggedInAccount()?.pubKey ?: "" }

    private val storage by lazy { DataStoreNappletStorage(modules.appStores.getDataStore("napplet_storage"), accountScope) }

    private val signerLedger by lazy { NostrSignerPermissionLedger(modules.signerPermissionStore) }

    // Each broker is tied to the Account it was built on: logging out and back in makes a new Account
    // (the old one's scope and signer are closed), so a broker for the old one must not be reused.
    private val brokers = HashMap<String, Pair<Account, NappletBroker>>()

    private val mutableSessions = MutableStateFlow<List<CefNappletSession>>(emptyList())
    val sessions: StateFlow<List<CefNappletSession>> = mutableSessions.asStateFlow()

    private val mutableBrowsers = MutableStateFlow<List<CefBrowserSession>>(emptyList())

    /** The in-app browser's pages, each the content of a [Route.WebPage] screen. */
    val browsers: StateFlow<List<CefBrowserSession>> = mutableBrowsers.asStateFlow()

    private val popupGate = BrowserPopupGate()

    private val mutableConsentWindows = MutableStateFlow<Map<String, CefBrowserSession>>(emptyMap())

    /**
     * Which page's screen shows each `browser:<origin>` app's questions: the last one that asked as
     * that app. Two screens on the same site would otherwise both show the same question.
     */
    val consentWindows: StateFlow<Map<String, CefBrowserSession>> = mutableConsentWindows.asStateFlow()

    private val mutableScreens = MutableStateFlow<List<CefNappletSession>>(emptyList())

    /**
     * Napplets and nSites run as screens ([Route.NostrApp]): the navigation layout places them, so no
     * window of the host's draws them.
     */
    val screens: StateFlow<List<CefNappletSession>> = mutableScreens.asStateFlow()

    // Napplets running inside notes: no window, so they are kept apart from [sessions].
    private val inlineSessions = MutableStateFlow<Set<CefNappletSession>>(emptySet())

    @Volatile
    private var themeColors = NappletThemeColors("#0E0E10", "#E6E6E6", "#9A82DB")

    init {
        PageScrollbars.update(themeColors)
        // NAP-INTENT: a handler is one of the napplets the user pinned, opened in a window of its own.
        runtime.intents =
            NappletIntentDispatcher(
                catalog = { NappletIntentCatalog.of(modules.favoriteApps) },
                launch = { handler -> withContext(Dispatchers.Main) { open(handler.manifest).getOrNull() } },
            )
        // A window signs as the account that opened it. When that account is no longer the one
        // logged in, its windows close rather than keep acting for someone who left.
        modules.applicationIOScope.launch {
            modules.sessionManager.accountContent
                .map { (it as? AccountState.LoggedIn)?.account?.pubKey }
                .distinctUntilChanged()
                .collect { pubKey ->
                    sessions.value.filter { it.accountPubKey != pubKey }.forEach(::close)
                    screens.value.filter { it.accountPubKey != pubKey }.forEach(::closeScreen)
                    browsers.value.filter { it.accountPubKey != pubKey }.forEach(::closeBrowser)
                    inlineSessions.value.filter { it.accountPubKey != pubKey }.forEach(::closeInline)
                    // Their sites' cookies and storage go with them.
                    runtime.engine.retainWebContexts(pubKey)
                    // The last frames shown were the old account's screens.
                    InlineNappletPosters.clear()
                    synchronized(this@DesktopNappletHost) { brokers.keys.retainAll(setOfNotNull(pubKey)) }
                }
        }
    }

    /** Whether Tor is on for the app; a site that chose Tor then goes through it. */
    val torEnabled: Boolean get() = torEnabled()

    /** [torEnabled] as it changes, for the windows' onion switch to follow the app's Tor setting. */
    val torEnabledState: StateFlow<Boolean> =
        modules.torPrefs.torType
            .map { it != TorType.OFF }
            .stateIn(modules.applicationIOScope, SharingStarted.Eagerly, torEnabled())

    private fun torEnabled(): Boolean = modules.torPrefs.torType.value != TorType.OFF

    /** Validates [manifest] and opens it in a new window, or says why it can't. */
    fun open(manifest: NappletManifest): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open napplets"))
        return NappletLaunch.fromManifest(manifest).map { open(it, account, useTor = false) }
    }

    /**
     * Opens an nSite ([com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent] or
     * [com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent]) in a new window. It goes
     * through Tor when Tor is on, unless the user moved this site to the open web.
     */
    fun openSite(event: Event): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open sites"))
        return NappletLaunch.fromSite(event).map { launch ->
            open(launch, account, useTor = torEnabled() && networkChoices.useTor(launch.identity.coordinate))
        }
    }

    /**
     * Runs the webxdc [app] attached to post [postId] by [poster] in a new window: offline, at an
     * origin of its own for this account and app instance.
     */
    fun openWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to run webxdc apps"))
        val selfName = account.userProfile().toBestDisplayName()
        return runCatching { open(NappletLaunch.fromWebxdc(app, postId, poster, account.pubKey, selfName), account, useTor = false) }
    }

    /**
     * Moves an open site to Tor or the open web: remembers the choice, then reopens the window on
     * the new route, as Android relaunches its activity.
     */
    fun setNetworkMode(
        session: CefNappletSession,
        useTor: Boolean,
    ) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        networkChoices.set(session.launch.identity.coordinate, useTor)
        close(session)
        open(session.launch, account, useTor && torEnabled())
    }

    /** Opens [session]'s app again in a fresh window, after a failure the user wants to retry. */
    fun reopen(session: CefNappletSession) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        close(session)
        open(session.launch, account, session.useTor)
    }

    /**
     * Whether [url] can open as an in-app page: a web page, with someone logged in to sign for it.
     * Otherwise the caller hands the link to the system.
     */
    fun canBrowse(url: String): Boolean =
        modules.sessionManager.loggedInAccount() != null &&
            BrowserChrome.originOf(url) != null &&
            CefBrowserSession.navigationOf(url, isMainFrame = true) == CefBrowserSession.Navigation.LOAD

    /**
     * Starts the page of the [route] screen, with NIP-07 for the logged-in account. It goes through
     * Tor when Tor is on, unless the user moved its host to the open web; [Route.WebPage.preferTor]
     * keeps it on Tor whatever that choice (an `.onion`, a page a Tor page opened). Fails for anything
     * but a web page, or with nobody logged in.
     */
    fun openBrowser(route: Route.WebPage): Result<CefBrowserSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to browse with Nostr"))
        if (!canBrowse(route.url)) return Result.failure(IllegalArgumentException("Not a web page"))
        val useTor = torEnabled() && (route.preferTor || OmniboxInput.isOnion(route.url) || networkChoices.useTor(webKey(route.url)))
        return Result.success(openBrowser(route.url, route.id, account, useTor))
    }

    private fun openBrowser(
        url: String,
        routeId: String,
        account: Account,
        useTor: Boolean,
    ): CefBrowserSession =
        CefBrowserSession(
            startUrl = url,
            routeId = routeId,
            accountPubKey = account.pubKey,
            useTor = useTor,
            account = account,
            broker = brokerFor(account),
            runtime = runtime,
            onPopup = ::openPopup,
            onVisit = { visited, title -> modules.browserHistory.record(visited, title) },
            onAsk = { asker, app ->
                if (mutableConsentWindows.value[app] !== asker) mutableConsentWindows.update { it + (app to asker) }
            },
            onZoom = { host, percent ->
                browsers.value.forEach { other -> if (other.isOnHost(host)) other.refreshZoom(percent) }
            },
        ).also { session ->
            // A popup's screen opened: it now counts against the budget until it closes.
            synchronized(pendingPopups) { pendingPopups.remove(routeId) }
            mutableBrowsers.update { it + session }
            session.start()
        }

    // Popups the budget let through whose screens haven't opened yet, by route id, with their opener.
    private val pendingPopups = HashMap<String, CefBrowserSession>()

    /**
     * A page asked for a window: within the shared budget, its opener's screen opens it as a screen of
     * its own, on Tor if the opener was, signing as the same account while it is still logged in.
     */
    private fun openPopup(
        opener: CefBrowserSession,
        url: String,
    ) {
        if (modules.sessionManager.loggedInAccount()?.pubKey != opener.accountPubKey) return
        popupGate.tryOpen {
            val route = Route.WebPage(url, preferTor = opener.useTor, id = newPageId())
            synchronized(pendingPopups) { pendingPopups[route.id] = opener }
            opener.requestPopup(route)
            route.id
        }
    }

    /**
     * Moves [session]'s host to Tor or the open web: remembers the choice, then starts the page it was
     * showing again on the new route, for its screen to show instead.
     */
    fun setBrowserNetworkMode(
        session: CefBrowserSession,
        useTor: Boolean,
    ): CefBrowserSession? {
        val account = modules.sessionManager.loggedInAccount() ?: return null
        val url =
            session.page.value.url
                .ifBlank { session.startUrl }
        networkChoices.set(webKey(url), useTor)
        closeBrowser(session, replaced = true)
        return openBrowser(url, session.routeId, account, (useTor || OmniboxInput.isOnion(url)) && torEnabled())
    }

    /** Starts [session]'s page again, after a failure the user wants to retry. */
    fun reopenBrowser(session: CefBrowserSession): CefBrowserSession? {
        val account = modules.sessionManager.loggedInAccount() ?: return null
        closeBrowser(session, replaced = true)
        return openBrowser(session.startUrl, session.routeId, account, session.useTor)
    }

    /**
     * Closes [session]'s page. [replaced] when its screen stays, with a new page in its place (a retry,
     * a move to or from Tor): a popup's screen then keeps its place in the popup budget.
     */
    fun closeBrowser(
        session: CefBrowserSession,
        replaced: Boolean = false,
    ) {
        mutableBrowsers.update { it - session }
        session.close()
        if (!replaced) popupGate.closed(session.routeId)
        // Popups it was let open that never became screens give their place back.
        val unopened =
            synchronized(pendingPopups) {
                pendingPopups
                    .filterValues { it === session }
                    .keys
                    .toList()
                    .onEach(pendingPopups::remove)
            }
        unopened.forEach(popupGate::closed)
        // Its questions move to another page on the same site, or are dismissed with it.
        val others = browsers.value
        mutableConsentWindows.update { owners ->
            owners
                .mapNotNull { (app, owner) ->
                    if (owner !== session) app to owner else others.lastOrNull { app in it.apps.value }?.let { app to it }
                }.toMap()
        }
        val asked = others.flatMapTo(HashSet()) { it.apps.value }
        session.apps.value
            .filter { it !in asked }
            .forEach(consent::dismissApp)
        if (sessions.value.isEmpty() && browsers.value.isEmpty()) modules.nappletPermissionLedger.endSession()
    }

    // A web host's network choice, kept beside the nSites' in the same store.
    private fun webKey(url: String): String = "web:" + (OmniboxInput.hostOf(url)?.lowercase() ?: url)

    private fun open(
        launch: NappletLaunch,
        account: Account,
        useTor: Boolean,
    ): CefNappletSession =
        CefNappletSession(launch, account.pubKey, useTor, account, brokerFor(account), runtime).also { session ->
            mutableSessions.update { it + session }
            session.start()
        }

    /**
     * Runs [manifest] inside a note as instance [instanceId]: its browser paints into
     * [CefNappletSession.offscreen] for the note to draw, and it may use what the user granted the
     * app but never asks anything ([NappletLaunch.inline]). The note closes it with [closeInline].
     */
    fun openInline(
        manifest: NappletManifest,
        instanceId: String,
    ): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to run napplets"))
        return NappletLaunch.fromManifest(manifest).map { launch -> startInline(launch.inline(instanceId), account) }
    }

    /**
     * Runs the webxdc [app] posted in [postId] by [poster] inside its note, as instance [instanceId]:
     * the same offline origin, and so the same storage, its window has.
     */
    fun openInlineWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
    ): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to run webxdc apps"))
        val launch = NappletLaunch.fromWebxdc(app, postId, poster, account.pubKey, account.userProfile().toBestDisplayName()).inline(instanceId)
        return runCatching { startInline(launch, account) }
    }

    /**
     * Shows the reader's front page [html] (written, resolved and checked by commons `frontpage`) as
     * instance [instanceId], on the same off-screen Chromium as an inline nSite. No script runs in it;
     * its art follows the user's Tor choice the way a site's traffic does.
     */
    fun openInlinePaper(
        html: String,
        title: String,
        instanceId: String,
    ): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to read your Observer"))
        val launch = NappletLaunch.forPaper(account.pubKey, title, html).inline(instanceId)
        return runCatching { startInline(launch, account, useTor = torEnabled() && networkChoices.useTor(launch.identity.coordinate)) }
    }

    private fun startInline(
        launch: NappletLaunch,
        account: Account,
        useTor: Boolean = false,
    ): CefNappletSession {
        val session = CefNappletSession(launch, account.pubKey, useTor = useTor, account, brokerFor(account), runtime, OffscreenNappletView())
        inlineSessions.update { it + session }
        // The account may have left between reading it and listing the session, after the switch
        // closed what it found: this one would run on as someone who left.
        if (modules.sessionManager.loggedInAccount()?.pubKey != account.pubKey) {
            closeInline(session)
            throw IllegalStateException("The account changed")
        }
        session.start()
        return session
    }

    fun closeInline(session: CefNappletSession) {
        inlineSessions.update { it - session }
        session.close()
    }

    fun close(session: CefNappletSession) {
        mutableSessions.update { it - session }
        afterClose(session)
    }

    /**
     * Whether [event] can run as an app screen: a napplet or nSite whose launch is valid, with someone
     * logged in to act for it.
     */
    fun canRunAsScreen(event: Event): Boolean = modules.sessionManager.loggedInAccount() != null && launchOf(event).isSuccess

    private fun launchOf(event: Event): Result<NappletLaunch> =
        when (event) {
            is NappletManifest -> NappletLaunch.fromManifest(event)
            is RootSiteEvent, is NamedSiteEvent -> NappletLaunch.fromSite(event)
            else -> Result.failure(IllegalArgumentException("Not a napplet or an nSite"))
        }

    /**
     * Starts the app of a [Route.NostrApp] screen ([routeId], its coordinate) from its [event]: painted off-screen for
     * the screen to draw, at a screen's frame rate. An nSite goes through Tor when Tor is on, unless the
     * user moved it to the open web, as its window would.
     */
    fun openScreen(
        event: Event,
        routeId: String,
    ): Result<CefNappletSession> {
        val account = modules.sessionManager.loggedInAccount() ?: return Result.failure(IllegalStateException("Log in to open apps"))
        return launchOf(event).map { launch ->
            val useTor = launch.profile.exposesNetwork && torEnabled() && networkChoices.useTor(launch.identity.coordinate)
            startScreen(launch, account, useTor, routeId)
        }
    }

    private fun startScreen(
        launch: NappletLaunch,
        account: Account,
        useTor: Boolean,
        routeId: String,
    ): CefNappletSession =
        CefNappletSession(
            launch,
            account.pubKey,
            useTor,
            account,
            brokerFor(account),
            runtime,
            offscreen = OffscreenNappletView(),
            frameRate = CefNappletSession.SCREEN_FRAME_RATE,
            routeId = routeId,
        ).also { session ->
            mutableScreens.update { it + session }
            session.start()
        }

    /** Moves an app screen's site to Tor or the open web: remembers the choice and starts it again there. */
    fun setScreenNetworkMode(
        session: CefNappletSession,
        useTor: Boolean,
    ): CefNappletSession? {
        val account = modules.sessionManager.loggedInAccount() ?: return null
        networkChoices.set(session.launch.identity.coordinate, useTor)
        closeScreen(session)
        return startScreen(session.launch, account, useTor && torEnabled(), session.routeId.orEmpty())
    }

    /** Starts an app screen's app again, after a failure the user wants to retry. */
    fun reopenScreen(session: CefNappletSession): CefNappletSession? {
        val account = modules.sessionManager.loggedInAccount() ?: return null
        closeScreen(session)
        return startScreen(session.launch, account, session.useTor, session.routeId.orEmpty())
    }

    fun closeScreen(session: CefNappletSession) {
        mutableScreens.update { it - session }
        afterClose(session)
    }

    private fun afterClose(session: CefNappletSession) {
        session.close()
        val coordinate = session.launch.identity.coordinate
        if ((sessions.value + screens.value).none { it.launch.identity.coordinate == coordinate }) consent.dismissApp(coordinate)
        if (sessions.value.isEmpty() && screens.value.isEmpty() && browsers.value.isEmpty()) modules.nappletPermissionLedger.endSession()
    }

    /**
     * Downloads a card's files into the blob cache while it is on screen, so Open starts at once.
     * Never in the clear while Tor is on and not running yet (the launch waits for it instead), and
     * not when the user's media setting keeps images from loading by themselves.
     */
    suspend fun prefetch(
        author: String,
        identifier: String,
        paths: List<PathTag>,
        servers: List<String>,
    ) {
        val account = modules.sessionManager.loggedInAccount() ?: return
        // A card that scrolls by downloads nothing the user's media setting wouldn't load for an image.
        if (!modules.uiState.showImages()) return
        val torPort = modules.torManager.activePortOrNull.value
        if (torPort == null && torEnabled()) return
        // The partition its window will use: the account opening it, and the site.
        val partition = NappletBlobCache.partitionOf(account.pubKey, author, identifier)
        NappletBlobPrefetcher.prefetch(paths, servers, modules.filesDir, torPort ?: -1, partition)
    }

    /** The app's colors, for `theme.get` and the `theme.changed` push. */
    fun updateTheme(colors: NappletThemeColors) {
        if (colors == themeColors) return
        themeColors = colors
        PageScrollbars.update(colors)
        browsers.value.forEach { it.restyleScrollbars() }
        sessions.value.forEach { it.pushTheme(colors) }
        screens.value.forEach { it.pushTheme(colors) }
        inlineSessions.value.forEach { it.pushTheme(colors) }
    }

    fun shutdown() {
        sessions.value.forEach(::close)
        browsers.value.forEach(::closeBrowser)
        inlineSessions.value.forEach(::closeInline)
        screens.value.forEach(::closeScreen)
        // Videos and audio that play in Chromium (they aren't napplets, but share its engine).
        ChromiumMediaSession.closeAll()
        runtime.engine.shutdown()
    }

    @Synchronized
    private fun brokerFor(account: Account): NappletBroker =
        brokers[account.pubKey]?.takeIf { it.first === account }?.second
            ?: buildBroker(account).also { brokers[account.pubKey] = account to it }

    private fun buildBroker(account: Account): NappletBroker =
        buildAccountNappletBroker(
            account = account,
            browserIcons = modules.browserIcons,
            // The account's own grants and storage, whoever is logged in when it asks.
            ledger = modules.nappletPermissionLedger.forAccount(account.pubKey),
            signerLedger = signerLedger,
            storage = storage.forAccount(account.pubKey),
            httpClient = { useProxy -> modules.okHttpClients.getHttpClient(useProxy) },
            useTor = { coordinate ->
                networkChoices.awaitReady()
                torEnabled() && networkChoices.useTor(coordinate)
            },
            prompts = consent,
            theme = NappletThemeGateway { themeColors },
            upload = AccountNappletUploadGateway(account, modules.roleBasedHttpClientBuilder::okHttpClientForUploads),
        )

    companion object {
        /** A new [Route.WebPage.id]. */
        fun newPageId(): String = UUID.randomUUID().toString()
    }
}
