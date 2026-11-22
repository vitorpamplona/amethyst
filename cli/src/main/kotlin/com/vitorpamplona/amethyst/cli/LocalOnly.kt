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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * `AMY_LOCAL_ONLY=1`: amy reaches only hosts on this machine or its private network, whatever a
 * relay list, a default set or a link names. Test harnesses set it so a run can never send its
 * throwaway accounts' events to the public relays Amethyst ships as defaults.
 *
 * A host is allowed when it is named in [Context.BOOTSTRAP_ENV] or `AMY_ALLOW_HOSTS` (comma
 * separated), or when every address it resolves to is loopback, link-local, private (RFC 1918,
 * IPv6 ULA) or carrier-grade NAT (100.64.0.0/10). Anything else fails before the connection
 * opens, with an IOException naming the host, and is reported once per host on stderr.
 *
 * Every OkHttp client amy builds goes through [httpClientBuilder], so the rule covers relay
 * sockets and plain HTTP (Blossom, NIP-05, zaps, mints) alike.
 */
object LocalOnly {
    const val ENV = "AMY_LOCAL_ONLY"
    const val ALLOW_ENV = "AMY_ALLOW_HOSTS"

    val enabled: Boolean by lazy { System.getenv(ENV)?.trim()?.lowercase() in setOf("1", "true", "yes") }

    private val namedHosts: Set<String> by lazy {
        listOfNotNull(System.getenv(Context.BOOTSTRAP_ENV), System.getenv(ALLOW_ENV))
            .flatMap { it.split(',') }
            .mapNotNull { hostOf(it.trim()) }
            .toSet()
    }

    private val verdicts = ConcurrentHashMap<String, Boolean>()

    /** `wss://relay.example:443/path` or a bare `relay.example` → `relay.example`. */
    internal fun hostOf(entry: String): String? {
        if (entry.isEmpty()) return null
        val afterScheme = entry.substringAfter("://", entry)
        val authority = afterScheme.substringBefore('/').substringAfterLast('@')
        val host =
            if (authority.startsWith("[")) {
                authority.substringAfter('[').substringBefore(']')
            } else {
                authority.substringBefore(':')
            }
        return host.lowercase().ifEmpty { null }
    }

    internal fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isAnyLocalAddress) return true
        val bytes = address.address
        return if (address is Inet4Address) {
            // 100.64.0.0/10, carrier-grade NAT: never the public internet.
            bytes[0].toInt() and 0xff == 100 && (bytes[1].toInt() and 0xc0) == 64
        } else {
            // fc00::/7, unique local addresses.
            (bytes[0].toInt() and 0xfe) == 0xfc
        }
    }

    /** Whether [host] may be reached while [enabled]. Resolves it once per process. */
    fun allows(host: String): Boolean {
        val key = host.lowercase()
        if (key == "localhost" || key in namedHosts) return true
        return verdicts.getOrPut(key) {
            val addresses = runCatching { InetAddress.getAllByName(key) }.getOrNull()
            val allowed = !addresses.isNullOrEmpty() && addresses.all { isLocalAddress(it) }
            if (!allowed) System.err.println("[amy] $ENV: refusing $key (not on this machine or its private network)")
            allowed
        }
    }

    /** [allows] for a relay url; always true while [enabled] is off. */
    fun allowsRelay(relay: NormalizedRelayUrl): Boolean = !enabled || hostOf(relay.url)?.let { allows(it) } ?: false

    private val interceptor =
        Interceptor { chain ->
            val host = chain.request().url.host
            if (!allows(host)) throw IOException("$ENV is set: refusing to reach $host")
            chain.proceed(chain.request())
        }

    /**
     * The one way amy makes an OkHttp client: a plain builder, plus the [ENV] guard when it is on.
     * Callers add their own timeouts, socket factory or dispatcher on top.
     */
    fun httpClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder().also { if (enabled) it.addInterceptor(interceptor) }

    /** [httpClientBuilder] with nothing else set. */
    fun httpClient(): OkHttpClient = httpClientBuilder().build()
}
