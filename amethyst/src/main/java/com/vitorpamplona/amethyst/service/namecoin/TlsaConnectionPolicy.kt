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
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Decorates a relay [OkHttpClient] with the per-`.bit`-host wiring needed
 * to actually open a connection from a Namecoin-resolved record:
 *
 *  1. **DNS** — if the `.bit` host has a cached `ip` from its Namecoin
 *     record, install a [BitRelayDns] adapter so `OkHttp` can resolve
 *     the host instead of failing against ICANN DNS. This is the only
 *     way `.bit` records whose `relay` field is itself a `.bit` URL
 *     (e.g. `wss://relay.testls.bit/`) can be reached.
 *  2. **TLS pinning** — if the Namecoin record carries a `tls` field,
 *     install a [TlsaTrustManager] so the cert chain MUST validate
 *     against the published TLSA records (RFC 6698 / `ifa-0001`).
 *
 * Both steps reuse the same [BitRelayResolver] cache populated when the
 * URL rewriter ran; no extra ElectrumX call. For non-`.bit` URLs the
 * input client is returned unchanged so non-Namecoin traffic is not
 * affected.
 *
 * Reuses existing infrastructure:
 *   - [BitRelayResolver.cachedTlsaFor] reads the TLSA records that were
 *     parsed during the relay-URL resolution. No extra ElectrumX call.
 *   - [TlsaTrustManager] applies the [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier]
 *     spec policy. The policy itself lives in `commonMain` so iOS / desktop
 *     can reuse it later.
 *   - The platform default `X509TrustManager` is reused for the `PKIX-*`
 *     usages so the existing system CA store stays the source of truth for
 *     PKIX validation.
 */
class TlsaConnectionPolicy(
    private val resolver: BitRelayResolver,
) {
    /**
     * Possibly return a client whose TLS handshake is constrained by the
     * Namecoin `tls` field. The original [base] is returned unchanged when
     * there is nothing to enforce.
     */
    fun decorate(
        url: NormalizedRelayUrl,
        base: OkHttpClient,
    ): OkHttpClient = decorate(url.url, base)

    /**
     * Raw-URL form, used by the HTTP path (NIP-11 RelayInfo, etc.) where
     * the caller has a [String] URL but no [NormalizedRelayUrl] wrapper.
     */
    fun decorate(
        rawUrl: String,
        base: OkHttpClient,
    ): OkHttpClient {
        if (!BitRelayResolver.isBitRelayUrl(rawUrl)) return base
        val host = hostOf(rawUrl) ?: return base

        // Step 1: install BitRelayDns so the .bit host can be resolved at
        // all. We always do this for .bit hosts, even when there are no
        // TLSA records, because OkHttp would otherwise hit ICANN DNS and
        // fail before any TLS handshake could happen.
        var client =
            base
                .newBuilder()
                .dns(BitRelayDns(resolver, fallback = base.dns))
                .build()

        // Step 2: if the publisher shipped TLSA records, layer the trust
        // manager on top. We swallow cache-read failures to avoid taking
        // down non-pinned connections; same shape as before.
        val records =
            try {
                runBlocking(Dispatchers.IO) {
                    resolver.cachedTlsaFor(host)
                }
            } catch (t: Throwable) {
                Log.w("TlsaConnectionPolicy") {
                    "TLSA cache lookup failed for $host: ${t.message}; falling back to system trust"
                }
                return client
            }

        if (records == null) {
            // The host has not been resolved yet. The URL rewriter runs in
            // OkHttpWebSocket.connect() before the OkHttpClient factory is
            // consulted, so by the time we get here the cache should be
            // populated. If it isn't, that means the rewriter failed (e.g.
            // ElectrumX unreachable) and the rewriter has already decided
            // to fall through to the original `.bit` URL. There's nothing
            // sensible for us to pin against in that state.
            return client
        }
        if (records.isEmpty()) {
            // Resolved successfully but the publisher chose not to ship
            // TLSA records. The current Namecoin ecosystem is mostly in
            // this state today, so the "no TLS pinning" path is the
            // expected behaviour, not a bug.
            Log.d("TlsaConnectionPolicy") {
                "$host resolved but Namecoin record has no `tls` field; using system trust"
            }
            return client
        }

        val systemTm = systemTrustManager() ?: return client
        val tm = TlsaTrustManager(host, records, systemTm)

        // Use a TLSv1.2-preferring SSLContext to match the rest of the
        // Namecoin path (ElectrumXClient.buildPinnedSslFactory does the same
        // thing for the same OEM-Conscrypt reasons).
        val ssl =
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (_: Exception) {
                SSLContext.getInstance("TLS")
            }
        ssl.init(null, arrayOf<javax.net.ssl.TrustManager>(tm), SecureRandom())

        return client
            .newBuilder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .build()
    }

    private fun hostOf(rawUrl: String): String? =
        runCatching {
            URI(rawUrl.trim()).host?.lowercase()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    /**
     * Returns the platform default [X509TrustManager], or null if the
     * platform's TrustManagerFactory unexpectedly omits one (e.g. Xiaomi
     * stripped TM stack — same fallback shape we use elsewhere).
     */
    private fun systemTrustManager(): X509TrustManager? {
        val tmf =
            try {
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            } catch (_: Exception) {
                return null
            }
        return try {
            tmf.init(null as KeyStore?)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
