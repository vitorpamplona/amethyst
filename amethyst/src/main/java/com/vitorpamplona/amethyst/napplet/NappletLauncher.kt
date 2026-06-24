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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.napplethost.NappletHostActivity
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

/**
 * Opens a napplet/nsite in the sandboxed [NappletHostActivity] (the `:napplet` process). Only
 * the verified manifest data the host needs to render and broker for the applet is passed —
 * the declared `path → hash` map, the Blossom servers, the applet's identity coordinate, the
 * declared capabilities, and a display title. No account state crosses into the sandbox process.
 */
object NappletLauncher {
    /** Opens a NIP-5D napplet, forwarding its declared capabilities to the broker. */
    fun launch(
        context: Context,
        manifest: NappletManifest,
        authorPubKey: HexKey,
        identifier: String,
    ) = launch(
        context = context,
        paths = manifest.paths(),
        servers = manifest.servers(),
        authorPubKey = authorPubKey,
        identifier = identifier,
        aggregateHash = manifest.declaredAggregateHash() ?: manifest.computeAggregateHash(),
        title = manifest.title() ?: identifier.ifBlank { "Napplet" },
        requires = manifest.requires(),
    )

    /**
     * Opens any NIP-5A static site (nsite or napplet). [requires] is empty for a plain nsite —
     * the broker then refuses every capability, so the site renders as inert static content.
     */
    fun launch(
        context: Context,
        paths: List<PathTag>,
        servers: List<String>,
        authorPubKey: HexKey,
        identifier: String,
        aggregateHash: HexKey?,
        title: String,
        requires: List<String>,
        // nSites open in "website mode": a NIP-07 window.nostr provider + normal network. The broker
        // then grants the IDENTITY + RELAY capabilities NIP-07 needs (consent-gated), regardless of the
        // (empty) manifest `requires`. Napplets pass false and keep their declared-only, locked sandbox.
        websiteMode: Boolean = false,
    ) {
        val params = buildLaunchParams(context, paths, servers, authorPubKey, identifier, aggregateHash, title, requires, websiteMode)
        val intent =
            Intent(context, NappletHostActivity::class.java).apply {
                putExtras(params)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    /**
     * Builds the verified launch parameters — minting the launch token, augmenting the Blossom server
     * set, resolving the per-site Tor choice and capability labels — as a [Bundle] keyed by the
     * [NappletHostContract] EXTRA_* names. Used both for the activity intent (above) and for the
     * embedded [com.vitorpamplona.amethyst.napplethost.NappletHostService] session (carried over
     * Messenger), so the two host paths launch from identical, main-process-minted parameters.
     */
    fun buildLaunchParams(
        context: Context,
        paths: List<PathTag>,
        servers: List<String>,
        authorPubKey: HexKey,
        identifier: String,
        aggregateHash: HexKey?,
        title: String,
        requires: List<String>,
        websiteMode: Boolean,
    ): Bundle {
        val proxyPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1

        // Augment the manifest's servers with the author's published Blossom list (kind:10063), if
        // we already hold it, so a blob the manifest's servers dropped can still be fetched. The
        // host re-verifies every blob's sha256, so a wrong/extra server can never inject content.
        val authorBlossomServers =
            runCatching {
                (LocalCache.getAddressableNoteIfExists(BlossomServersEvent.createAddressTag(authorPubKey))?.event as? BlossomServersEvent)?.servers()
            }.getOrNull().orEmpty()
        val allServers = (servers + authorBlossomServers).distinct()

        // Mint the launch token in the (trusted) main process: the broker resolves the sandbox's
        // requests back to THIS identity + declared set, regardless of anything the sandbox sends.
        val identity = NappletIdentity(authorPubKey = authorPubKey, identifier = identifier, aggregateHash = aggregateHash)
        val declared =
            if (websiteMode) {
                setOf(NappletCapability.IDENTITY, NappletCapability.RELAY)
            } else {
                resolveRequiredCapabilities(requires).capabilities.toSet()
            }
        val launchToken = NappletLaunchRegistry.register(identity, declared)

        // Resolve the per-site network choice (Tor default; a site can be opted out to the open web).
        // Locked napplets always keep Tor for their blob fetches — only nSites expose the toggle.
        NappletNetworkRegistry.init(context.applicationContext)
        val useTor = if (websiteMode) NappletNetworkRegistry.useTor(identity.coordinate) else true

        // Resolve capability labels here (the app has the resources) so the sandbox module needs none.
        val capLabels = declared.map { context.getString(it.labelRes()) }

        return Bundle().apply {
            putStringArrayList(NappletHostContract.EXTRA_PATHS, ArrayList(paths.map { it.path }))
            putStringArrayList(NappletHostContract.EXTRA_HASHES, ArrayList(paths.map { it.hash }))
            putStringArrayList(NappletHostContract.EXTRA_SERVERS, ArrayList(allServers))
            putString(NappletHostContract.EXTRA_AUTHOR, authorPubKey)
            putString(NappletHostContract.EXTRA_IDENTIFIER, identifier)
            putString(NappletHostContract.EXTRA_AGGREGATE_HASH, aggregateHash)
            putString(NappletHostContract.EXTRA_TITLE, title)
            putStringArrayList(NappletHostContract.EXTRA_REQUIRES, ArrayList(requires))
            putStringArrayList(NappletHostContract.EXTRA_CAP_LABELS, ArrayList(capLabels))
            putString(NappletHostContract.EXTRA_LAUNCH_TOKEN, launchToken)
            putInt(NappletHostContract.EXTRA_PROXY_PORT, proxyPort)
            putBoolean(NappletHostContract.EXTRA_WEBSITE_MODE, websiteMode)
            putBoolean(NappletHostContract.EXTRA_USE_TOR, useTor)
        }
    }
}
