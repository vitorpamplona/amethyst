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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletOrigins
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** A script every frame of a launch must run at document start, before the page's own. */
enum class NappletGuard {
    /**
     * [NappletWebContract.WEBRTC_GUARD_JS]. Required: a host that can't install it doesn't run the
     * launch, since WebRTC's UDP goes around the proxy (a network for a napplet, the real address
     * for a site on Tor).
     */
    WEBRTC,

    /** [NappletWebContract.COOKIE_GUARD_JS]: every app shares the napplet.local cookie jar. Wanted, not required. */
    COOKIES,
}

/**
 * What one launch decides before any browser exists, the same on every host: its origin, its route,
 * the guards its frames need and the page it probes first.
 *
 * - [appOrigin] carries the account in its seed only when the browser keeps one storage jar for every
 *   account (Desktop's Chromium). Android's WebView and iOS's data stores split storage per account
 *   themselves, so the origin stays the one Android always derived.
 * - [useTor] is whether a site's own traffic goes through Tor; [blobsUseTor] whether the launch's
 *   files do. A launch with no network of its own (a napplet, a webxdc app) fetches its blobs as the
 *   app's "Applets and Websites" Tor switch says.
 */
class NappletLaunchPlan(
    val profile: HostProfile,
    val appOrigin: String,
    val useTor: Boolean,
    val blobsUseTor: Boolean,
    val guards: Set<NappletGuard>,
    val origins: NappletOrigins,
) {
    /** Whether the page talks to the web (through the route the host claims for it). */
    val usesNetwork: Boolean get() = profile.exposesNetwork

    /** With Tor on, nothing at all is fetched until Tor runs: fail closed, never around it. */
    val needsTor: Boolean get() = blobsUseTor

    /** The path probed before anything shows: a missing site is a clear message, not a blank page. */
    val entryPath: String get() = if (profile == HostProfile.WEBSITE) "/" else NappletSiteServer.INDEX_PATH

    /**
     * The shell's head: the host's own [bridgeHead] (Desktop's bridge object; empty where the browser
     * injects it), behind the shell's refusal to start without the WebRTC guard where one is needed.
     */
    fun shellHead(bridgeHead: String): String = if (NappletGuard.WEBRTC in guards) NappletWebContract.WEBRTC_CHECK + bridgeHead else bridgeHead

    companion object {
        /**
         * Whether the launch's frames must lose WebRTC: anything that runs script, except a site on the
         * open web, whose network is its own. Android's `NappletWebRtcGuard.isNeeded`.
         */
        fun webRtcGuardNeeded(
            profile: HostProfile,
            useTor: Boolean,
        ): Boolean = profile.runsScripts && (profile != HostProfile.WEBSITE || useTor)

        /** The guards [profile] needs on [useTor]. */
        fun guardsFor(
            profile: HostProfile,
            useTor: Boolean,
        ): Set<NappletGuard> = if (webRtcGuardNeeded(profile, useTor)) setOf(NappletGuard.WEBRTC, NappletGuard.COOKIES) else setOf(NappletGuard.COOKIES)

        /**
         * Whether a launch's own traffic goes through Tor: only a launch with a network of its own, only
         * with Tor on, and only when the user didn't move it to the open web ([siteViaTor], asked only
         * then).
         */
        fun useTorFor(
            profile: HostProfile,
            torEnabled: Boolean,
            siteViaTor: () -> Boolean,
        ): Boolean = profile.exposesNetwork && torEnabled && siteViaTor()

        fun of(
            launch: NappletLaunch,
            accountPubKey: HexKey,
            useTor: Boolean,
            /** The app's "Applets and Websites" Tor switch, for a launch with no network of its own. */
            appletBlobsUseTor: Boolean,
            /** True when the browser keeps one storage jar for every account (Desktop's Chromium). */
            sharedStorageJar: Boolean,
            origins: NappletOrigins = NappletWebContract.HTTPS,
        ): NappletLaunchPlan {
            val profile = launch.profile
            val appOrigin =
                origins.appOrigin(
                    NappletWebContract.appId(
                        author = launch.identity.authorPubKey,
                        identifier = launch.identity.identifier,
                        partition = if (sharedStorageJar && profile.ownsOrigin) accountPubKey else null,
                        profile = profile,
                    ),
                    profile,
                )
            return NappletLaunchPlan(
                profile = profile,
                appOrigin = appOrigin,
                useTor = useTor,
                blobsUseTor = if (profile.exposesNetwork) useTor else appletBlobsUseTor,
                guards = guardsFor(profile, useTor),
                origins = origins,
            )
        }
    }
}
