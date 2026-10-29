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
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletArtifactPolicy
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.exposedDomains
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.amethyst.commons.napplet.unsupportedRequired
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

/**
 * Everything a desktop napplet or nSite window needs, checked the way Android's `NappletLauncher`
 * checks it.
 *
 * A [HostProfile.NAPPLET] is a signed, valid NIP-5D manifest whose required domains this host
 * brokers, with exactly one `/index.html` artifact. A [HostProfile.WEBSITE] is a signed NIP-5A site:
 * every path it lists, served at its own origin, with NIP-07 behind the consent broker. [declared] is
 * the security decision: the capabilities the broker may grant this launch, from the manifest or the
 * posture and nothing the page says later.
 */
class DesktopNappletLaunch(
    val identity: NappletIdentity,
    val declared: Set<NappletCapability>,
    val title: String,
    val paths: List<PathTag>,
    val servers: List<String>,
    /** The account that opened it: the window keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    val profile: HostProfile = HostProfile.NAPPLET,
    /**
     * Whether a website's traffic and blobs go through Tor, from the user's choice for this site. A
     * napplet has no network of its own; its blobs follow the app's Tor setting.
     */
    val useTor: Boolean = false,
) {
    /** Exact NAP domain names the prelude projects onto `window.napplet`; absence is the availability signal. */
    val declaredDomains: List<String> = declared.map { it.name.lowercase() }.distinct()

    /** The same launch on the other network route, to reopen a site after the user switches it. */
    fun withUseTor(useTor: Boolean) = DesktopNappletLaunch(identity, declared, title, paths, servers, accountPubKey, profile, useTor)

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
                    servers = (manifest.servers() + authorServers(event.pubKey)).distinct(),
                    accountPubKey = accountPubKey,
                )
            }

        /**
         * An nSite ([RootSiteEvent] or [NamedSiteEvent]), opened in the website posture. [useTor] is
         * where its traffic goes; the caller resolves it from the user's choice for this site.
         */
        fun fromSite(
            event: Event,
            accountPubKey: HexKey,
            useTor: Boolean,
        ): Result<DesktopNappletLaunch> =
            runCatching {
                require(event.verify()) { "The site's signature does not verify" }
                val (identifier, paths, servers, title) =
                    when (event) {
                        is RootSiteEvent -> SiteFields("", event.paths(), event.servers(), event.title())
                        is NamedSiteEvent -> SiteFields(event.identifier(), event.paths(), event.servers(), event.title())
                        else -> throw IllegalArgumentException("Not a NIP-5A site")
                    }
                require(paths.isNotEmpty()) { "The site lists no files" }
                DesktopNappletLaunch(
                    identity =
                        NappletIdentity(
                            authorPubKey = event.pubKey,
                            identifier = identifier,
                            aggregateHash = null,
                        ),
                    declared = HostProfile.WEBSITE.declaredCapabilities(emptyList()),
                    title = title?.ifBlank { null } ?: identifier.ifBlank { "nsite" },
                    paths = paths,
                    servers = (servers + authorServers(event.pubKey)).distinct(),
                    accountPubKey = accountPubKey,
                    profile = HostProfile.WEBSITE,
                    useTor = useTor,
                )
            }

        private data class SiteFields(
            val identifier: String,
            val paths: List<PathTag>,
            val servers: List<String>,
            val title: String?,
        )

        // The author's own Blossom list (kind:10063), when cached, backs up the event's servers. Every
        // blob is checked against its sha256, so an extra server can't inject anything.
        private fun authorServers(author: HexKey): List<String> =
            (LocalCache.getAddressableNoteIfExists(BlossomServersEvent.createAddressTag(author))?.event as? BlossomServersEvent)
                ?.servers()
                .orEmpty()
    }
}
