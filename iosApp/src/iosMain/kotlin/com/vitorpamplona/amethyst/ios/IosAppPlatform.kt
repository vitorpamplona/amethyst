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

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.account.ui.legal.StoreTermsGate
import com.vitorpamplona.amethyst.commons.account.ui.legal.storeLegalSettingsCategory
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.qrcode.ui.scanner.IosQrCodeScannerDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.nsite_open_failed
import com.vitorpamplona.amethyst.commons.resources.webxdc_open_failed
import com.vitorpamplona.amethyst.commons.ui.map.TileGeocacheMapTab
import com.vitorpamplona.amethyst.commons.ui.map.TileGeohashLocationPickerContent
import com.vitorpamplona.amethyst.commons.ui.map.TileGeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.LocalAppNav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.DeviceAroundMeLocationLabel
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ios.napplet.IosHeldNappletPane
import com.vitorpamplona.amethyst.ios.napplet.IosHeldPaperSession
import com.vitorpamplona.amethyst.ios.napplet.IosInlineNappletPane
import com.vitorpamplona.amethyst.ios.napplet.IosNappletHost
import com.vitorpamplona.amethyst.ios.tor.IosArti
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppBottomBar as SharedAppBottomBar

/**
 * iOS's [AppPlatform]: the shared bottom bar, as the desktop draws it, and napplets as screens
 * ([IosAppLauncher]). Pickers, sharing and the rest of the platform pieces keep the empty defaults
 * until B4, B5 and B7.
 */
class IosAppPlatform(
    private val modules: IosAppModules,
) : AppPlatform {
    /** Android's version name (the catalog's `app`, plus the branch off main), from the build. */
    override val appVersionName: String get() = IosBuildInfo.VERSION_NAME

    override val appFlavor: String get() = "ios"

    // The built-in Tor runs on Arti (plans/2026-11-25-ios-tor.md, B12d). Only a build without the
    // engine (Kotlin compiled off a Mac, never an app) says Tor is unavailable.
    override val supportsTor: Boolean get() = IosArti.AVAILABLE

    // Orbot on iOS is a VPN, not a SOCKS port another app could route through, so "external Tor"
    // has nothing to point at. IosAppModules runs the built-in Tor for a stored one.
    override val supportsExternalTor: Boolean get() = false

    // The Cashu and on-chain wallets hold the user's funds in the app, which the App Store allows
    // only from an organization account (3.1.5(i)): off unless the build turns them on
    // (iosApp/build.gradle.kts, IN_APP_WALLETS). NWC and external wallets are unaffected.
    override val offersInAppWallets: Boolean get() = IosBuildInfo.IN_APP_WALLETS

    // NIP-46 remote signers: a bunker:// address in the key field, or a nostrconnect:// code a
    // signer scans. iOS has no NIP-55 signer apps, so this is how a key can stay out of the app,
    // as on the desktop. The session, its relays (by the signer-relay Tor setting) and its
    // permission prompts are the shared ones IosAppModules already builds.
    override val supportsRemoteSignerLogin: Boolean get() = true

    // A signer app on this phone that registered nostrconnect: (LSApplicationQueriesSchemes in
    // project.yml lets canOpenURL ask) takes the code without a scan. Only the addresses the login
    // screen makes: never a scheme a page or a note could slip in.
    override fun openNostrConnect(
        uri: String,
        appId: String?,
    ): Boolean {
        if (!uri.startsWith("nostrconnect://")) return false
        val url = NSURL.URLWithString(uri) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return false
        app.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        return true
    }

    // The App Store asks apps with user-generated content for an agreement before sign-up
    // (guideline 1.2), as the Play build does: the same checkbox and the same published terms.
    override val requiresTermsAcceptance: Boolean get() = true

    @Composable
    override fun TermsGate(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        showError: Boolean,
    ) = StoreTermsGate(checked, onCheckedChange, showError)

    override fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? = storeLegalSettingsCategory(uriHandler)

    /** Where the shared app's toasts show: a snackbar over the bottom of the screen (MainViewController). */
    val snackbarHostState = SnackbarHostState()

    // iOS has no toast; a snackbar is what the desktop shows for the same messages. Callers may
    // report from any thread, so the snackbar is shown from the composition's scope.
    @Composable
    override fun rememberToaster(): Toaster {
        val scope = rememberCoroutineScope()
        return remember(scope) {
            Toaster { message, long ->
                scope.launch {
                    snackbarHostState.showSnackbar(message, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
                }
            }
        }
    }

    // The shared tile map (OpenStreetMap, as Android and the desktop draw): the location picker, with
    // Core Location's search and "use my location", and the geocaches' map tab.
    @Composable
    override fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
        precision: Int?,
    ) = TileGeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm, precision)

    @Composable
    override fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) = TileGeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)

    override val hasGeocacheMap: Boolean get() = true

    @Composable
    override fun GeocacheMapTab(
        feedContentState: FeedContentState,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = TileGeocacheMapTab(feedContentState, accountViewModel, nav)

    @Composable
    override fun AroundMeLocationLabel() = DeviceAroundMeLocationLabel()

    // The shared scanner over AVFoundation's QR detector (IosQrCodeScannerDialog).
    @Composable
    override fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) = IosQrCodeScannerDialog(onDismiss, onScan)

    // AVSpeechSynthesizer, the system's text-to-speech.
    @Composable
    override fun SpeakNameButton(name: String) = IosSpeakNameButton(name)

    // The shared bar, imported under another name: inside this override the bare name is the override.
    @Composable
    override fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) = SharedAppBottomBar(selectedRoute, nav, accountViewModel, onClick)

    @Composable
    override fun rememberAppLauncher(): AppLauncher {
        val toaster = rememberToaster()
        val nappletFailed = stringRes(Res.string.napplet_desktop_open_failed)
        val siteFailed = stringRes(Res.string.nsite_open_failed)
        val webxdcFailed = stringRes(Res.string.webxdc_open_failed)
        // A webxdc card sits deep in a note, with no screen's nav at hand: its app opens on the window's.
        val appNav = LocalAppNav.current
        return remember(toaster, nappletFailed, siteFailed, webxdcFailed, appNav) {
            IosAppLauncher({ modules.nappletHost }, toaster, nappletFailed, siteFailed, webxdcFailed, appNav)
        }
    }

    @Composable
    override fun PrefetchNappletFiles(
        author: String,
        identifier: String,
        paths: List<PathTag>,
        servers: List<String>,
    ) {
        LaunchedEffect(author, identifier, paths, servers) { runCatchingCancellable { modules.nappletHost.prefetch(author, identifier, paths, servers) } }
    }

    // A WKWebView in the note, on the same session core as an app screen.
    override val runsNappletsInline: Boolean get() = true

    @Composable
    override fun InlineNappletSurface(
        manifest: NappletManifest,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        val host = modules.nappletHost
        IosInlineNappletPane(host, instanceId, onStatus, modifier) { host.openInline(manifest, instanceId) }
    }

    @Composable
    override fun InlineWebxdcSurface(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        val host = modules.nappletHost
        IosInlineNappletPane(host, instanceId, onStatus, modifier) { host.openInlineWebxdc(app, postId, poster, instanceId) }
    }

    @Composable
    override fun InlinePaperSurface(
        html: String,
        title: String,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
        takesWheel: Boolean,
    ) {
        val host = modules.nappletHost
        // Held by the screen's back-stack entry, not the composition: a tab switch and back keeps the
        // page, scrolled where the reader left it, instead of loading it again from the top.
        val holder = viewModel(key = "front-page-paper") { IosHeldPaperSession(host) }
        val session = remember(holder, instanceId, html, title) { holder.open(instanceId, html, title) }
        IosHeldNappletPane(session, instanceId, onStatus, modifier, takesTouches = takesWheel)
    }
}

/**
 * NIP-5D napplets, NIP-5A nSites and webxdc apps open as [Route.NostrApp] screens, which the
 * navigation layout places, as on the desktop. Web links keep the platform's default (nothing yet).
 */
private class IosAppLauncher(
    private val napplets: () -> IosNappletHost,
    private val toaster: Toaster,
    private val nappletFailed: String,
    private val siteFailed: String,
    private val webxdcFailed: String,
    private val appNav: INav?,
) : AppLauncher {
    override fun launch(
        app: FavoriteApp,
        stillLoading: String,
        nav: INav?,
    ) {
        when (app) {
            is FavoriteApp.WebApp -> launchUrl(app.url, preferTor = false, nav = nav)
            is FavoriteApp.NostrApp -> {
                when (val event = LocalCache.getAddressableNoteIfExists(app.coordinate)?.event) {
                    null -> toaster.show(stillLoading, false)
                    is NappletManifest -> openNapplet(event, nav)
                    is RootSiteEvent, is NamedSiteEvent -> openSite(event, nav)
                    else -> Unit
                }
            }
        }
    }

    override fun launchUrl(
        url: String,
        preferTor: Boolean,
        nav: INav?,
    ) {}

    override val opensNapplets: Boolean get() = true

    override val opensSites: Boolean get() = true

    override val opensWebxdc: Boolean get() = true

    override fun openNapplet(
        manifest: NappletManifest,
        nav: INav?,
    ) {
        val event = manifest as Event
        if (nav == null || !napplets().canRunAsScreen(event)) {
            Log.w(TAG) { "Refusing to open napplet ${event.id}" }
            toaster.show(nappletFailed, false)
            return
        }
        nav.nav(Route.NostrApp(favoriteCoordinateOf(event)))
    }

    override fun openSite(
        site: Event,
        nav: INav?,
    ) {
        if (nav == null || !napplets().canRunAsScreen(site)) {
            Log.w(TAG) { "Refusing to open nSite ${site.id}" }
            toaster.show(siteFailed, false)
            return
        }
        nav.nav(Route.NostrApp(favoriteCoordinateOf(site)))
    }

    override fun openWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ) {
        val nav = appNav
        val routeId = if (nav != null) napplets().prepareWebxdc(app, postId, poster).getOrNull() else null
        if (nav == null || routeId == null) {
            Log.w(TAG) { "Refusing to open webxdc app ${app.url}" }
            toaster.show(webxdcFailed, false)
            return
        }
        nav.nav(Route.NostrApp(routeId))
    }

    private companion object {
        const val TAG = "IosAppLauncher"
    }
}
