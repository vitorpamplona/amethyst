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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletResource
import com.vitorpamplona.amethyst.commons.napplet.NappletResourceResult
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpBlockedException
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpRequest
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpTimeoutException
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
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
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches a resource URL on an applet's behalf — the applet has no direct network
 * (`connect-src 'none'`), so every `resource.bytes` is brokered through here. Handles `data:`,
 * `https:`, and `blossom:` URLs; blossom blobs are content-addressed and **sha256-verified** before
 * returning, so a wrong server can never substitute the blob.
 *
 * Network goes through [http]. On Android and Desktop that is the app-wide OkHttp client (the shared
 * `DualHttpClientManager`) behind `OkHttpNappletHttp`.
 * Reusing it — rather than standing up a private client — means napplet blob fetches inherit the
 * same passive `Onion-Location` discovery + `.onion` rewriting, local Blossom cache redirect,
 * connection pool and DNS as every other HTTP role. On iOS it is the app's Darwin clients. Tor-or-clearnet is chosen per request by [useTor] for the calling
 * applet (on Android its network mode: locked napplets are pinned to Tor; nSites follow the user's
 * per-site toggle), so a brokered fetch routes exactly like that applet's own page loads.
 * Built per account (so it reads the right Blossom server list); consent is enforced by the
 * broker before [fetch] ever runs.
 */
class NappletResourceFetcher(
    private val account: Account,
    private val http: NappletHttp,
    private val useTor: suspend (coordinate: String) -> Boolean,
) {
    /** Fetches an https/data/blossom/nostr resource and preserves the NAP-RESOURCE error category. */
    suspend fun fetch(
        url: String,
        coordinate: String,
    ): NappletResourceResult =
        withContext(Dispatchers.IO) {
            when {
                url.startsWith("data:") -> decodeDataUrl(url)
                url.startsWith("nostr:") ->
                    resolveNostr(url)?.let(::success) ?: failure(ERROR_NOT_FOUND, "Nostr resource not found.")
                url.startsWith("https://") || url.startsWith("blossom:") -> {
                    // Route like the applet's own page: locked napplets stay on Tor. Each hop is a
                    // public-only request: no ambient cookies/auth, and DNS validated before it goes.
                    val viaTor = useTor(coordinate)
                    if (url.startsWith("https://")) fetchHttps(url, viaTor) else fetchBlossom(url, viaTor)
                }
                else -> failure(ERROR_UNSUPPORTED_SCHEME, "Unsupported resource URL scheme.")
            }
        }

    private suspend fun fetchHttps(
        url: String,
        viaTor: Boolean,
        expectedHash: String? = null,
    ): NappletResourceResult {
        var current = safeHttpsResourceUrl(url) ?: return failure(ERROR_BLOCKED, "Only credential-free HTTPS URLs are allowed.")
        repeat(MAX_REDIRECTS + 1) { hop ->
            try {
                val response =
                    http.get(
                        NappletHttpRequest(
                            url = current,
                            viaTor = viaTor,
                            maxBytes = MAX_RESOURCE_BYTES.toLong(),
                            timeoutMs = FETCH_TIMEOUT_MS,
                            followRedirects = false,
                            publicOnly = true,
                        ),
                    )
                if (response.isRedirect) {
                    if (hop >= MAX_REDIRECTS) return failure(ERROR_BLOCKED, "Redirect limit exceeded.")
                    val location = response.location ?: return failure(ERROR_NETWORK, "Redirect has no location.")
                    current = resolveResourceRedirect(current, location)?.let(::safeHttpsResourceUrl) ?: return failure(ERROR_BLOCKED, "Redirect left credential-free HTTPS.")
                    return@repeat
                }
                if (response.status == 404) return failure(ERROR_NOT_FOUND)
                if (!response.isSuccessful) return failure(ERROR_NETWORK, "Upstream returned HTTP ${response.status}.")
                val body = response.body?.takeUnless { response.tooLarge } ?: return failure(ERROR_TOO_LARGE)
                if (expectedHash != null && !StaticSiteResolver.verify(body, expectedHash)) {
                    return failure(ERROR_DECODE_FAILED, "Blossom SHA-256 verification failed.")
                }
                return classify(body)
            } catch (e: NappletHttpBlockedException) {
                return failure(ERROR_BLOCKED, e.message)
            } catch (_: NappletHttpTimeoutException) {
                return failure(ERROR_TIMEOUT)
            } catch (e: Exception) {
                // Cancellation is not an upstream failure — do not report it to the
                // napplet as one, and do not keep the request alive past it.
                if (e is CancellationException) throw e
                return failure(ERROR_NETWORK)
            }
        }
        return failure(ERROR_BLOCKED, "Redirect limit exceeded.")
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
        return NappletResource(event.toJson().encodeToByteArray(), MIME_JSON)
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
        return runCatchingCancellable {
            account.client.taggedAs(SubPurpose.ADD_ONS, "Napplet").fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = NOSTR_FETCH_TIMEOUT_MS)
        }.getOrDefault(emptyList())
            .maxByOrNull { it.createdAt }
    }

    /**
     * Fetches a BUD-10 `blossom:<sha256>.<ext>` blob from the user's Blossom servers
     * (kind:10063) over [http], verifying the sha256 before returning — content-addressed, so a
     * wrong server can never substitute the blob. Returns null for a malformed hash or if no server
     * serves it.
     */
    private suspend fun fetchBlossom(
        url: String,
        viaTor: Boolean,
    ): NappletResourceResult {
        val reference = parseBlossomResource(url) ?: return failure(ERROR_INVALID_REQUEST, "Malformed BUD-10 Blossom URI.")
        val hash = reference.sha256
        val authorServers =
            reference.authors.take(10).flatMap { author ->
                (resolveReplaceable(BlossomServersEvent.KIND, author) as? BlossomServersEvent)?.servers().orEmpty()
            }
        val servers =
            (
                reference.servers + authorServers +
                    account.blossomServers
                        .getBlossomServersList()
                        ?.servers()
                        .orEmpty()
            ).map { if ("://" in it) it else "https://$it" }
                .filter(::isSafeHttpsResourceUrl)
                .distinct()
        var sawHashMismatch = false
        for (candidate in StaticSiteResolver.candidateUrls(servers, hash)) {
            when (val fetched = fetchHttps(candidate, viaTor, hash)) {
                is NappletResourceResult.Success -> {
                    if (!StaticSiteResolver.verify(fetched.resource.bytes, hash)) {
                        sawHashMismatch = true
                        continue
                    }
                    return fetched
                }
                is NappletResourceResult.Failure -> {
                    if (fetched.error == ERROR_DECODE_FAILED) sawHashMismatch = true
                    if (fetched.error == ERROR_BLOCKED) return fetched
                }
            }
        }
        if (sawHashMismatch) return failure(ERROR_DECODE_FAILED, "Blossom SHA-256 verification failed.")
        return failure(ERROR_NOT_FOUND, "No Blossom server returned the verified blob.")
    }

    /** Parses a `data:[<mediatype>][;base64],<data>` URL into bytes + content type. */
    private fun decodeDataUrl(url: String): NappletResourceResult {
        val comma = url.indexOf(',')
        if (comma < 0) return failure(ERROR_INVALID_REQUEST, "Malformed data URL.")
        val meta = url.substring("data:".length, comma)
        val data = url.substring(comma + 1)
        if (data.length > MAX_DATA_URL_CHARS) return failure(ERROR_TOO_LARGE)
        val isBase64 = meta.endsWith(";base64")
        val declaredType =
            meta
                .removeSuffix(";base64")
                .substringBefore(';')
                .ifEmpty { MIME_PLAIN_TEXT }
                .lowercase()
        val bytes =
            if (isBase64) {
                // Whitespace is skipped and padding is optional, as Android's Base64.DEFAULT did; any
                // other character outside the alphabet still fails.
                runCatching { decodeDataBase64(data.filterNot { it.isWhitespace() }) }.getOrNull()
                    ?: return failure(ERROR_DECODE_FAILED, "Invalid base64 data URL.")
            } else {
                runCatching { decodeDataPayload(data) }.getOrNull()
                    ?: return failure(ERROR_DECODE_FAILED, "Invalid escaped data URL.")
            }
        if (bytes.size > MAX_RESOURCE_BYTES) return failure(ERROR_TOO_LARGE)
        return classify(bytes, declaredType)
    }

    private fun classify(
        bytes: ByteArray,
        declaredType: String? = null,
    ): NappletResourceResult {
        val type =
            resourceType(bytes, declaredType)
                ?: return failure(ERROR_BLOCKED, "SVG, HTML and XML are not delivered by this runtime.")
        return success(NappletResource(bytes, type))
    }

    private fun success(resource: NappletResource): NappletResourceResult = NappletResourceResult.Success(resource)

    private fun failure(
        error: String,
        message: String? = null,
    ): NappletResourceResult = NappletResourceResult.Failure(error, message)

    companion object {
        /**
         * A BUD-10 `blossom:<sha256>[.<ext>][?…]` reference. The extension is optional, as in
         * [BlossomUri.parse]: the blob is fetched by hash and verified against it, so the extension
         * decides nothing here.
         */
        internal fun parseBlossomResource(url: String): BlossomUri? {
            if (!BLOSSOM_RESOURCE.matches(url)) return null
            return BlossomUri.parse(url)
        }

        /**
         * The MIME type a resource is delivered with, or null when it is refused.
         *
         * Refused: SVG, HTML and XML (which carries XHTML and SVG). Those are the formats a page
         * renders by running what is inside them (`innerHTML`, `<object>`, a `blob:` frame), and a
         * napplet runs under `script-src 'unsafe-inline'`, so an event handler in a fetched file
         * would act with every grant the user gave the napplet. Anything else is data the napplet
         * decodes itself (a glTF model, a gzip archive, a font): it gets the type its magic bytes
         * show, a declared JSON or text type that the bytes bear out, or
         * `application/octet-stream`, never a type that invites rendering.
         */
        internal fun resourceType(
            bytes: ByteArray,
            declaredType: String? = null,
        ): String? {
            if (declaredType in MARKUP_TYPES || looksLikeMarkup(bytes)) return null
            sniffContentType(bytes)?.let { return it }
            return when {
                declaredType == MIME_JSON && isJson(bytes) -> MIME_JSON
                declaredType == MIME_PLAIN_TEXT && isPlainText(bytes) -> MIME_PLAIN_TEXT
                else -> MIME_OCTET_STREAM
            }
        }

        /**
         * SVG anywhere in the first [MIME_PREFIX_BYTES], or a document a browser would sniff as
         * HTML or XML: after a UTF-8 BOM and whitespace, an XML declaration or one of the WHATWG
         * MIME-sniffing HTML openers (`<!doctype html`, `<html`, `<script`, `<!--`, …) followed by
         * a space or `>`.
         */
        internal fun looksLikeMarkup(bytes: ByteArray): Boolean {
            val prefix = bytes.copyOfRange(0, minOf(bytes.size, MIME_PREFIX_BYTES)).decodeToString().lowercase()
            if (prefix.contains("<svg")) return true
            val start = prefix.removePrefix(UTF8_BOM).trimStart(' ', '\t', '\n', '\r', '\u000C')
            if (start.startsWith("<?xml")) return true
            return HTML_OPENERS.any { opener ->
                start.startsWith(opener) && start.length > opener.length && (start[opener.length] == ' ' || start[opener.length] == '>')
            }
        }

        private fun isJson(bytes: ByteArray): Boolean = runCatching { Json.parseToJsonElement(bytes.decodeToString()) }.isSuccess

        private fun isPlainText(bytes: ByteArray): Boolean = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.isSuccess && bytes.none { it == 0.toByte() }

        /** data: percent escapes encode bytes, and '+' is literal (not HTML form encoding). */
        internal fun decodeDataPayload(data: String): ByteArray {
            val output = Buffer()
            var start = 0
            while (start < data.length) {
                val escaped = data.indexOf('%', start)
                val end = if (escaped < 0) data.length else escaped
                output.write(data.substring(start, end).encodeToByteArray())
                if (escaped < 0) break
                require(escaped + 2 < data.length) { "Incomplete data URL escape" }
                val high = data[escaped + 1].digitToIntOrNull(16) ?: error("Invalid data URL escape")
                val low = data[escaped + 2].digitToIntOrNull(16) ?: error("Invalid data URL escape")
                output.writeByte(high * 16 + low)
                start = escaped + 3
            }
            return output.readByteArray()
        }

        internal fun isSafeHttpsResourceUrl(url: String): Boolean = safeHttpsResourceUrl(url) != null

        private const val NOSTR_FETCH_TIMEOUT_MS = 8_000L
        private const val FETCH_TIMEOUT_MS = 30_000L
        private const val MAX_REDIRECTS = 5
        private const val MIME_PREFIX_BYTES = 8 * 1024
        private const val MAX_DATA_URL_CHARS = 24 * 1024 * 1024
        const val MAX_RESOURCE_BYTES = 10 * 1024 * 1024
        private const val ERROR_INVALID_REQUEST = "invalid-request"
        private const val ERROR_NOT_FOUND = "not-found"
        private const val ERROR_BLOCKED = "blocked-by-policy"
        private const val ERROR_TIMEOUT = "timeout"
        private const val ERROR_TOO_LARGE = "too-large"
        private const val ERROR_UNSUPPORTED_SCHEME = "unsupported-scheme"
        private const val ERROR_DECODE_FAILED = "decode-failed"
        private const val ERROR_NETWORK = "network-error"
        private const val MIME_JSON = "application/json"
        private const val MIME_PLAIN_TEXT = "text/plain"
        private const val MIME_OCTET_STREAM = "application/octet-stream"
        private const val UTF8_BOM = "\uFEFF"
        private val BLOSSOM_RESOURCE = Regex("^blossom:[0-9a-fA-F]{64}(?:\\.[a-zA-Z0-9]+)?(?:\\?[^#]*)?$")
        private val MARKUP_TYPES =
            setOf(
                "image/svg+xml",
                "text/html",
                "application/xhtml+xml",
                "text/xml",
                "application/xml",
            )

        // The WHATWG MIME Sniffing "HTML" patterns (section 7.1), lowercased.
        private val HTML_OPENERS =
            listOf(
                "<!doctype html",
                "<html",
                "<head",
                "<script",
                "<iframe",
                "<h1",
                "<div",
                "<font",
                "<table",
                "<a",
                "<style",
                "<title",
                "<b",
                "<body",
                "<br",
                "<p",
                "<!--",
            )
    }
}
