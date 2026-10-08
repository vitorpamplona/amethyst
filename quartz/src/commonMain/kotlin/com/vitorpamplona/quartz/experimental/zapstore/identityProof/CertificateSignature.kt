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

/**
 * Verifies [signature] over [message] with the public key of the X.509 certificate
 * [certificateDer], the way zsp's `verifyProofSignature` does:
 *
 * - RSA: SHA-256 with PKCS#1 v1.5 (what zsp signs with), falling back to RSASSA-PSS with SHA-256,
 *   MGF1-SHA-256 and a 32-byte salt (Go's `PSSSaltLengthEqualsHash`).
 * - EC (any named curve, P-256 in practice): ECDSA over SHA-256, the signature ASN.1 DER encoded.
 * - Ed25519: the message itself (no pre-hash), 64-byte signature, via Quartz's own [com.vitorpamplona.quartz.mls.crypto.Ed25519].
 *
 * Returns only [IdentityProofVerification.VALID], [IdentityProofVerification.BAD_SIGNATURE],
 * [IdentityProofVerification.BAD_CERTIFICATE] or [IdentityProofVerification.UNSUPPORTED_ALGORITHM].
 * Never throws.
 */
internal expect fun verifyCertificateSignature(
    certificateDer: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): IdentityProofVerification
