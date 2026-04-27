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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier.Result
import com.vitorpamplona.quartz.utils.Log
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * `X509TrustManager` that enforces a Namecoin `tls` (TLSA) record set against
 * the peer chain presented during the TLS handshake of a `.bit` relay
 * connection.
 *
 * This is the Amethyst (JVM) glue around the Quartz [TlsaVerifier]:
 *
 *   - The platform-agnostic spec policy (RFC 6698 §2.1 selectors / matching
 *     types / usages) lives entirely in [TlsaVerifier].
 *   - This class only handles the JVM-specific bits: pulling DER from
 *     [X509Certificate], computing SHA-512 via [MessageDigest], and
 *     deciding whether to delegate the path-validation half of `PKIX-TA` /
 *     `PKIX-EE` usages to the platform default trust manager.
 *
 * Behaviour for `checkServerTrusted`:
 *
 *   1. If the TLSA record set has at least one well-formed `DANE-TA` /
 *      `DANE-EE` record that matches a cert in the chain → accept.
 *      DANE replaces the PKIX trust anchor: we deliberately do NOT delegate
 *      to the platform default trust manager. Self-signed or "wrong CA"
 *      certs published by the domain owner are the whole point of DANE.
 *
 *   2. If the matching record is `PKIX-TA` / `PKIX-EE` → also require that
 *      the platform default trust manager accepts the chain. PKIX usages
 *      are constraints layered on top of normal CA validation, not
 *      replacements for it.
 *
 *   3. If no record matched → reject with [CertificateException]. The fact
 *      that the relay is running on a `.bit` domain that publishes TLSA
 *      records is taken as opt-in to mandatory TLSA validation; falling
 *      back to plain PKIX would let a MITM with a public-CA cert defeat
 *      the whole feature.
 *
 *   4. If the record set is empty (no `tls` field on the Namecoin record),
 *      this trust manager should NOT be installed in the first place; the
 *      caller is responsible for falling back to the platform default
 *      trust manager. This is enforced by [TlsaConnectionPolicy].
 *
 * The trust manager is intentionally per-connection: instances are not
 * shared, and the pinned record set is captured at construction time. The
 * cache invalidation lives in [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver].
 */
class TlsaTrustManager(
    /** The `.bit` host the records were pulled from (logged for diagnostics). */
    private val bitHost: String,
    /** TLSA records from the Namecoin record. Must not be empty. */
    private val records: List<NamecoinNameResolver.TlsaRecord>,
    /** Platform default trust manager, used to validate `PKIX-*` matches. */
    private val systemTrustManager: X509TrustManager,
) : X509TrustManager {
    init {
        require(records.isNotEmpty()) {
            "TlsaTrustManager requires at least one TLSA record; install the platform default for empty sets"
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        // We only run on the WebSocket client side; client auth never happens.
        // Forward to the system trust manager so other code paths that share
        // a wrapped factory (none today) keep behaving sensibly.
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        if (chain.isEmpty()) throw CertificateException("Empty server certificate chain for $bitHost")

        val view =
            chain.mapIndexed { idx, cert ->
                TlsaVerifier.CertView(
                    index = idx,
                    encodedDer = cert.encoded,
                    spkiDer = cert.publicKey.encoded,
                )
            }

        val result = TlsaVerifier.verify(view, records, ::sha512)
        when (result) {
            is Result.Match -> {
                if (result.requiresPkixValidation) {
                    // PKIX-* matched the published cert/SPKI; we still need
                    // a normal CA-validated path before trusting it.
                    systemTrustManager.checkServerTrusted(chain, authType)
                }
                Log.d("TlsaTrustManager") {
                    val rec = result.matchedRecord
                    "TLSA match for $bitHost: usage=${rec.usage} selector=${rec.selector} " +
                        "matchingType=${rec.matchingType} chainIndex=${result.matchedCertIndex} " +
                        (if (result.requiresPkixValidation) "(also passed PKIX)" else "(DANE-only)")
                }
            }

            is Result.NoMatch -> {
                throw CertificateException(
                    "TLSA validation failed for $bitHost: chain matched none of " +
                        "${result.triedRecords} record(s) from the Namecoin `tls` field",
                )
            }

            Result.NoUsableRecords -> {
                // Defensive: caller should not have installed this trust
                // manager if every record is unusable, but if it did, fall
                // back to platform PKIX so we don't hard-break the connection
                // on a record-format quirk we don't yet understand.
                Log.w("TlsaTrustManager") {
                    "All ${records.size} TLSA record(s) for $bitHost have UNKNOWN policy fields; " +
                        "falling back to platform PKIX validation."
                }
                systemTrustManager.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers

    private fun sha512(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(input)
}
