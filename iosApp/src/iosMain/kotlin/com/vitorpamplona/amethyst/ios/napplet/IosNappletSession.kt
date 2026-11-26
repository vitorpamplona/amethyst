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
import com.vitorpamplona.amethyst.commons.napplet.blobs.PaperFiles
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoute
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.napplet.webxdc.OkioWebxdcBundles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcRuntime
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.WebKit.WKWebView
import kotlin.concurrent.Volatile

/**
 * One open napplet, nSite or webxdc app on iOS: the shared [NappletSessionCore] driven through
 * [IosNappletWebEngine], the counterpart of the desktop's `CefNappletSession` and Android's
 * `NappletHostActivity`.
 *
 * - Its storage is the launching account's own data store ([IosNappletHost.dataStoreFor]), as
 *   Android gives every account its own WebView profile; the origin carries no account.
 * - A napplet or a webxdc app has no network of its own: only its files are fetched, through Tor when
 *   "Applets and Websites" says so, and nothing is fetched until Tor runs. Its frames reach nothing
 *   else (CSP, then the engine's request filter).
 * - An nSite reaches the web through its store's route ([IosNappletStores]): on Tor when [useTor], and
 *   then nothing at all leaves (not its files, not a page) until Tor has a port; a Tor that goes away
 *   under it blocks its loads, never sends them direct.
 * - Requests that act for the user wait while it isn't on screen: the screen says when it is
 *   ([setAttended]).
 */
class IosNappletSession(
    val launch: NappletLaunch,
    /** The account that opened it: it keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    /** Whether an nSite's own traffic goes through Tor ([NappletLaunchPlan.useTorFor]); false for the others. */
    val useTor: Boolean,
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
            useTor = useTor,
            appletBlobsUseTor = host.nappletBlobsUseTor(),
            sharedStorageJar = false,
            origins = NappletWebContract.NAPPLET_SCHEME,
        )

    /** Whether it runs inside a note: it never asks the user anything, and gets no file picker (as on Android). */
    val inline: Boolean get() = launch.identity.inline

    private val engine = IosNappletWebEngine(plan, dataStore = { host.dataStoreFor(accountPubKey) }, refusesFilePicker = inline)

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
            route = IosNappletStoreRoute(host.stores, accountPubKey, plan.usesNetwork, host::torPort, host::awaitTorPort, host::torRunning, host::awaitTor),
            files = ::filesFor,
            shellHtml = { NappletWebResources.shellHtml() },
            shimJs = { NappletWebResources.shimJs().decodeToString() },
            links = Links(),
            // NAP-FS through the system's document picker; a run inside a note picks nothing.
            filePort = if (inline) null else IosNappletFilePort(),
            // Attended only while its screen is on show and the app in front.
            attendedWhenShown = false,
            onClosed = host::trimBlobCache,
        ).also { engine.core = it }.also { it.engine = engine }

    val state: StateFlow<NappletSessionState> get() = core.state

    /** The download the page waits on while [NappletSessionState.Loading]. */
    val download: StateFlow<NappletDownloadProgress?> get() = core.download

    /** The browser, once it shows. */
    val webView: StateFlow<WKWebView?> get() = engine.webView

    /** Whether the shell finished loading, for a note that shows the page once it has painted. */
    val loaded: StateFlow<Boolean> get() = engine.shellLoaded

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

    /** Whether the site goes through Tor right now though it chose the open web: another open site of its account wants Tor. */
    fun routedOverTor(): Boolean = !useTor && host.stores.routeOf(accountPubKey) is NappletRoute.Tor

    private fun filesFor(plan: NappletLaunchPlan): NappletFiles {
        launch.paper?.let { return PaperFiles(it) }
        launch.webxdc?.let { app ->
            return WebxdcFiles(
                OkioWebxdcBundles(host.webxdcRoot, host.http, viaTor = plan.blobsUseTor),
                app.url,
                app.hash,
                launch.servers,
                WebxdcRuntime.script(app.selfAddr, app.selfName, app.isAppSender),
            )
        }
        return NappletBlobStore(
            host.blobCache.forSite(NappletBlobCache.partitionOf(accountPubKey, launch.identity.authorPubKey, launch.identity.identifier)),
            host.http,
            launch.paths,
            launch.servers,
            viaTor = plan.blobsUseTor,
        )
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
