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
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/**
 * TLS 1.3 `CertificateVerify` verification, shared by every JDK/Android-backed
 * [CertificateValidator].
 *
 * Chain policy and CertificateVerify are separate questions, and the split
 * matters: how a validator decides it likes a certificate (a trust store, a
 * pinned fingerprint) is policy, but proving the peer holds the matching
 * private key is not optional under any policy. A validator that pinned a
 * fingerprint and skipped this would accept anyone who could copy a public
 * certificate off the wire.
 */
internal object CertificateVerifySignature {
    fun verify(
        publicKey: PublicKey,
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    ) {
        // RFC 8446 §4.4.3 — the signed content is:
        //   64 spaces || "TLS 1.3, server CertificateVerify" || 0x00 || transcript_hash
        val context = "TLS 1.3, server CertificateVerify".encodeToByteArray()
        val signedData = ByteArray(64 + context.size + 1 + transcriptHash.size)
        for (i in 0 until 64) signedData[i] = 0x20
        context.copyInto(signedData, 64)
        signedData[64 + context.size] = 0x00
        transcriptHash.copyInto(signedData, 64 + context.size + 1)

        val sig = jcaSignatureFor(signatureAlgorithm)
        sig.initVerify(publicKey)
        sig.update(signedData)
        if (!sig.verify(signature)) {
            throw QuicCodecException("CertificateVerify signature did not verify")
        }
    }

    private fun jcaSignatureFor(algorithm: Int): Signature =
        when (algorithm) {
            TlsConstants.SIG_ECDSA_SECP256R1_SHA256 -> {
                Signature.getInstance("SHA256withECDSA")
            }

            TlsConstants.SIG_ECDSA_SECP384R1_SHA384 -> {
                Signature.getInstance("SHA384withECDSA")
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA256 -> {
                rsaPss("SHA-256", 32)
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA384 -> {
                rsaPss("SHA-384", 48)
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA512 -> {
                rsaPss("SHA-512", 64)
            }

            TlsConstants.SIG_ED25519 -> {
                try {
                    // JCA "Ed25519" was added to Android Conscrypt in API 33.
                    // On API 26–32 (our minSdk floor) this throws — surface
                    // it as a clean QuicCodecException so the read loop maps
                    // to CONNECTION_CLOSE rather than crashing the parser.
                    Signature.getInstance("Ed25519")
                } catch (_: NoSuchAlgorithmException) {
                    throw QuicCodecException(
                        "Ed25519 not supported on this platform " +
                            "(requires Android API 33+ or a JDK with the EdDSA provider)",
                    )
                }
            }

            // Audit-4 #2: rsa_pkcs1_* schemes are forbidden in CertificateVerify
            // by RFC 8446 §4.2.3 (only allowed in CertificateRequest for
            // legacy compat). Accepting them allowed a server to sign with
            // weaker PKCS#1 v1.5 instead of RSA-PSS.
            else -> {
                throw QuicCodecException("unsupported signature algorithm 0x${algorithm.toString(16)}")
            }
        }

    private fun rsaPss(
        digest: String,
        saltLen: Int,
    ): Signature {
        val sig = Signature.getInstance("RSASSA-PSS")
        sig.setParameter(PSSParameterSpec(digest, "MGF1", MGF1ParameterSpec(digest), saltLen, 1))
        return sig
    }
}
