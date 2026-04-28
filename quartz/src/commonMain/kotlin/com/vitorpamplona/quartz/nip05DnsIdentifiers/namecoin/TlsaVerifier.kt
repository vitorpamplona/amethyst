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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaMatchingType
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaRecord
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaSelector
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.TlsaUsage
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Spec-compliant matching for Namecoin/DNS TLSA records (RFC 6698 / Namecoin
 * `ifa-0001` "tls" item).
 *
 * This is the policy core used by the `.bit` relay TLS path: given a chain of
 * peer certificates produced by the platform TLS handshake and a list of TLSA
 * records pulled from the Namecoin `d/<name>` value, decide whether the chain
 * is acceptable.
 *
 * The verifier is intentionally free of platform crypto APIs so it can live
 * in `commonMain`. Callers supply:
 *   - the cert DER (`encoded`) for selector 0 (full cert) usages
 *   - the SubjectPublicKeyInfo DER for selector 1 (SPKI) usages
 *   - a `sha512` callback (since `commonMain` only ships `sha256`)
 *
 * The verifier only enforces the TLSA association data match. Path
 * construction and PKIX validation are still expected to be done by the
 * platform [javax.net.ssl.X509TrustManager] for the `PKIX-TA` and `PKIX-EE`
 * usages; for the DANE usages the platform PKIX validation can be skipped
 * (DANE is the trust anchor by itself).
 */
object TlsaVerifier {
    /** A single peer-supplied certificate, exposed in the two encodings TLSA needs. */
    data class CertView(
        /** Position in the chain. 0 = leaf (end-entity), n = root anchor. */
        val index: Int,
        /** Full DER encoding (`X509Certificate.encoded`). */
        val encodedDer: ByteArray,
        /** SubjectPublicKeyInfo DER (`X509Certificate.publicKey.encoded` on JVM). */
        val spkiDer: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CertView) return false
            if (index != other.index) return false
            if (!encodedDer.contentEquals(other.encodedDer)) return false
            return spkiDer.contentEquals(other.spkiDer)
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + encodedDer.contentHashCode()
            result = 31 * result + spkiDer.contentHashCode()
            return result
        }
    }

    /** Outcome of [verify]. */
    sealed class Result {
        /** All records were unusable shapes (UNKNOWN selector / matching type / empty data). */
        data object NoUsableRecords : Result()

        /**
         * At least one record matched the chain. [matchedRecord] is the one
         * that succeeded; [matchedCertIndex] is the position in the chain that
         * matched (0 = leaf for EE usages; > 0 typically for TA usages).
         *
         * [requiresPkixValidation] is `true` for `PKIX-TA` and `PKIX-EE`
         * usages — the caller MUST also run a normal PKIX trust check. For
         * DANE-* usages it's `false`: the TLSA record itself is the trust
         * anchor and platform PKIX may be bypassed.
         */
        data class Match(
            val matchedRecord: TlsaRecord,
            val matchedCertIndex: Int,
            val requiresPkixValidation: Boolean,
        ) : Result()

        /**
         * Records were present and well-formed, but none of them matched the
         * presented chain. The caller MUST reject the connection.
         */
        data class NoMatch(
            val triedRecords: Int,
        ) : Result()
    }

    /**
     * Verify [chain] against [records] per RFC 6698 §2.1.
     *
     * Iteration order: records are tried in declaration order; the first
     * match short-circuits.
     *
     * @param chain   peer certificate chain, leaf-first
     * @param records TLSA records from the Namecoin `tls` field
     * @param sha512  platform SHA-512 implementation (commonMain only ships sha256)
     */
    fun verify(
        chain: List<CertView>,
        records: List<TlsaRecord>,
        sha512: (ByteArray) -> ByteArray,
    ): Result = verifyWithStapled(chain, records, emptyList(), sha512)

    /**
     * Like [verify], but also accepts a list of extra SPKI DER blobs to try
     * for `*-TA` (trust-anchor) usages. Used to honour the ncgencert AIA
     * stapling convention: the AIA Parent CA is not actually sent over the
     * wire, but its SPKI is stapled into the served chain's metadata so
     * verifiers can synthetically add a one-step-higher trust anchor.
     *
     * Stapled SPKIs only participate in `PKIX-TA` / `DANE-TA` matches —
     * end-entity matches must still come from a real cert in the served
     * chain. Stapled SPKIs participate ONLY in the `SUBJECT_PUBLIC_KEY_INFO`
     * selector path; full-cert matches are skipped because we don't have the
     * full DER for a stapled-only key.
     *
     * The chain is checked first; only when no record matches the served
     * chain do stapled candidates get a try. This preserves the existing
     * matching semantics for pre-stapling deployments.
     *
     * @param chain          peer certificate chain, leaf-first
     * @param records        TLSA records from the Namecoin `tls` field
     * @param stapledSpkis   extra SPKI DER blobs (e.g. ncgencert AIA staples)
     * @param sha512         platform SHA-512 implementation
     */
    fun verifyWithStapled(
        chain: List<CertView>,
        records: List<TlsaRecord>,
        stapledSpkis: List<ByteArray>,
        sha512: (ByteArray) -> ByteArray,
    ): Result {
        if (records.isEmpty()) return Result.NoUsableRecords
        var usable = 0
        for (record in records) {
            val expected = decodeAssociationData(record) ?: continue
            // Skip records whose policy fields we can't honour.
            if (record.usage == TlsaUsage.UNKNOWN) continue
            if (record.selector == TlsaSelector.UNKNOWN) continue
            if (record.matchingType == TlsaMatchingType.UNKNOWN) continue

            usable++
            val candidates = certsForUsage(chain, record.usage)
            for (cert in candidates) {
                val source =
                    when (record.selector) {
                        TlsaSelector.FULL_CERT -> cert.encodedDer
                        TlsaSelector.SUBJECT_PUBLIC_KEY_INFO -> cert.spkiDer
                        TlsaSelector.UNKNOWN -> continue
                    }
                val actual =
                    when (record.matchingType) {
                        TlsaMatchingType.EXACT -> source
                        TlsaMatchingType.SHA_256 -> sha256(source)
                        TlsaMatchingType.SHA_512 -> sha512(source)
                        TlsaMatchingType.UNKNOWN -> continue
                    }
                if (actual.contentEquals(expected)) {
                    return Result.Match(
                        matchedRecord = record,
                        matchedCertIndex = cert.index,
                        requiresPkixValidation = isPkixUsage(record.usage),
                    )
                }
            }
        }

        // No match against the served chain. Try stapled SPKIs as a fallback.
        // Only TA usages are eligible (stapled keys represent the AIA Parent
        // CA, which is structurally a trust anchor); only the SPKI selector
        // is eligible (we don't have full-cert DER for stapled keys); and
        // only SHA-256 / SHA-512 / EXACT matching applies the usual way.
        if (stapledSpkis.isNotEmpty()) {
            for (record in records) {
                if (record.usage != TlsaUsage.PKIX_TA && record.usage != TlsaUsage.DANE_TA) continue
                if (record.selector != TlsaSelector.SUBJECT_PUBLIC_KEY_INFO) continue
                if (record.matchingType == TlsaMatchingType.UNKNOWN) continue
                val expected = decodeAssociationData(record) ?: continue
                for (spki in stapledSpkis) {
                    val actual =
                        when (record.matchingType) {
                            TlsaMatchingType.EXACT -> spki
                            TlsaMatchingType.SHA_256 -> sha256(spki)
                            TlsaMatchingType.SHA_512 -> sha512(spki)
                            TlsaMatchingType.UNKNOWN -> continue
                        }
                    if (actual.contentEquals(expected)) {
                        return Result.Match(
                            matchedRecord = record,
                            // -1 signals "matched a stapled key, not a position in the served chain".
                            matchedCertIndex = -1,
                            requiresPkixValidation = isPkixUsage(record.usage),
                        )
                    }
                }
            }
        }

        return if (usable == 0) Result.NoUsableRecords else Result.NoMatch(usable)
    }

    /**
     * The cert(s) in [chain] that a given [usage] is allowed to match against,
     * per RFC 6698 §2.1.1:
     *
     *   - Usage 0 (PKIX-TA) and 2 (DANE-TA): any cert in the chain (leaf or
     *     intermediate or root). We deliberately allow leaf as well so that
     *     small operators who publish a single record for their leaf still
     *     work.
     *   - Usage 1 (PKIX-EE) and 3 (DANE-EE): the leaf only.
     */
    private fun certsForUsage(
        chain: List<CertView>,
        usage: TlsaUsage,
    ): List<CertView> =
        when (usage) {
            TlsaUsage.PKIX_TA, TlsaUsage.DANE_TA -> chain
            TlsaUsage.PKIX_EE, TlsaUsage.DANE_EE -> chain.take(1)
            TlsaUsage.UNKNOWN -> emptyList()
        }

    private fun isPkixUsage(usage: TlsaUsage): Boolean = usage == TlsaUsage.PKIX_TA || usage == TlsaUsage.PKIX_EE

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeAssociationData(record: TlsaRecord): ByteArray? {
        // Strip ALL whitespace, not just the trim() ends, because some
        // publishers (and clipboard pastes that flow through `nano`/`vim`
        // wrap) embed newlines or stray spaces. RFC 4648 explicitly says
        // "any amount of line feed character(s) MAY then be inserted".
        val raw = record.associationDataBase64.filterNot { it.isWhitespace() }
        if (raw.isEmpty()) return null
        return runCatching { Base64.decode(raw) }
            .recoverCatching {
                // Some publishers strip padding; tolerate that.
                val padded =
                    when (val mod = raw.length % 4) {
                        0 -> raw
                        else -> raw + "=".repeat(4 - mod)
                    }
                Base64.decode(padded)
            }.recoverCatching {
                // Some publishers use the "MIME"/url-safe alphabet. Try a
                // last-ditch normalisation so we don't reject otherwise-valid
                // records.
                Base64.decode(
                    raw
                        .replace('-', '+')
                        .replace('_', '/')
                        .let {
                            val mod = it.length % 4
                            if (mod == 0) it else it + "=".repeat(4 - mod)
                        },
                )
            }.getOrNull()
    }
}
