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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.nsite_open_failed
import com.vitorpamplona.amethyst.commons.resources.webxdc_open_failed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.LocalAppNav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ios.napplet.IosNappletHost
import com.vitorpamplona.amethyst.ios.tor.IosArti
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.utils.Log
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
