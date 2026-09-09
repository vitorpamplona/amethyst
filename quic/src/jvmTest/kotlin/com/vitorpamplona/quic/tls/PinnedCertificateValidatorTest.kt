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
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pinning is the trust model for a self-signed Marmot preview endpoint, so the
 * interesting cases are all the ways it must REFUSE. A pin that quietly
 * accepts the wrong certificate is worse than no pin: the operator believes
 * they configured something.
 */
class PinnedCertificateValidatorTest {
    /**
     * A self-signed P-256 certificate for `marmot-preview.test` with an
     * `IP:127.0.0.1` SAN, valid for a century so this test does not become a
     * time bomb. Nothing signs with it — only its DER bytes matter here.
     */
    private val leafDer =
        (
            "308201a43082014aa00302010202146633303a60bb8854f6c7128247d681e5e72afff2300a06082a8648ce3d040302301e31" +
                "1c301a06035504030c136d61726d6f742d707265766965772e746573743020170d3236303930393135323432325a180f3231" +
                "3236303831363135323432325a301e311c301a06035504030c136d61726d6f742d707265766965772e746573743059301306" +
                "072a8648ce3d020106082a8648ce3d030107034200042498e9233c2eb1e6302fb98d0761205c0cd38e9eb72ea89651acb8f1" +
                "a97c32f2f658dfef6a2c1e118f2874f2ae6607c3499814c00d58cf6ebd894b2445742d40a3643062301d0603551d0e041604" +
                "14b9b33ffe3a961e40004413767b7d84acc1069cc5301f0603551d23041830168014b9b33ffe3a961e40004413767b7d84ac" +
                "c1069cc5300f0603551d130101ff040530030101ff300f0603551d110408300687047f000001300a06082a8648ce3d040302" +
                "0348003045022100fea992edecb7f0b3e92d798fac6eca4f728784d879a2d96999e6ec458fa7813002207b921e876f9f38f2" +
                "ede262f55fa8b8968a4373c5bc0ec8326cdd1f8c8c759743"
        ).hexToBytes()

    private val fingerprintHex = "c0f7a502abf9b8f0657ec5c39eaaf4bcff21bb530c8afb81ecbf0b38b76f676c"

    @Test
    fun `the pin is the SHA-256 of the leaf DER exactly as sent`() {
        // The digest a broker operator publishes comes from
        // `openssl x509 -outform der | sha256sum`, so this is the value the
        // whole design hangs on. If it were over anything else — the PEM, the
        // public key, a re-encoded cert — a correctly configured pin would
        // reject a correct endpoint.
        val digest = MessageDigest.getInstance("SHA-256").digest(leafDer)
        assertEquals(fingerprintHex, digest.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun `a matching fingerprint validates`() {
        PinnedCertificateValidator.ofSha256Hex(fingerprintHex).validateChain(listOf(leafDer), "marmot-preview.test")
    }

    @Test
    fun `the host is not checked because the pin already named the certificate`() {
        // A self-signed preview endpoint reached by IP literal usually has no
        // name worth checking, and the pin is a stronger statement than any
        // name would be. This asserts the deliberate difference from
        // JdkCertificateValidator rather than an accident.
        PinnedCertificateValidator.ofSha256Hex(fingerprintHex).validateChain(listOf(leafDer), "not-the-cert-name.example")
    }

    @Test
    fun `a different fingerprint is refused`() {
        val other = "00".repeat(32)
        val e =
            assertFailsWith<QuicCodecException> {
                PinnedCertificateValidator.ofSha256Hex(other).validateChain(listOf(leafDer), "marmot-preview.test")
            }
        assertTrue(e.message!!.contains("pinned SHA-256"), e.message)
    }

    @Test
    fun `one matching pin among several is enough`() {
        PinnedCertificateValidator
            .ofSha256Hex("11".repeat(32), fingerprintHex, "22".repeat(32))
            .validateChain(listOf(leafDer), "marmot-preview.test")
    }

    @Test
    fun `pinning by DER is the same pin as pinning by its fingerprint`() {
        PinnedCertificateValidator.ofDer(leafDer).validateChain(listOf(leafDer), "marmot-preview.test")
    }

    @Test
    fun `an openssl-formatted fingerprint is accepted verbatim`() {
        // `openssl x509 -fingerprint -sha256` prints colon-separated upper
        // case. Making the operator strip that by hand is how a pin ends up
        // mistyped.
        val colonised = fingerprintHex.chunked(2).joinToString(":").uppercase()
        PinnedCertificateValidator.ofSha256Hex(colonised).validateChain(listOf(leafDer), "marmot-preview.test")
    }

    @Test
    fun `an empty chain is refused`() {
        assertFailsWith<QuicCodecException> {
            PinnedCertificateValidator.ofSha256Hex(fingerprintHex).validateChain(emptyList(), "marmot-preview.test")
        }
    }

    @Test
    fun `only the leaf is pinned, so a matching cert deeper in the chain does not count`() {
        // Pinning the leaf and then honouring a match anywhere in the chain
        // would let a peer present any certificate it likes and append the
        // pinned one behind it.
        assertFailsWith<QuicCodecException> {
            PinnedCertificateValidator
                .ofSha256Hex(fingerprintHex)
                .validateChain(listOf(byteArrayOf(1, 2, 3), leafDer), "marmot-preview.test")
        }
    }

    @Test
    fun `a pin that is not a SHA-256 digest is rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { PinnedCertificateValidator.ofSha256Hex("abcd") }
        assertFailsWith<IllegalArgumentException> { PinnedCertificateValidator.ofSha256Hex("zz".repeat(32)) }
        assertFailsWith<IllegalArgumentException> { PinnedCertificateValidator(emptyList()) }
    }

    @Test
    fun `CertificateVerify before Certificate is refused`() {
        // Order matters: without a leaf there is no key to check the signature
        // against, and silently passing would make the pin decorative.
        assertFailsWith<QuicCodecException> {
            PinnedCertificateValidator
                .ofSha256Hex(fingerprintHex)
                .verifySignature(TlsConstants.SIG_ECDSA_SECP256R1_SHA256, ByteArray(64), ByteArray(32))
        }
    }

    @Test
    fun `a garbage signature does not verify against the pinned key`() {
        val validator = PinnedCertificateValidator.ofSha256Hex(fingerprintHex)
        validator.validateChain(listOf(leafDer), "marmot-preview.test")
        assertFailsWith<Exception> {
            validator.verifySignature(TlsConstants.SIG_ECDSA_SECP256R1_SHA256, ByteArray(70), ByteArray(32))
        }
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) or Character.digit(this[i * 2 + 1], 16)).toByte()
        }
}
