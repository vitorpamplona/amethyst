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
package com.vitorpamplona.amethyst.napplet.gateways

import android.util.Base64
import com.vitorpamplona.amethyst.commons.napplet.NappletResource
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

/**
 * Fetches a resource URL on an applet's behalf — the applet has no direct network
 * (`connect-src 'none'`), so every `resource.bytes` is brokered through here. Handles `data:`,
 * `https:`, and `blossom:` URLs; blossom blobs are content-addressed and **sha256-verified** before
 * returning, so a wrong server can never substitute the blob.
 *
 * Network goes through the app-wide [OkHttpClient] supplied by [httpClient] (the shared
 * [com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager]). Reusing it — rather than
 * standing up a private client — means napplet blob fetches inherit the same Tor routing,
 * passive `Onion-Location` discovery + `.onion` rewriting, local Blossom cache redirect,
 * connection pool and DNS as every other HTTP role. Built per account (so it reads the right
 * Blossom server list); consent is enforced by the broker before [fetch] ever runs.
 */
class NappletResourceFetcher(
    private val account: Account,
    private val httpClient: () -> OkHttpClient,
) {
    /** Fetches an https/data/blossom resource, or null if unsupported/unavailable. */
    suspend fun fetch(url: String): NappletResource? =
        withContext(Dispatchers.IO) {
            when {
                url.startsWith("data:") -> decodeDataUrl(url)
                url.startsWith("https://") -> {
                    runCatching {
                        client()
                            .newCall(
                                Request
                                    .Builder()
                                    .url(url)
                                    .get()
                                    .build(),
                            ).execute()
                            .use { r ->
                                if (!r.isSuccessful) return@withContext null
                                val body = r.body.bytes()
                                val type = r.header("Content-Type") ?: "application/octet-stream"
                                NappletResource(body, type)
                            }
                    }.getOrNull()
                }
                url.startsWith("blossom:") -> fetchBlossom(url)
                url.startsWith("nostr:") -> resolveNostr(url)
                else -> null
            }
        }

    /**
     * Resolves a `nostr:` URI (NIP-19) to the referenced event and returns its JSON. An `nembed`
     * carries the event inline; `note`/`nevent`/`naddr` resolve from the local cache, falling back to
     * a bounded relay fetch; `npub`/`nprofile` resolve the author's kind-0 metadata event. Returns
     * the event JSON as `application/json`, or null when nothing resolves.
     */
    private suspend fun resolveNostr(url: String): NappletResource? {
        val entity = Nip19Parser.uriToRoute(url)?.entity ?: return null
        val event =
            when (entity) {
                is NEmbed -> entity.event
                is NNote -> resolveEvent(entity.hex)
                is NEvent -> resolveEvent(entity.hex)
                is NAddress -> resolveAddress(entity.address())
                is NPub -> resolveReplaceable(0, entity.hex)
                is NProfile -> resolveReplaceable(0, entity.hex)
                else -> null
            } ?: return null
        return NappletResource(event.toJson().encodeToByteArray(), "application/json")
    }

    /** A non-replaceable event by id: local cache first, then a bounded relay fetch. */
    private suspend fun resolveEvent(id: String): Event? =
        account.cache.getNoteIfExists(id)?.event
            ?: fetchOne(Filter(ids = listOf(id)))

    /** A parameterized-replaceable event by address: local cache first, then a bounded relay fetch. */
    private suspend fun resolveAddress(address: Address): Event? =
        account.cache.getAddressableNoteIfExists(address)?.event
            ?: fetchOne(Filter(kinds = listOf(address.kind), authors = listOf(address.pubKeyHex), tags = mapOf("d" to listOf(address.dTag))))

    /** A replaceable event (e.g. kind-0 metadata) by author: local cache first, then a bounded relay fetch. */
    private suspend fun resolveReplaceable(
        kind: Int,
        author: String,
    ): Event? =
        account.cache
            .getAddressableNoteIfExists(Address(kind, author, ""))
            ?.event
            ?: fetchOne(Filter(kinds = listOf(kind), authors = listOf(author)))

    /** Bounded relay fetch for [filter]; returns the newest matching event, or null. */
    private suspend fun fetchOne(filter: Filter): Event? {
        val relays = account.homeRelays.flow.value
        if (relays.isEmpty()) return null
        return runCatching {
            account.client.fetchAll(filters = relays.associateWith { listOf(filter) }, timeoutMs = NOSTR_FETCH_TIMEOUT_MS)
        }.getOrDefault(emptyList())
            .maxByOrNull { it.createdAt }
    }

    /**
     * The app-wide OkHttp client for host-side blob fetches (the applet has no direct network).
     * The shared manager already routes through Tor when active, captures `Onion-Location` and
     * rewrites `.onion`s, bridges the local Blossom cache, and pools connections — so there is no
     * private client to build or cache here.
     */
    private fun client(): OkHttpClient = httpClient()

    /**
     * Fetches a `blossom:<sha256>` (or `blossom://<sha256>`) blob from the user's Blossom servers
     * (kind:10063), verifying the sha256 before returning — content-addressed, so a wrong server
     * can never substitute the blob. Returns null for a malformed hash or if no server serves it.
     */
    private fun fetchBlossom(url: String): NappletResource? {
        val hash =
            url
                .removePrefix("blossom://")
                .removePrefix("blossom:")
                .substringBefore('/')
                .substringBefore('?')
                .trim()
                .lowercase()
        if (!hash.matches(Regex("^[0-9a-f]{64}$"))) return null

        val servers =
            account.blossomServers
                .getBlossomServersList()
                ?.servers()
                .orEmpty()
        val client = client()
        for (candidate in StaticSiteResolver.candidateUrls(servers, hash)) {
            val bytes =
                runCatching {
                    client
                        .newCall(
                            Request
                                .Builder()
                                .url(candidate)
                                .get()
                                .build(),
                        ).execute()
                        .use { r ->
                            if (r.isSuccessful) r.body.bytes() else null
                        }
                }.getOrNull() ?: continue
            if (StaticSiteResolver.verify(bytes, hash)) {
                return NappletResource(bytes, sniffContentType(bytes) ?: "application/octet-stream")
            }
        }
        return null
    }

    /** Parses a `data:[<mediatype>][;base64],<data>` URL into bytes + content type. */
    private fun decodeDataUrl(url: String): NappletResource? {
        val comma = url.indexOf(',')
        if (comma < 0) return null
        val meta = url.substring("data:".length, comma)
        val data = url.substring(comma + 1)
        val isBase64 = meta.endsWith(";base64")
        val contentType = meta.removeSuffix(";base64").ifEmpty { "text/plain" }
        val bytes =
            if (isBase64) {
                runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return null
            } else {
                URLDecoder.decode(data, "UTF-8").encodeToByteArray()
            }
        return NappletResource(bytes, contentType)
    }

    companion object {
        private const val NOSTR_FETCH_TIMEOUT_MS = 8_000L
    }
}
