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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.AiaStapledPubkeyExtractor
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier.Result
import com.vitorpamplona.quartz.utils.Log
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

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

        val stapledSpkis = collectStapledSpkis(chain)
        val result = TlsaVerifier.verifyWithStapled(view, records, stapledSpkis, ::sha512)
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

    /**
     * Walk the served chain extracting any AIA-stapled SPKIs (ncgencert
     * convention). The Domain CA cert in an ncgencert chain carries the AIA
     * Parent CA's SPKI in two redundant places — the issuer-DN serialNumber
     * attribute as a JSON blob, and the AIA caIssuers URL as a `pubb64=`
     * query parameter. Both should produce the same bytes; we collect both
     * to be robust to operator variation.
     *
     * Duplicates are deduplicated so [TlsaVerifier.verifyWithStapled] doesn't
     * waste cycles re-hashing the same blob.
     */
    private fun collectStapledSpkis(chain: Array<X509Certificate>): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        for (cert in chain) {
            // 1) Issuer DN serialNumber attribute. RFC 2253 form preserves the
            //    "\0A" escapes ncgencert needs us to unescape.
            val issuerDn =
                runCatching { cert.issuerX500Principal.getName(X500Principal.RFC2253) }.getOrNull()
            AiaStapledPubkeyExtractor.extractFromIssuerDn(issuerDn)?.let { out.addUnique(it) }

            // 2) AIA caIssuers URLs. We deliberately don't fetch them — the
            //    `pubb64=` query parameter contains everything we need.
            for (url in extractAiaCaIssuersUrls(cert)) {
                AiaStapledPubkeyExtractor.extractFromAiaUrl(url)?.let { out.addUnique(it) }
            }
        }
        return out
    }

    private fun MutableList<ByteArray>.addUnique(value: ByteArray) {
        if (none { it.contentEquals(value) }) add(value)
    }

    /**
     * Extract the CA-Issuers URLs from an X.509 cert's Authority Information
     * Access extension (RFC 5280 §4.2.2.1, OID 1.3.6.1.5.5.7.1.1).
     *
     * On JVM there's no zero-cost way to do this through `X509Certificate`'s
     * public API — the extension comes back as a raw DER OctetString. We
     * parse just enough of the DER structure to recover the URLs, falling
     * back to a simple ASCII scan for `http`/`https` scheme prefixes if the
     * structured parse misses anything (defensive: ncgencert URLs are pure
     * ASCII inside an IA5String).
     */
    private fun extractAiaCaIssuersUrls(cert: X509Certificate): List<String> {
        val raw = runCatching { cert.getExtensionValue("1.3.6.1.5.5.7.1.1") }.getOrNull() ?: return emptyList()
        // raw is an OctetString wrapping the actual SEQUENCE. Strip the outer
        // OCTET STRING tag (04) + length, then scan the inner bytes for the
        // CA-Issuers OID (1.3.6.1.5.5.7.48.2 = 06 08 2B 06 01 05 05 07 30 02)
        // followed by a uniformResourceIdentifier IA5String (context tag
        // [6] = 86). This is a deliberately permissive scan; we don't need
        // a full DER parser since we only care about extracting URL strings.
        val urls = mutableListOf<String>()
        val caIssuersOid = byteArrayOf(0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02)
        var i = 0
        while (i + caIssuersOid.size < raw.size) {
            if (raw.regionMatches(i, caIssuersOid)) {
                // Right after the OID: skip the SEQUENCE wrapper (if any) and
                // look for the [6] context-specific tag (0x86) for URI.
                var j = i + caIssuersOid.size
                // Walk forward up to ~8 bytes scanning for 0x86 context tag.
                val scanLimit = (j + 8).coerceAtMost(raw.size)
                while (j < scanLimit) {
                    if (raw[j] == 0x86.toByte()) {
                        // Next byte is the length (we only care about short-form
                        // lengths; URLs are well under 128 bytes).
                        if (j + 1 < raw.size) {
                            val len = raw[j + 1].toInt() and 0xFF
                            if (len < 128 && j + 2 + len <= raw.size) {
                                val urlBytes = raw.copyOfRange(j + 2, j + 2 + len)
                                urls.add(urlBytes.decodeToString())
                            }
                        }
                        break
                    }
                    j++
                }
                i = j + 1
            } else {
                i++
            }
        }
        return urls
    }

    private fun ByteArray.regionMatches(
        offset: Int,
        other: ByteArray,
    ): Boolean {
        if (offset + other.size > this.size) return false
        for (k in other.indices) if (this[offset + k] != other[k]) return false
        return true
    }
}
