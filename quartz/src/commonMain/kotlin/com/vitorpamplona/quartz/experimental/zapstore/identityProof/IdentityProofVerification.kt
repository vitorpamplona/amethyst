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
 * The outcome of [IdentityProofEvent.verify], in the order the checks run: the first failing
 * check is the one reported. Only [VALID] means the certificate key vouches for the npub.
 */
enum class IdentityProofVerification {
    /** Well formed, for this certificate, not revoked, not expired, and the signature verifies. */
    VALID,

    /** No 64-hex `d`, no base64 `signature`, or no `expiry` later than `created_at`. */
    MALFORMED,

    /** The certificate does not hash (SHA-256 of its DER) to the proof's `d`. */
    CERT_MISMATCH,

    /** The publisher withdrew the proof with a `revoked` tag. */
    REVOKED,

    /** `expiry` has passed. */
    EXPIRED,

    /** The certificate bytes hash to `d` but are not a parseable X.509 certificate. */
    BAD_CERTIFICATE,

    /**
     * The certificate's key type has no verifier here: DSA or another algorithm Zapstore does not
     * sign with, or any key on a platform without X.509 support (iOS, macOS and Linux native).
     */
    UNSUPPORTED_ALGORITHM,

    /** The signature does not verify against the certificate's public key. */
    BAD_SIGNATURE,
}
