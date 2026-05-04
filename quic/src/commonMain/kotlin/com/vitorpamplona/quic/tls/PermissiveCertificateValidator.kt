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

/**
 * Test-only certificate validator that accepts every chain and every
 * signature algorithm. Useful for connecting to local development servers
 * with self-signed certificates (picoquic, nests-rs, quic-go's `interop`
 * server, etc.) where the system trust store would reject the cert.
 *
 * **NEVER** wire this into production code. The whole point of the
 * required-validator design is that the type system catches misuse — pass
 * this only from explicit test entry points.
 */
class PermissiveCertificateValidator : CertificateValidator {
    override fun validateChain(
        chain: List<ByteArray>,
        expectedHost: String,
    ) {
        // Accept anything. No-op.
    }

    override fun verifySignature(
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    ) {
        // Accept anything. No-op.
    }
}
