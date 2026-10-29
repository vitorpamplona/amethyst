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

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.NappletArtifactPolicy
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.exposedDomains
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.amethyst.commons.napplet.unsupportedRequired
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

/**
 * Everything a desktop napplet window needs, checked the way Android's `NappletLauncher` checks it:
 * a signed, valid NIP-5D manifest whose required domains this host brokers, with exactly one
 * `/index.html` artifact. [declared] is the security decision: the capabilities the broker may
 * grant this launch, from the manifest and nothing the page says later.
 */
class DesktopNappletLaunch(
    val identity: NappletIdentity,
    val declared: Set<NappletCapability>,
    val title: String,
    val paths: List<PathTag>,
    val servers: List<String>,
    /** The account that opened it: the window keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
) {
    /** Exact NAP domain names the prelude projects onto `window.napplet`; absence is the availability signal. */
    val declaredDomains: List<String> = declared.map { it.name.lowercase() }

    companion object {
        fun from(
            manifest: NappletManifest,
            accountPubKey: HexKey,
        ): Result<DesktopNappletLaunch> =
            runCatching {
                val event = manifest as? Event ?: error("Not a manifest event")
                require(event.verify()) { "The manifest's signature does not verify" }
                require(manifest.isValid()) { "Invalid NIP-5D manifest" }
                val identifier = (manifest as? NamedNappletEvent)?.identifier() ?: ""
                val unsupported = manifest.unsupportedRequired()
                require(unsupported.isEmpty()) { "Needs what Amethyst does not offer: ${unsupported.joinToString()}" }
                val paths = listOfNotNull(manifest.artifactFile())
                val artifact =
                    requireNotNull(NappletArtifactPolicy.verifiedArtifactHash(paths, manifest.artifact())) {
                        "A napplet is one self-contained /index.html with a valid artifact hash"
                    }

                // The author's own Blossom list (kind:10063), when cached, backs up the manifest's
                // servers. Every blob is checked against its sha256, so an extra server can't inject.
                val authorServers =
                    (LocalCache.getAddressableNoteIfExists(BlossomServersEvent.createAddressTag(event.pubKey))?.event as? BlossomServersEvent)
                        ?.servers()
                        .orEmpty()

                DesktopNappletLaunch(
                    identity =
                        NappletIdentity(
                            authorPubKey = event.pubKey,
                            identifier = identifier,
                            aggregateHash = artifact,
                            manifestKind = event.kind,
                            manifestEventId = event.id,
                        ),
                    declared = resolveRequiredCapabilities(manifest.exposedDomains()).capabilities,
                    title = manifest.title()?.ifBlank { null } ?: identifier.ifBlank { "Napplet" },
                    paths = paths,
                    servers = (manifest.servers() + authorServers).distinct(),
                    accountPubKey = accountPubKey,
                )
            }
    }
}
