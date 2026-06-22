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
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder

/**
 * Fetches a resource URL on an applet's behalf — the applet has no direct network
 * (`connect-src 'none'`), so every `resource.bytes` is brokered through here. Handles `data:`,
 * `https:`, and `blossom:` URLs; blossom blobs are content-addressed and **sha256-verified** before
 * returning, so a wrong server can never substitute the blob.
 *
 * Owns a Tor-routed [OkHttpClient], cached and rebuilt only when the active Tor port ([torPort])
 * changes. Built per account (so it reads the right Blossom server list); consent is enforced by the
 * broker before [fetch] ever runs.
 */
class NappletResourceFetcher(
    private val account: Account,
    private val torPort: () -> Int,
) {
    // Reused blob HTTP client, keyed by the active Tor port (see client()).
    private var cachedHttp: Pair<Int, OkHttpClient>? = null

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
                // nostr: resolution (event → bytes) is unspecified for resource.bytes; left as a follow-up.
                else -> null
            }
        }

    /**
     * Tor-routed OkHttp client for host-side blob fetches (the applet has no direct network).
     * Cached and reused for connection pooling; rebuilt only when the Tor proxy port changes.
     */
    @Synchronized
    private fun client(): OkHttpClient {
        val port = torPort()
        cachedHttp?.let { (cachedPort, client) -> if (cachedPort == port) return client }
        val client =
            if (port > 0) {
                OkHttpClient.Builder().proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))).build()
            } else {
                OkHttpClient()
            }
        cachedHttp = port to client
        return client
    }

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
}
