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
package com.vitorpamplona.quartz.experimental.zapstore.identityProof

import com.vitorpamplona.quartz.mls.crypto.Ed25519
import java.io.ByteArrayInputStream
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

/** `SEQUENCE { SEQUENCE { OID 1.3.101.112 }, BIT STRING (32 bytes) }`: an Ed25519 SubjectPublicKeyInfo. */
private val ED25519_SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

private const val ED25519_SPKI_SIZE = 44

/** Go's `rsa.PSSSaltLengthEqualsHash` for SHA-256. */
private val PSS_SHA256 = PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)

internal actual fun verifyCertificateSignature(
    certificateDer: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): IdentityProofVerification {
    val publicKey =
        try {
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certificateDer)).publicKey
        } catch (_: Exception) {
            return IdentityProofVerification.BAD_CERTIFICATE
        }

    val verified =
        when {
            publicKey.algorithm == "RSA" -> verifyRsa(publicKey, message, signature)
            publicKey.algorithm == "EC" -> verifyJca("SHA256withECDSA", publicKey, message, signature)
            isEd25519(publicKey) -> verifyEd25519(publicKey, message, signature)
            else -> return IdentityProofVerification.UNSUPPORTED_ALGORITHM
        }
    return if (verified) IdentityProofVerification.VALID else IdentityProofVerification.BAD_SIGNATURE
}

/** PKCS#1 v1.5 first (what zsp signs with), then PSS (which zsp also accepts). */
private fun verifyRsa(
    publicKey: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean = verifyJca("SHA256withRSA", publicKey, message, signature) || verifyRsaPss(publicKey, message, signature)

/** The JDK names PSS `RSASSA-PSS` (parameters required); Android's Conscrypt `SHA256withRSA/PSS`. */
private fun verifyRsaPss(
    publicKey: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean {
    for (algorithm in arrayOf("RSASSA-PSS", "SHA256withRSA/PSS")) {
        val verifier =
            try {
                Signature.getInstance(algorithm)
            } catch (_: NoSuchAlgorithmException) {
                continue
            }
        try {
            verifier.setParameter(PSS_SHA256)
        } catch (_: Exception) {
            // `SHA256withRSA/PSS` already defaults to MGF1-SHA-256 and a 32-byte salt.
            if (algorithm == "RSASSA-PSS") continue
        }
        return runVerifier(verifier, publicKey, message, signature)
    }
    return false
}

private fun verifyJca(
    algorithm: String,
    publicKey: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean =
    try {
        runVerifier(Signature.getInstance(algorithm), publicKey, message, signature)
    } catch (_: GeneralSecurityException) {
        false
    }

private fun runVerifier(
    verifier: Signature,
    publicKey: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean =
    try {
        verifier.initVerify(publicKey)
        verifier.update(message)
        verifier.verify(signature)
    } catch (_: Exception) {
        // A malformed signature (wrong length, bad DER) throws instead of returning false.
        false
    }

/** By encoding, not by name: the JDK calls the key `EdDSA`, other providers `Ed25519`. */
private fun isEd25519(publicKey: PublicKey): Boolean {
    val spki = publicKey.encoded ?: return false
    if (spki.size != ED25519_SPKI_SIZE) return false
    for (i in ED25519_SPKI_PREFIX.indices) {
        if (spki[i] != ED25519_SPKI_PREFIX[i]) return false
    }
    return true
}

/** Quartz's own Ed25519, so Android does not depend on a provider that has it. */
private fun verifyEd25519(
    publicKey: PublicKey,
    message: ByteArray,
    signature: ByteArray,
): Boolean {
    if (signature.size != 64) return false
    val raw = publicKey.encoded.copyOfRange(ED25519_SPKI_PREFIX.size, ED25519_SPKI_SIZE)
    return try {
        Ed25519.verify(message, signature, raw)
    } catch (_: Exception) {
        false
    }
}
