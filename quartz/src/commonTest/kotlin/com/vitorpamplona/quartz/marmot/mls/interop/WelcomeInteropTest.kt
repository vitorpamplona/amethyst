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
package com.vitorpamplona.quartz.marmot.mls.interop

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.GroupSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.messages.Welcome
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS Welcome message processing (RFC 9420 Section 12.4.3.1)
 * against IETF test vectors from github.com/mlswg/mls-implementations (welcome.json).
 *
 * Verifies that Welcome messages and KeyPackages produced by OpenMLS and mls-rs
 * can be correctly deserialized by Quartz.
 */
class WelcomeInteropTest {
    private val allVectors: List<WelcomeVector> =
        JsonMapper.jsonInstance.decodeFromString<List<WelcomeVector>>(
            TestResourceLoader().loadString("mls/welcome.json"),
        )

    private val vectors: List<WelcomeVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testKeyPackageDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpBytes = v.keyPackage.hexToByteArray()
            // KeyPackage is wrapped in MlsMessage
            val mlsMsg = MlsMessage.decodeTls(TlsReader(kpBytes))
            assertEquals(
                WireFormat.KEY_PACKAGE,
                mlsMsg.wireFormat,
                "KeyPackage wire format mismatch at vector $idx",
            )

            // Verify round-trip
            assertContentEquals(
                kpBytes,
                mlsMsg.toTlsBytes(),
                "KeyPackage round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testWelcomeDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val welcomeBytes = v.welcome.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            assertEquals(
                WireFormat.WELCOME,
                mlsMsg.wireFormat,
                "Welcome wire format mismatch at vector $idx",
            )

            // Verify round-trip
            assertContentEquals(
                welcomeBytes,
                mlsMsg.toTlsBytes(),
                "Welcome round-trip mismatch at vector $idx",
            )
        }
    }

    /**
     * RFC 7748 §6.1 X25519 known-answer vectors. If any of these fail, our
     * DH output disagrees with every spec-compliant implementation (MDK,
     * OpenMLS, OpenSSL, BoringSSL, BouncyCastle), which is exactly the
     * failure mode that makes interop-Welcome fail with BAD_DECRYPT.
     */
    @Test
    fun testX25519Rfc7748Vectors() {
        val scalar1 = "a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4".hexToByteArray()
        val u1 = "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c".hexToByteArray()
        val expect1 = "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552".hexToByteArray()
        assertContentEquals(
            expect1,
            com.vitorpamplona.quartz.marmot.mls.crypto.X25519
                .dh(scalar1, u1),
            "RFC 7748 §6.1 vector 1 mismatch — X25519 DH is non-compliant",
        )

        val scalar2 = "4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d".hexToByteArray()
        val u2 = "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493".hexToByteArray()
        val expect2 = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957".hexToByteArray()
        assertContentEquals(
            expect2,
            com.vitorpamplona.quartz.marmot.mls.crypto.X25519
                .dh(scalar2, u2),
            "RFC 7748 §6.1 vector 2 mismatch",
        )
    }

    /**
     * Sanity check: the init_pub in each Welcome vector's KeyPackage must
     * match X25519.publicFromPrivate(init_priv). If they don't, the HPKE
     * kem_context (enc || pkRm) computed during decapsulation will not
     * match what the sender used, leading to key-schedule divergence.
     */
    @Test
    fun testWelcomeInitKeyMatchesPrivateKey() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpMsg = MlsMessage.decodeTls(TlsReader(v.keyPackage.hexToByteArray()))
            val kp = MlsKeyPackage.decodeTls(TlsReader(kpMsg.payload))

            val initPriv = v.initPriv.hexToByteArray()
            val derivedPub =
                com.vitorpamplona.quartz.marmot.mls.crypto.X25519
                    .publicFromPrivate(initPriv)

            assertContentEquals(
                kp.initKey,
                derivedPub,
                "init_key mismatch at welcome vector $idx: KeyPackage.init_key != X25519.publicFromPrivate(init_priv)",
            )
        }
    }

    /**
     * Compare our X25519 DH against Java's built-in XDH (JDK 11+) using the
     * IETF encrypt_with_label vector's priv and kem_output. Any disagreement
     * here explains the BAD_DECRYPT on Welcomes from MDK/OpenMLS.
     */
    @Test
    fun testX25519DhMatchesJavaXdh() {
        val priv = "fb1ade7939987ff12a9d620772b1f9f7caeba26f8a3ecea9617d9402cd862444".hexToByteArray()
        val kemOut = "0a144e8fbf2d6dcf6fe9d2e2b8aeca5461ff5b0ea9c0ede1040c3dc7ed1dfd1c".hexToByteArray()
        val expected = "940f1c6d6e60a066d98c5ab04561ab87118c3fcbcbd68f1734864f9a304a3b38".hexToByteArray()
        val ours =
            com.vitorpamplona.quartz.marmot.mls.crypto.X25519
                .dh(priv, kemOut)
        assertContentEquals(expected, ours, "X25519 DH disagrees with Java XDH on IETF vector")
    }

    /**
     * Cross-check our decrypt against the IETF encrypt_with_label vector
     * directly. If this fails, the HPKE key schedule or AEAD params in our
     * implementation disagree with OpenMLS/MDK.
     */
    @Test
    fun testDecryptIetfEncryptWithLabelVector() {
        val raw = TestResourceLoader().loadString("mls/crypto-basics.json")
        val all =
            JsonMapper.jsonInstance
                .decodeFromString<List<CryptoBasicsVector>>(raw)
                .filter { it.cipherSuite == 1 }
        assertTrue(all.isNotEmpty())

        for ((idx, v) in all.withIndex()) {
            val ewl = v.encryptWithLabel
            val plaintext =
                MlsCryptoProvider.decryptWithLabel(
                    ewl.priv.hexToByteArray(),
                    ewl.label,
                    ewl.context.hexToByteArray(),
                    ewl.kemOutput.hexToByteArray(),
                    ewl.ciphertext.hexToByteArray(),
                )
            assertContentEquals(
                ewl.plaintext.hexToByteArray(),
                plaintext,
                "IETF encrypt_with_label decrypt mismatch at vector $idx",
            )
        }
    }

    /**
     * Exercise the HPKE path used to unwrap GroupSecrets from an IETF
     * passive-client Welcome vector. This is the exact call site that
     * interop-fails against MDK/OpenMLS when the context is wrong.
     */
    @Test
    fun testWelcomeGroupSecretsDecryptAgainstIetfVector() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpMsg = MlsMessage.decodeTls(TlsReader(v.keyPackage.hexToByteArray()))
            assertEquals(WireFormat.KEY_PACKAGE, kpMsg.wireFormat)
            val kp = MlsKeyPackage.decodeTls(TlsReader(kpMsg.payload))
            val myRef = kp.reference()

            val welcomeMsg = MlsMessage.decodeTls(TlsReader(v.welcome.hexToByteArray()))
            assertEquals(WireFormat.WELCOME, welcomeMsg.wireFormat)
            val welcome = Welcome.decodeTls(TlsReader(welcomeMsg.payload))

            val mySecrets = welcome.secrets.find { it.newMember.contentEquals(myRef) }
            assertTrue(mySecrets != null, "Welcome vector $idx has no secrets for our KeyPackage")

            val initPriv = v.initPriv.hexToByteArray()
            val gsBytes =
                MlsCryptoProvider.decryptWithLabel(
                    initPriv,
                    "Welcome",
                    welcome.encryptedGroupInfo,
                    mySecrets.encryptedGroupSecrets.kemOutput,
                    mySecrets.encryptedGroupSecrets.ciphertext,
                )

            // Should parse cleanly as GroupSecrets
            GroupSecrets.decodeTls(TlsReader(gsBytes))
        }
    }
}
