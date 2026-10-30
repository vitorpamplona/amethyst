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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
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
 * A checked napplet or nSite launch: everything a host needs to load it and broker for it, decided
 * on the trusted side from a signed event and nothing the page says later. Android's launcher turns
 * it into its sandbox's launch parameters; the desktop opens a window from it.
 *
 * - [fromManifest]: a NIP-5D napplet. A valid, signed manifest whose required domains Amethyst
 *   brokers, with exactly one `/index.html` artifact matching its `x` tag. [declared] is what its
 *   manifest asks for.
 * - [fromSite]: a NIP-5A nSite. Every path its event lists; [declared] is the website set NIP-07
 *   needs, behind the consent broker.
 *
 * [servers] are the event's, backed by the author's own Blossom list (kind:10063) when it is cached.
 * Every blob is checked against its sha256, so a wrong or extra server can't inject anything.
 */
class NappletLaunch(
    val identity: NappletIdentity,
    val profile: HostProfile,
    val declared: Set<NappletCapability>,
    /** The NAP domains the manifest exposes, as the sandbox gets them; empty for a site. */
    val requires: List<String>,
    val title: String,
    val paths: List<PathTag>,
    val servers: List<String>,
    /** Set for a webxdc app ([HostProfile.WEBXDC]): its archive, and who is running it. */
    val webxdc: WebxdcLaunch? = null,
) {
    /** Exact NAP domain names the prelude projects onto `window.napplet`; absence is the availability signal. */
    val declaredDomains: List<String> = declared.map { it.name.lowercase() }.distinct()

    /**
     * This launch, run inside a note as its own instance [instanceId]: it keeps what the user granted
     * the app, and its questions wait for the user to open it ([NappletIdentity.inline]).
     */
    fun inline(instanceId: String): NappletLaunch = NappletLaunch(identity.copy(inline = true, instanceId = instanceId), profile, declared, requires, title, paths, servers, webxdc)

    companion object {
        /**
         * A NIP-5D napplet from its [manifest], refused unless it is signed by [authorPubKey] (when
         * given) and named [identifier] (when given).
         */
        fun fromManifest(
            manifest: NappletManifest,
            authorPubKey: HexKey? = null,
            identifier: String? = null,
        ): Result<NappletLaunch> =
            runCatching {
                val event = manifest as? Event ?: error("Not a manifest event")
                require(event.verify()) { "The manifest's signature does not verify" }
                require(authorPubKey == null || event.pubKey == authorPubKey) { "The manifest is not by its author" }
                require(manifest.isValid()) { "Invalid NIP-5D manifest" }
                val name = (manifest as? NamedNappletEvent)?.identifier() ?: ""
                require(identifier == null || identifier == name) { "Manifest identifier mismatch" }
                val unsupported = manifest.unsupportedRequired()
                require(unsupported.isEmpty()) { "Unavailable required NAP domains: $unsupported" }
                val paths = listOfNotNull(manifest.artifactFile())
                val artifact =
                    requireNotNull(NappletArtifactPolicy.verifiedArtifactHash(paths, manifest.artifact())) {
                        "NIP-5D requires one self-contained /index.html with a valid artifact hash matching its x tag."
                    }
                val requires = manifest.exposedDomains()
                NappletLaunch(
                    identity =
                        NappletIdentity(
                            authorPubKey = event.pubKey,
                            identifier = name,
                            aggregateHash = artifact,
                            manifestKind = event.kind,
                            manifestEventId = event.id,
                        ),
                    profile = HostProfile.NAPPLET,
                    declared = HostProfile.NAPPLET.declaredCapabilities(requires),
                    requires = requires,
                    title = manifest.title()?.ifBlank { null } ?: name.ifBlank { "Napplet" },
                    paths = paths,
                    servers = withAuthorServers(manifest.servers(), event.pubKey),
                )
            }

        /** A NIP-5A nSite ([RootSiteEvent] or [NamedSiteEvent]), in the website posture. */
        fun fromSite(event: Event): Result<NappletLaunch> =
            runCatching {
                require(event.verify()) { "The site's signature does not verify" }
                val identifier: String
                val paths: List<PathTag>
                val servers: List<String>
                val title: String?
                when (event) {
                    is RootSiteEvent -> {
                        identifier = ""
                        paths = event.paths()
                        servers = event.servers()
                        title = event.title()
                    }
                    is NamedSiteEvent -> {
                        identifier = event.identifier()
                        paths = event.paths()
                        servers = event.servers()
                        title = event.title()
                    }
                    else -> throw IllegalArgumentException("Not a NIP-5A site")
                }
                require(paths.isNotEmpty()) { "The site lists no files" }
                NappletLaunch(
                    identity = NappletIdentity(authorPubKey = event.pubKey, identifier = identifier, aggregateHash = null),
                    profile = HostProfile.WEBSITE,
                    declared = HostProfile.WEBSITE.declaredCapabilities(emptyList()),
                    requires = emptyList(),
                    title = title?.ifBlank { null } ?: identifier.ifBlank { "nsite" },
                    paths = paths,
                    servers = withAuthorServers(servers, event.pubKey),
                )
            }

        /**
         * A webxdc [app] attached to post [postId] by [poster], run by the user [selfPubKey], shown to
         * the app as [selfName]. Its origin, and so its storage, is the poster's app instance
         * ([WebxdcApp.instance]): another author reusing the same `webxdc` id gets an origin of its own.
         */
        fun fromWebxdc(
            app: WebxdcApp,
            postId: HexKey,
            poster: HexKey,
            selfPubKey: HexKey,
            selfName: String,
        ): NappletLaunch =
            NappletLaunch(
                identity =
                    NappletIdentity(
                        authorPubKey = poster,
                        identifier = WEBXDC_IDENTIFIER_PREFIX + app.instance(postId),
                        aggregateHash = app.hash,
                    ),
                profile = HostProfile.WEBXDC,
                declared = HostProfile.WEBXDC.declaredCapabilities(emptyList()),
                requires = emptyList(),
                title =
                    app.title ?: app.url
                        .substringBefore('?')
                        .substringAfterLast('/')
                        .ifBlank { "webxdc" },
                paths = emptyList(),
                servers = withAuthorServers(emptyList(), poster),
                webxdc =
                    WebxdcLaunch(
                        url = app.url,
                        hash = app.hash,
                        selfAddr = WebxdcApp.selfAddr(selfPubKey, poster, app.instance(postId)),
                        selfName = selfName,
                        isAppSender = poster == selfPubKey,
                    ),
            )

        /** Marks a webxdc instance in [NappletIdentity.identifier], apart from any napplet or site name. */
        const val WEBXDC_IDENTIFIER_PREFIX = "webxdc:"

        private fun withAuthorServers(
            servers: List<String>,
            author: HexKey,
        ): List<String> {
            val authorServers =
                runCatching {
                    (LocalCache.getAddressableNoteIfExists(BlossomServersEvent.createAddressTag(author))?.event as? BlossomServersEvent)?.servers()
                }.getOrNull().orEmpty()
            return (servers + authorServers).distinct()
        }
    }
}

/** A webxdc launch's archive and the facts its `webxdc.js` gives the app. */
class WebxdcLaunch(
    val url: String,
    /** The archive's sha256; null when the post gives none, and it runs as served. */
    val hash: HexKey?,
    /** The user as the app sees them: a pseudonym of its own ([WebxdcApp.selfAddr]), never their key. */
    val selfAddr: String,
    val selfName: String,
    /** Whether the user posted the app. */
    val isAppSender: Boolean,
)
