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
package com.vitorpamplona.amethyst.napplethost

import android.os.Bundle
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/**
 * One launch as the keyless `:napplet` process receives it from the main process's launcher, the same
 * for the full-screen [NappletHostActivity] and the embedded [NappletHostService]: the verified files to
 * serve, the opaque launch token the broker resolves to the trusted identity, and the route it was given.
 * Nothing here is trusted for consent: the broker decides every request from the token alone.
 */
class NappletSandboxLaunch(
    /** The launch as the sandbox serves it. Its declared set only shapes the page; the broker enforces its own. */
    val launch: NappletLaunch,
    /** Opaque token the broker resolves to this launch's identity, declared set and account. */
    val launchToken: String,
    /** The launcher's route: a site's own traffic, or a napplet's blobs, through Tor ([NappletLaunchPlan.blobsUseTor]). */
    val useTor: Boolean,
    /** Tor's SOCKS port, or -1 when it isn't known (with [useTor], nothing may be fetched). */
    val proxyPort: Int,
    val themeType: String,
    /** Opaque per-account WebView storage profile; null or blank lands on the shared default jar. */
    val webViewProfile: String?,
    /** Pre-localized labels for the "what it can access" sheet. */
    val capabilityLabels: List<String>,
) {
    val profile: HostProfile get() = launch.profile
    val author: String get() = launch.identity.authorPubKey
    val identifier: String get() = launch.identity.identifier

    /**
     * The shared plan: this browser splits storage per account itself (a WebView profile), so the origin
     * carries no account. The launcher's one Tor flag is a site's route or a napplet's blob route.
     */
    fun plan(): NappletLaunchPlan =
        NappletLaunchPlan.of(
            launch = launch,
            accountPubKey = "",
            useTor = profile.exposesNetwork && useTor,
            appletBlobsUseTor = useTor,
            sharedStorageJar = false,
        )

    /** Whether [other] is this same launch (a `singleTask` host brought back to it keeps the page). */
    fun isSameLaunch(other: NappletSandboxLaunch): Boolean =
        other.author == author &&
            other.identifier == identifier &&
            other.profile == profile &&
            other.webViewProfile == webViewProfile &&
            other.useTor == useTor &&
            other.launch.webxdc?.url == launch.webxdc?.url &&
            other.launch.webxdc?.hash == launch.webxdc?.hash

    companion object {
        /** The launch in [data], or null when it is malformed or carries no token. */
        fun from(data: Bundle?): NappletSandboxLaunch? {
            if (data == null) return null
            val pathList = data.getStringArrayList(NappletHostContract.EXTRA_PATHS) ?: return null
            val hashList = data.getStringArrayList(NappletHostContract.EXTRA_HASHES) ?: return null
            val profile = HostProfile.fromName(data.getString(NappletHostContract.EXTRA_HOST_PROFILE))
            val webxdc = NappletHostContract.webxdcOf(data)
            // Only a PAPER launch may carry a page of its own; anything else serves its listed files.
            val paper = NappletHostContract.paperOf(data)?.takeIf { profile == HostProfile.PAPER }
            // A webxdc app's one file is its archive, and a front page's is its page: each its own extra.
            if (pathList.size != hashList.size || (pathList.isEmpty() && webxdc == null && paper == null)) return null
            val author = data.getString(NappletHostContract.EXTRA_AUTHOR).orEmpty()
            val launchToken = data.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()
            if (author.isEmpty() || launchToken.isEmpty()) return null
            val requires = data.getStringArrayList(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
            val launch =
                NappletLaunch(
                    identity = NappletIdentity(author, data.getString(NappletHostContract.EXTRA_IDENTIFIER).orEmpty()),
                    profile = profile,
                    // Domain-object presence is the NIP-5D availability signal; the broker still enforces the
                    // authoritative set minted into the launch token in the main process.
                    declared = profile.declaredCapabilities(requires),
                    requires = requires,
                    title = data.getString(NappletHostContract.EXTRA_TITLE).orEmpty(),
                    paths = pathList.indices.map { PathTag(pathList[it], hashList[it]) },
                    servers = data.getStringArrayList(NappletHostContract.EXTRA_SERVERS) ?: emptyList(),
                    webxdc = webxdc,
                    paper = paper,
                )
            return NappletSandboxLaunch(
                launch = launch,
                launchToken = launchToken,
                useTor = data.getBoolean(NappletHostContract.EXTRA_USE_TOR, true),
                proxyPort = data.getInt(NappletHostContract.EXTRA_PROXY_PORT, -1),
                themeType = data.getString(NappletHostContract.EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" },
                webViewProfile = data.getString(NappletHostContract.EXTRA_WEBVIEW_PROFILE),
                capabilityLabels = data.getStringArrayList(NappletHostContract.EXTRA_CAP_LABELS) ?: emptyList(),
            )
        }
    }
}
