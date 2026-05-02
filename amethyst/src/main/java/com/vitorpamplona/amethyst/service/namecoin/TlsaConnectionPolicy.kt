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
 * Decorates a relay [OkHttpClient] with TLSA certificate pinning when the
 * relay is a `.bit` host that has been resolved through [BitRelayResolver]
 * and whose Namecoin record carries a `tls` field.
 *
 * For non-`.bit` URLs, or `.bit` URLs whose Namecoin record has no `tls`
 * field, [decorate] returns the input client unchanged so non-Namecoin
 * traffic is not affected.
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
    ): OkHttpClient {
        if (!BitRelayResolver.isBitRelay(url)) return base
        val host = hostOf(url.url) ?: return base
        val records =
            try {
                runBlocking(Dispatchers.IO) {
                    resolver.cachedTlsaFor(host)
                }
            } catch (t: Throwable) {
                Log.w("TlsaConnectionPolicy") {
                    "TLSA cache lookup failed for $host: ${t.message}; falling back to system trust"
                }
                return base
            }

        if (records == null) {
            // The host has not been resolved yet. The URL rewriter runs in
            // OkHttpWebSocket.connect() before the OkHttpClient factory is
            // consulted, so by the time we get here the cache should be
            // populated. If it isn't, that means the rewriter failed (e.g.
            // ElectrumX unreachable) and the rewriter has already decided
            // to fall through to the original `.bit` URL. There's nothing
            // sensible for us to pin against in that state.
            return base
        }
        if (records.isEmpty()) {
            // Resolved successfully but the publisher chose not to ship
            // TLSA records. The current Namecoin ecosystem is mostly in
            // this state today, so the "no TLS pinning" path is the
            // expected behaviour, not a bug.
            Log.d("TlsaConnectionPolicy") {
                "$host resolved but Namecoin record has no `tls` field; using system trust"
            }
            return base
        }

        val systemTm = systemTrustManager() ?: return base
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

        return base
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
