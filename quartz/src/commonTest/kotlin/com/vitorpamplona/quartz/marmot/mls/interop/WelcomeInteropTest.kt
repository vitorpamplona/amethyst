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
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.GroupInfo
import com.vitorpamplona.quartz.marmot.mls.messages.GroupSecrets
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.messages.Welcome
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
            X25519.dh(scalar1, u1),
            "RFC 7748 §6.1 vector 1 mismatch — X25519 DH is non-compliant",
        )

        val scalar2 = "4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d".hexToByteArray()
        val u2 = "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493".hexToByteArray()
        val expect2 = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957".hexToByteArray()
        assertContentEquals(
            expect2,
            X25519.dh(scalar2, u2),
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
            val derivedPub = X25519.publicFromPrivate(initPriv)

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
        val ours = X25519.dh(priv, kemOut)
        assertContentEquals(expected, ours, "X25519 DH disagrees with Java XDH on IETF vector")
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

    /**
     * Verify each IETF KeyPackage's self-signature (signature over KeyPackageTBS
     * using the LeafNode's signature_key). Exercises our Ed25519 + SignContext
     * + LeafNode TLS encoding against known-good OpenMLS/mls-rs output.
     */
    @Test
    fun testIetfKeyPackageSelfSignatureVerifies() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpMsg = MlsMessage.decodeTls(TlsReader(v.keyPackage.hexToByteArray()))
            val kp = MlsKeyPackage.decodeTls(TlsReader(kpMsg.payload))

            assertTrue(
                kp.verifySignature(),
                "KeyPackage self-signature verification failed at welcome vector $idx",
            )
        }
    }

    /**
     * Full Welcome → GroupInfo path against an IETF vector:
     *   1. HPKE-decrypt GroupSecrets using init_priv.
     *   2. Derive welcome_key / welcome_nonce from joiner_secret.
     *   3. AEAD-decrypt encrypted_group_info.
     *   4. Verify GroupInfo signature against welcome.signer_pub.
     *
     * This is the canonical "can we actually read an OpenMLS Welcome"
     * test — it touches HPKE key schedule, AEAD (twice), HKDF-ExpandWithLabel,
     * GroupInfo TLS decoding, and Ed25519 GroupInfoTBS verification.
     */
    @Test
    fun testIetfWelcomeFullDecryptAndGroupInfoSignature() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpMsg = MlsMessage.decodeTls(TlsReader(v.keyPackage.hexToByteArray()))
            val kp = MlsKeyPackage.decodeTls(TlsReader(kpMsg.payload))
            val myRef = kp.reference()

            val welcomeMsg = MlsMessage.decodeTls(TlsReader(v.welcome.hexToByteArray()))
            val welcome = Welcome.decodeTls(TlsReader(welcomeMsg.payload))
            val mySecrets =
                welcome.secrets.find { it.newMember.contentEquals(myRef) }
                    ?: error("No secrets for our KeyPackage at vector $idx")

            val gsBytes =
                MlsCryptoProvider.decryptWithLabel(
                    v.initPriv.hexToByteArray(),
                    "Welcome",
                    welcome.encryptedGroupInfo,
                    mySecrets.encryptedGroupSecrets.kemOutput,
                    mySecrets.encryptedGroupSecrets.ciphertext,
                )
            val groupSecrets = GroupSecrets.decodeTls(TlsReader(gsBytes))

            // RFC 9420 §8.1 Welcome derivation:
            //   member_secret  = Extract(joiner_secret, psk_secret)
            //   welcome_secret = DeriveSecret(member_secret, "welcome")
            //   welcome_key    = ExpandWithLabel(welcome_secret, "key",   "", AEAD.Nk)
            //   welcome_nonce  = ExpandWithLabel(welcome_secret, "nonce", "", AEAD.Nn)
            val pskSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val memberSecret =
                MlsCryptoProvider.hkdfExtract(groupSecrets.joinerSecret, pskSecret)
            val welcomeSecret = MlsCryptoProvider.deriveSecret(memberSecret, "welcome")
            val welcomeKey =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "key",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val welcomeNonce =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "nonce",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )

            val groupInfoBytes =
                MlsCryptoProvider.aeadDecrypt(
                    welcomeKey,
                    welcomeNonce,
                    ByteArray(0),
                    welcome.encryptedGroupInfo,
                )
            val groupInfo = GroupInfo.decodeTls(TlsReader(groupInfoBytes))

            assertTrue(
                groupInfo.verifySignature(v.signerPub.hexToByteArray()),
                "GroupInfo signature verification failed at welcome vector $idx",
            )
        }
    }

    /**
     * passive-client-welcome.json ships the full ratchet tree and the
     * joiner's three private keys. Verify we can at least decrypt the
     * Welcome's GroupInfo and find the joiner's own leaf in the tree —
     * the prerequisite for deriving the initial_epoch_authenticator.
     */
    @Test
    fun testPassiveClientWelcomeDecryptsGroupInfoAndFindsLeaf() {
        // Vectors with external PSKs need a psk_secret derivation we don't
        // bother to model in this test — the HPKE/AEAD round-trip is the
        // focus. IETF ships passive-client vectors both with and without
        // external_psks; we exercise the latter.
        val passive: List<PassiveClientVector> =
            JsonMapper.jsonInstance
                .decodeFromString<List<PassiveClientVector>>(
                    TestResourceLoader().loadString("mls/passive-client-welcome.json"),
                ).filter { it.cipherSuite == 1 && it.externalPsks.isEmpty() }
        assertTrue(passive.isNotEmpty(), "No cipher_suite==1, PSK-free passive-client-welcome vectors")

        for ((idx, v) in passive.withIndex()) {
            val kpMsg = MlsMessage.decodeTls(TlsReader(v.keyPackage.hexToByteArray()))
            val kp = MlsKeyPackage.decodeTls(TlsReader(kpMsg.payload))
            val myRef = kp.reference()

            val welcomeMsg = MlsMessage.decodeTls(TlsReader(v.welcome.hexToByteArray()))
            val welcome = Welcome.decodeTls(TlsReader(welcomeMsg.payload))
            val mySecrets =
                welcome.secrets.find { it.newMember.contentEquals(myRef) }
                    ?: error("No secrets for our KeyPackage at passive vector $idx")

            val gsBytes =
                MlsCryptoProvider.decryptWithLabel(
                    v.initPriv.hexToByteArray(),
                    "Welcome",
                    welcome.encryptedGroupInfo,
                    mySecrets.encryptedGroupSecrets.kemOutput,
                    mySecrets.encryptedGroupSecrets.ciphertext,
                )
            // Read joiner_secret directly — GroupSecrets.decodeTls assumes
            // psks is a vector of opaque blobs, but IETF passive-client
            // vectors with external PSKs encode them as PreSharedKeyID
            // structs. The HPKE decrypt is what this test exercises.
            val joinerSecret = TlsReader(gsBytes).readOpaqueVarInt()

            val pskSecret = ByteArray(MlsCryptoProvider.HASH_OUTPUT_LENGTH)
            val memberSecret = MlsCryptoProvider.hkdfExtract(joinerSecret, pskSecret)
            val welcomeSecret = MlsCryptoProvider.deriveSecret(memberSecret, "welcome")
            val welcomeKey =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "key",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val welcomeNonce =
                MlsCryptoProvider.expandWithLabel(
                    welcomeSecret,
                    "nonce",
                    ByteArray(0),
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )
            val groupInfoBytes =
                MlsCryptoProvider.aeadDecrypt(
                    welcomeKey,
                    welcomeNonce,
                    ByteArray(0),
                    welcome.encryptedGroupInfo,
                )
            val groupInfo = GroupInfo.decodeTls(TlsReader(groupInfoBytes))

            // Ratchet tree may come inline in GroupInfo extensions
            // (extension_type = 0x0001) or out-of-band via v.ratchetTree.
            // Some vectors carry neither (delivery-service lookup) — in that
            // case just assert the decrypt went through cleanly.
            val ratchetTreeExt = groupInfo.extensions.find { it.extensionType == 0x0001 }
            val treeBytes =
                ratchetTreeExt?.extensionData ?: v.ratchetTree?.hexToByteArray()
            if (treeBytes == null) continue

            val tree = RatchetTree.decodeTls(TlsReader(treeBytes))

            // Find our leaf by matching the signature public key from our
            // KeyPackage. (The vector's signature_priv is a 32-byte Ed25519
            // seed; our Ed25519.publicFromPrivate expects seed || pub, so
            // reach through the LeafNode instead.)
            val mySigPub = kp.leafNode.signatureKey
            var myLeafIndex = -1
            for (i in 0 until tree.leafCount) {
                val leaf = tree.getLeaf(i)
                if (leaf != null && leaf.signatureKey.contentEquals(mySigPub)) {
                    myLeafIndex = i
                    break
                }
            }
            assertTrue(
                myLeafIndex >= 0,
                "Joiner's signature key not found in ratchet tree at passive vector $idx",
            )

            // GroupInfo signature: signer is a different leaf in the tree.
            val signerLeaf = tree.getLeaf(groupInfo.signer)
            assertNotNull(
                signerLeaf,
                "Signer leaf is null at signer=${groupInfo.signer} for passive vector $idx",
            )
            assertTrue(
                groupInfo.verifySignature(signerLeaf.signatureKey),
                "GroupInfo signature verification failed for passive vector $idx",
            )
        }
    }
}
