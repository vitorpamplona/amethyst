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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoutePort
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.WebKit.WKWebView
import kotlin.concurrent.Volatile

/**
 * One open napplet on iOS: the shared [NappletSessionCore] driven through [IosNappletWebEngine], the
 * counterpart of the desktop's `CefNappletSession` and Android's `NappletHostActivity`.
 *
 * - Its storage is the launching account's own data store ([IosNappletHost.dataStoreFor]), as
 *   Android gives every account its own WebView profile; the origin carries no account.
 * - A napplet has no network of its own: only its files are fetched, through Tor when "Applets and
 *   Websites" says so, and nothing is fetched until Tor runs. Its frames reach nothing else (CSP,
 *   then the engine's request filter).
 * - Requests that act for the user wait while it isn't on screen: the screen says when it is
 *   ([setAttended]).
 */
class IosNappletSession(
    val launch: NappletLaunch,
    /** The account that opened it: it keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    account: Account?,
    broker: NappletBroker,
    private val host: IosNappletHost,
    /** The [com.vitorpamplona.amethyst.commons.model.navigation.Route.NostrApp] coordinate this is the screen of. */
    val routeId: String,
) {
    private val plan =
        NappletLaunchPlan.of(
            launch,
            accountPubKey,
            useTor = false,
            appletBlobsUseTor = host.nappletBlobsUseTor(),
            sharedStorageJar = false,
            origins = NappletWebContract.NAPPLET_SCHEME,
        )

    private val engine = IosNappletWebEngine(plan, dataStore = { host.dataStoreFor(accountPubKey) })

    /** Where a link the user clicked goes: Safari for the web; a front page's nostr: link into the app. */
    @Volatile
    var onNostrLink: (String) -> Unit = {}

    private val core: NappletSessionCore<IosNappletSession> =
        NappletSessionCore(
            owner = this,
            launch = launch,
            accountPubKey = accountPubKey,
            plan = plan,
            account = account,
            broker = broker,
            services = host.services,
            route = Route(),
            files = ::filesFor,
            shellHtml = { NappletWebResources.shellHtml() },
            shimJs = { NappletWebResources.shimJs().decodeToString() },
            links = Links(),
            // NAP-FS needs the file picker (the plan's N7).
            filePort = null,
            // Attended only while its screen is on show and the app in front.
            attendedWhenShown = false,
            onClosed = host::trimBlobCache,
        ).also { engine.core = it }.also { it.engine = engine }

    val state: StateFlow<NappletSessionState> get() = core.state

    /** The download the page waits on while [NappletSessionState.Loading]. */
    val download: StateFlow<NappletDownloadProgress?> get() = core.download

    /** The browser, once it shows. */
    val webView: StateFlow<WKWebView?> get() = engine.webView

    val isClosed: Boolean get() = core.isClosed

    var dialogs: NappletJsDialogs?
        get() = engine.dialogs
        set(value) {
            engine.dialogs = value
        }

    fun start() = core.start()

    /** Whether the user is looking at it: on screen, with the app in front. */
    fun setAttended(attended: Boolean) {
        Log.d(TAG) { "${launch.identity.coordinate.take(24)} attended=$attended" }
        engine.attended = attended
        core.setAttended(attended)
    }

    /** Back: a step back in the page's history when it has one. False when the screen should close. */
    fun goBack(): Boolean = engine.goBack()

    fun pushTheme(colors: NappletThemeColors) = core.pushTheme(colors)

    fun close() = core.close()

    private fun filesFor(plan: NappletLaunchPlan): NappletFiles =
        NappletBlobStore(
            host.blobCache.forSite(NappletBlobCache.partitionOf(accountPubKey, launch.identity.authorPubKey, launch.identity.identifier)),
            host.http,
            launch.paths,
            launch.servers,
            viaTor = plan.blobsUseTor,
        )

    /**
     * iOS proxies per data store, so there is no process-wide route to claim (`NappletProxyClaims` is
     * not used here). A napplet only waits for Tor when its files go through it; the open web (an
     * nSite) comes with the plan's N6.
     */
    private inner class Route : NappletRoutePort {
        override val torRunning: Boolean get() = host.torRunning()

        override suspend fun claim(useTor: Boolean): Boolean {
            Log.w(TAG) { "${launch.identity.coordinate} asks for a network of its own, which iOS doesn't give yet" }
            return false
        }

        override suspend fun awaitTor(): Boolean = host.awaitTor()

        override fun release() = Unit
    }

    private inner class Links : NappletLinkOpener {
        override fun openExternal(url: String) {
            val link = NSURL.URLWithString(url) ?: return
            UIApplication.sharedApplication.openURL(link, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }

        override fun openNostr(uri: String) = onNostrLink(uri)
    }

    private companion object {
        const val TAG = "IosNappletSession"
    }
}
