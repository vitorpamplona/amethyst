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
package com.vitorpamplona.quic.tls

import com.vitorpamplona.quic.QuicCodecException
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * A certificate validator that trusts exactly the endpoints whose leaf
 * certificate matches a configured SHA-256 fingerprint.
 *
 * This is the "self-signed endpoint" case, and it is a real one: Marmot's raw
 * QUIC binding says a preview endpoint or broker may be self-signed and that a
 * client MAY pin it by exact DER or by SHA-256 fingerprint through local
 * configuration. The alternative in use until now was accepting every
 * certificate, which is not a weaker trust model — it is no trust model, and
 * anyone on the path can be the broker.
 *
 * Pinning replaces the chain and the hostname check, and only those. It does
 * NOT replace proof of possession: the peer still has to sign the TLS
 * transcript with the pinned certificate's private key ([verifySignature]),
 * so copying a public certificate off the wire buys an attacker nothing.
 *
 * The pin is over the leaf's DER bytes exactly as the peer sent them, which is
 * what `openssl x509 -outform der | sha256sum` prints and what a broker
 * operator can therefore publish alongside its address. An expired or
 * not-yet-valid pinned certificate is still refused: pinning says WHICH
 * certificate, not that any certificate will do forever.
 */
class PinnedCertificateValidator(
    pins: Collection<ByteArray>,
) : CertificateValidator {
    private val pins: List<ByteArray> =
        pins.map {
            require(it.size == SHA256_LEN) { "a certificate pin is a $SHA256_LEN-byte SHA-256 digest, got ${it.size}" }
            it.copyOf()
        }

    private var leafCert: X509Certificate? = null

    init {
        require(this.pins.isNotEmpty()) { "a pinned validator needs at least one pin" }
    }

    override fun validateChain(
        chain: List<ByteArray>,
        expectedHost: String,
    ) {
        if (chain.isEmpty()) throw QuicCodecException("server sent empty certificate chain")

        // Only the leaf is pinned. The rest of the chain is not consulted at
        // all — with a pin there is no path to build and no issuer to trust,
        // and a self-signed endpoint has no chain to speak of.
        val leafDer = chain[0]
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(leafDer)
        if (pins.none { it.contentEqualsConstantTime(fingerprint) }) {
            throw QuicCodecException("certificate does not match any pinned SHA-256 fingerprint")
        }

        val parsed =
            try {
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(leafDer)) as X509Certificate
            } catch (t: Throwable) {
                throw QuicCodecException("pinned certificate parse failed: ${t.message}", t)
            }
        try {
            parsed.checkValidity()
        } catch (t: Throwable) {
            throw QuicCodecException("pinned certificate is not currently valid: ${t.message}", t)
        }

        // No hostname verification: the pin already names one certificate, and
        // a self-signed preview endpoint reached by IP literal typically has no
        // name to check against. `expectedHost` stays in the signature because
        // the interface is shared with trust-store validation.
        leafCert = parsed
    }

    override fun verifySignature(
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    ) {
        val cert = leafCert ?: throw QuicCodecException("CertificateVerify before Certificate")
        CertificateVerifySignature.verify(cert.publicKey, signatureAlgorithm, signature, transcriptHash)
    }

    companion object {
        const val SHA256_LEN = 32

        /**
         * Pin by SHA-256 fingerprint, written as hex.
         *
         * Colons and whitespace are accepted and ignored so the output of
         * `openssl x509 -fingerprint -sha256` can be pasted in as-is.
         */
        fun ofSha256Hex(vararg fingerprints: String): PinnedCertificateValidator = PinnedCertificateValidator(fingerprints.map { parseHexDigest(it) })

        /**
         * Pin by the certificate's exact DER bytes.
         *
         * The DER is reduced to its own SHA-256 immediately: "exact DER" and
         * "its fingerprint" are the same pin, and keeping one representation
         * means one comparison path to get right.
         */
        fun ofDer(vararg certificates: ByteArray): PinnedCertificateValidator =
            PinnedCertificateValidator(
                certificates.map { MessageDigest.getInstance("SHA-256").digest(it) },
            )

        private fun parseHexDigest(raw: String): ByteArray {
            val cleaned = raw.filterNot { it == ':' || it.isWhitespace() }
            require(cleaned.length == SHA256_LEN * 2) {
                "a SHA-256 fingerprint is ${SHA256_LEN * 2} hex characters, got ${cleaned.length}"
            }
            return ByteArray(SHA256_LEN) { i ->
                val hi = Character.digit(cleaned[i * 2], 16)
                val lo = Character.digit(cleaned[i * 2 + 1], 16)
                require(hi >= 0 && lo >= 0) { "a SHA-256 fingerprint must be hex" }
                ((hi shl 4) or lo).toByte()
            }
        }
    }
}
