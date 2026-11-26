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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

/**
 * [NappletHttp] on Ktor, for the platforms without OkHttp (iOS). [clientFor] gives the client of a
 * route: on iOS the app's Darwin clients ([com.vitorpamplona.amethyst.commons.service.http.DualKtorClients]),
 * whose Tor route fails every request while Tor has no port, so a Tor fetch never goes out direct.
 *
 * It holds to what [OkHttpNappletHttp] does, as far as Ktor lets it:
 * - the cap: a declared length over [NappletHttpRequest.maxBytes] is refused unread, and a body with
 *   no length is read one byte past the cap, then refused;
 * - the timeout covers the whole call, redirects included;
 * - redirects are followed here, hop by hop (at most [MAX_FOLLOWED_REDIRECTS], like OkHttp), never by
 *   the client, so a [NappletHttpRequest.publicOnly] request checks every hop and refuses an https
 *   page that redirects to plain http;
 * - no cookies: the requests carry no `Cookie` or credentials (the clients install no cookie
 *   plugin, and Ktor's Darwin engine gives its session no cookie storage).
 *
 * What it can't match: Ktor (NSURLSession under it) has no resolver hook and no view of the address
 * it connected to. So a [NappletHttpRequest.publicOnly] direct request asks [resolvesPublic] about
 * the host before it goes, and the session then resolves the name again itself: a name that
 * resolves to a public address for the check and to a private one for the connection (DNS
 * rebinding) gets through, where OkHttp would refuse it. An IP literal is checked exactly. Over Tor
 * the exit resolves names, so only IP literals are checked.
 */
class KtorNappletHttp(
    private val clientFor: (viaTor: Boolean) -> HttpClient,
    /** Whether every address [host] resolves to is public ([NappletPublicAddress]); false when it doesn't resolve. */
    private val resolvesPublic: suspend (host: String) -> Boolean,
) : NappletHttp {
    override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
        val client = noRedirects(clientFor(request.viaTor))
        return try {
            withTimeoutOrNull(request.timeoutMs) { follow(client, request) }
                ?: throw NappletHttpTimeoutException("${request.url} timed out")
        } catch (e: HttpRequestTimeoutException) {
            throw NappletHttpTimeoutException("${request.url} timed out", e)
        } catch (e: ConnectTimeoutException) {
            throw NappletHttpTimeoutException("${request.url} timed out", e)
        } catch (e: SocketTimeoutException) {
            throw NappletHttpTimeoutException("${request.url} timed out", e)
        }
    }

    private suspend fun follow(
        client: HttpClient,
        request: NappletHttpRequest,
    ): NappletHttpResponse {
        var url = Url(request.url)
        var hop = 0
        while (true) {
            if (request.publicOnly) checkPublic(url, request.viaTor)
            val response = client.prepareGet(url).execute { read(it, request.maxBytes) }
            val location = response.location
            if (!request.followRedirects || !response.isRedirect || location == null || hop >= MAX_FOLLOWED_REDIRECTS) return response
            val next = URLBuilder(url).takeFrom(location).build()
            if (request.publicOnly && url.protocol.name == "https" && next.protocol.name != "https") {
                throw NappletHttpBlockedException("${url.host} redirected to plain http.")
            }
            url = next
            hop++
        }
    }

    private suspend fun checkPublic(
        url: Url,
        viaTor: Boolean,
    ) {
        val host = url.host.removePrefix("[").removeSuffix("]")
        val literal = ipLiteralBytes(host)
        val public =
            when {
                literal != null -> NappletPublicAddress.isPublic(literal)
                // The exit resolves it, and can't reach the user's network anyway.
                viaTor -> true
                else -> resolvesPublic(host)
            }
        if (!public) throw NappletHttpBlockedException("$host does not resolve to a public address.")
    }

    private suspend fun read(
        response: HttpResponse,
        maxBytes: Long,
    ): NappletHttpResponse {
        val status = response.status.value
        val location = response.headers[HttpHeaders.Location]
        if (status !in 200..299) return NappletHttpResponse(status, location, null, tooLarge = false, received = 0)
        if ((response.contentLength() ?: -1L) > maxBytes) return NappletHttpResponse(status, location, null, tooLarge = true, received = 0)
        val bytes = response.bodyAsChannel().readBuffer(maxBytes + 1).readByteArray()
        val received = bytes.size.toLong()
        return if (received > maxBytes) {
            NappletHttpResponse(status, location, null, tooLarge = true, received = received)
        } else {
            NappletHttpResponse(status, location, bytes, tooLarge = false, received = received)
        }
    }

    // The route's client with redirects left to [follow]. Derived once per client: the Tor client is
    // rebuilt when its port changes, and a derived copy of a closed one is simply dropped.
    private val derivedLock = KmpLock()
    private val derived = HashMap<HttpClient, HttpClient>()

    private fun noRedirects(base: HttpClient): HttpClient =
        derivedLock.withLock {
            derived[base] ?: run {
                if (derived.size >= MAX_DERIVED_CLIENTS) derived.clear()
                base.config { followRedirects = false }.also { derived[base] = it }
            }
        }

    companion object {
        const val MAX_FOLLOWED_REDIRECTS = 20
        private const val MAX_DERIVED_CLIENTS = 4

        /** [host]'s bytes when it is an IPv4 or IPv6 literal, else null. */
        fun ipLiteralBytes(host: String): ByteArray? = ipv4Bytes(host) ?: ipv6Bytes(host)

        private fun ipv4Bytes(host: String): ByteArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val bytes = ByteArray(4)
            parts.forEachIndexed { i, part ->
                if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
                val value = part.toInt()
                if (value > 255) return null
                bytes[i] = value.toByte()
            }
            return bytes
        }

        private fun ipv6Bytes(host: String): ByteArray? {
            if (':' !in host) return null
            val address = host.substringBefore('%') // a zone id names an interface, not an address
            val halves = address.split("::")
            if (halves.size > 2) return null

            fun groups(part: String): List<String>? = if (part.isEmpty()) emptyList() else part.split(':').takeIf { list -> list.none { it.isEmpty() } }
            val head = groups(halves[0]) ?: return null
            val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()

            // A trailing dotted IPv4 (::ffff:1.2.3.4) is two groups.
            fun expand(
                list: List<String>,
                last: Boolean,
            ): List<Int>? =
                list.flatMapIndexed { i, group ->
                    if (last && i == list.lastIndex && '.' in group) {
                        val v4 = ipv4Bytes(group) ?: return null
                        listOf(((v4[0].toInt() and 0xff) shl 8) or (v4[1].toInt() and 0xff), ((v4[2].toInt() and 0xff) shl 8) or (v4[3].toInt() and 0xff))
                    } else {
                        if (group.length > 4) return null
                        listOf(group.toIntOrNull(16) ?: return null)
                    }
                }
            val headValues = expand(head, last = halves.size == 1) ?: return null
            val tailValues = expand(tail, last = true) ?: return null
            val missing = 8 - headValues.size - tailValues.size
            if (halves.size == 1 && missing != 0) return null
            if (halves.size == 2 && missing < 1) return null
            val all = headValues + List(missing) { 0 } + tailValues
            return ByteArray(16) { i -> (if (i % 2 == 0) all[i / 2] shr 8 else all[i / 2]).toByte() }
        }
    }
}
