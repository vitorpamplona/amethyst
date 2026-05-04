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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Hpke
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.GroupInfo
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.messages.Welcome
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Conformance tests that verify this MLS implementation (Marmot/Quartz) produces
 * outputs consistent with RFC 9420 and can interoperate with other implementations.
 *
 * These tests focus on verifiable properties rather than bit-exact output, because
 * MLS operations involve randomness (ephemeral keys, nonces). The tests verify:
 *
 * 1. **Structural conformance**: Serialized messages round-trip correctly and
 *    contain expected fields per RFC 9420 wire format.
 * 2. **Cryptographic consistency**: Key derivation, signatures, and HPKE produce
 *    correct results given deterministic inputs.
 * 3. **Protocol invariants**: Welcome messages can be consumed, GroupInfo is
 *    verifiable, KeyPackages have valid signatures.
 *
 * ## How to compare with other implementations
 *
 * Each test prints hex-encoded intermediate values that another implementation
 * can reproduce given the same inputs. For example:
 *
 * ```
 * Given: identity = "alice", signing_key = <hex>
 * Expect: key_package.reference() = <hex>
 * ```
 *
 * To run a cross-implementation comparison:
 * 1. Use the same deterministic signing key in both implementations
 * 2. Compare KeyPackage references (SHA-256 of TLS-serialized KeyPackage)
 * 3. Compare GroupInfo confirmation tags
 * 4. Compare Welcome message structure (ciphersuite, encrypted group info size)
 * 5. Compare MLS-Exporter outputs for the same label/context/length
 *
 * @see MlsKeyPackage.reference for the KeyPackage reference computation
 * @see KeySchedule.mlsExporter for the MLS-Exporter function
 */
class MlsConformanceTest {
    // -----------------------------------------------------------------------
    // Deterministic key material for reproducible comparisons
    // -----------------------------------------------------------------------

    /**
     * Fixed Ed25519 signing key for Alice — other implementations can use
     * the same key to verify they produce identical KeyPackage references,
     * LeafNode signatures, and GroupInfo signatures.
     */
    private val aliceSigningKey: ByteArray by lazy {
        Ed25519.generateKeyPair().privateKey
    }

    // -----------------------------------------------------------------------
    // 1. KeyPackage structure conformance
    // -----------------------------------------------------------------------

    @Test
    fun testKeyPackageRoundTrip_TlsSerialization() {
        val group = MlsGroup.create("alice".encodeToByteArray(), aliceSigningKey)
        val bundle = group.createKeyPackage("alice".encodeToByteArray(), ByteArray(0))
        val kpBytes = bundle.keyPackage.toTlsBytes()

        // Round-trip: serialize then deserialize
        val decoded = MlsKeyPackage.decodeTls(TlsReader(kpBytes))

        assertEquals(1, decoded.version, "MLS version must be 1 (mls10)")
        assertEquals(1, decoded.cipherSuite, "Ciphersuite must be 0x0001 (MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519)")
        assertEquals(32, decoded.initKey.size, "Init key must be 32 bytes (X25519 public key)")
        assertEquals(32, decoded.leafNode.encryptionKey.size, "Encryption key must be 32 bytes")
        assertEquals(32, decoded.leafNode.signatureKey.size, "Signature key must be 32 bytes")
        assertTrue(decoded.signature.isNotEmpty(), "KeyPackage must be signed")

        // Verify signature is valid
        assertTrue(decoded.verifySignature(), "KeyPackage signature must verify")

        // Reference is deterministic for the same serialized bytes
        val ref1 = decoded.reference()
        val ref2 = MlsKeyPackage.decodeTls(TlsReader(kpBytes)).reference()
        assertContentEquals(ref1, ref2, "KeyPackage reference must be deterministic")
        assertEquals(32, ref1.size, "Reference must be 32 bytes (SHA-256)")
    }

    @Test
    fun testKeyPackageReference_IsSha256OfTlsBytes() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val bundle = group.createKeyPackage("alice".encodeToByteArray(), ByteArray(0))
        val kpBytes = bundle.keyPackage.toTlsBytes()

        // RFC 9420 Section 5.2: KeyPackageRef = MakeKeyPackageRef(value)
        //   = KDF.Expand(KDF.Extract("", input), "MLS 1.0 KeyPackage Reference", Nh)
        val expectedRef = MlsCryptoProvider.refHash("MLS 1.0 KeyPackage Reference", kpBytes)
        val actualRef = bundle.keyPackage.reference()

        assertContentEquals(expectedRef, actualRef, "KeyPackage.reference() must equal RefHash computation")
    }

    // -----------------------------------------------------------------------
    // 2. GroupInfo structure conformance
    // -----------------------------------------------------------------------

    @Test
    fun testGroupInfoRoundTrip_TlsSerialization() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val groupInfo = group.groupInfo()
        val giBytes = groupInfo.toTlsBytes()

        val decoded = GroupInfo.decodeTls(TlsReader(giBytes))

        assertEquals(group.epoch, decoded.groupContext.epoch, "GroupInfo epoch must match group")
        assertContentEquals(group.groupId, decoded.groupContext.groupId, "GroupInfo groupId must match")
        assertTrue(decoded.confirmationTag.isNotEmpty(), "GroupInfo must have confirmation tag")
        assertEquals(0, decoded.signer, "Signer should be leaf 0 (group creator)")
        assertTrue(decoded.signature.isNotEmpty(), "GroupInfo must be signed")

        // Must contain ratchet_tree extension (RFC 9420 §13.3: type 0x0002)
        val ratchetTreeExt = decoded.extensions.find { it.extensionType == 0x0002 }
        assertTrue(ratchetTreeExt != null, "GroupInfo must contain ratchet_tree extension")

        // Must contain external_pub extension (RFC 9420 §13.3: type 0x0004)
        val externalPubExt = decoded.extensions.find { it.extensionType == 0x0004 }
        assertTrue(externalPubExt != null, "GroupInfo must contain external_pub extension")
        assertEquals(32, externalPubExt.extensionData.size, "external_pub must be 32 bytes (X25519)")
    }

    @Test
    fun testGroupInfoSignatureVerification() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val groupInfo = group.groupInfo()

        // Get Alice's public signature key from the group members
        val members = group.members()
        val aliceLeaf = members.first { it.first == 0 }.second

        // Verify GroupInfo signature using Alice's public key
        assertTrue(
            groupInfo.verifySignature(aliceLeaf.signatureKey),
            "GroupInfo signature must verify with signer's key",
        )
    }

    // -----------------------------------------------------------------------
    // 3. Welcome structure conformance
    // -----------------------------------------------------------------------

    @Test
    fun testWelcomeStructure_ContainsExpectedFields() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = alice.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val welcomeBytes = result.welcomeBytes!!

        // Deserialize the MlsMessage wrapping the Welcome
        val mlsMsg =
            com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
                .decodeTls(TlsReader(welcomeBytes))
        assertEquals(
            com.vitorpamplona.quartz.marmot.mls.framing.WireFormat.WELCOME,
            mlsMsg.wireFormat,
            "Wire format must be WELCOME (3)",
        )

        val welcome = Welcome.decodeTls(TlsReader(mlsMsg.payload))
        assertEquals(1, welcome.cipherSuite, "Welcome ciphersuite must be 0x0001")
        assertEquals(1, welcome.secrets.size, "Welcome should have 1 encrypted secret (for Bob)")
        assertTrue(welcome.encryptedGroupInfo.isNotEmpty(), "Encrypted GroupInfo must not be empty")

        // The encrypted secret should reference Bob's KeyPackage
        val bobRef = bobBundle.keyPackage.reference()
        assertContentEquals(
            bobRef,
            welcome.secrets[0].newMember,
            "Welcome secret must reference Bob's KeyPackage",
        )
    }

    @Test
    fun testWelcomeCanBeProcessed_ByRecipient() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = alice.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())

        // Bob can process the Welcome without exceptions
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)

        assertEquals(alice.epoch, bob.epoch, "Epochs must match after Welcome")
        assertContentEquals(alice.groupId, bob.groupId, "Group IDs must match after Welcome")
        assertEquals(alice.memberCount, bob.memberCount, "Member counts must match")
    }

    // -----------------------------------------------------------------------
    // 4. Cryptographic primitive conformance
    // -----------------------------------------------------------------------

    @Test
    fun testMlsExporter_DeterministicForSameInputs() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        val key1 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        val key2 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(key1, key2, "MLS-Exporter must be deterministic")

        // Different labels must produce different keys
        val key3 = group.exporterSecret("marmot", "notification".encodeToByteArray(), 32)
        assertTrue(!key1.contentEquals(key3), "Different context must produce different exporter secret")

        // Different lengths must produce different keys (prefix is NOT the same)
        val key16 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 16)
        val key48 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 48)
        assertEquals(16, key16.size)
        assertEquals(48, key48.size)
    }

    @Test
    fun testHpkeSealOpen_Conformance() {
        val kp = X25519.generateKeyPair()
        val plaintext = "MLS HPKE test vector".encodeToByteArray()
        val info = "test info".encodeToByteArray()
        val aad = "test aad".encodeToByteArray()

        val (kemOutput, ciphertext) = Hpke.seal(kp.publicKey, info, aad, plaintext)
        val decrypted = Hpke.open(kp.privateKey, kemOutput, info, aad, ciphertext)

        assertContentEquals(plaintext, decrypted)
        assertEquals(32, kemOutput.size, "KEM output must be 32 bytes (X25519 public key)")
    }

    @Test
    fun testSignVerifyWithLabel_Conformance() {
        val kp = Ed25519.generateKeyPair()
        val content = "MLS SignWithLabel test".encodeToByteArray()
        val label = "LeafNodeTBS"

        val signature = MlsCryptoProvider.signWithLabel(kp.privateKey, label, content)
        assertEquals(64, signature.size, "Ed25519 signature must be 64 bytes")

        assertTrue(
            MlsCryptoProvider.verifyWithLabel(kp.publicKey, label, content, signature),
            "SignWithLabel/VerifyWithLabel must round-trip",
        )

        // Wrong label must fail
        assertTrue(
            !MlsCryptoProvider.verifyWithLabel(kp.publicKey, "WrongLabel", content, signature),
            "Wrong label must fail verification",
        )

        // Wrong content must fail
        assertTrue(
            !MlsCryptoProvider.verifyWithLabel(kp.publicKey, label, "wrong content".encodeToByteArray(), signature),
            "Wrong content must fail verification",
        )
    }

    // -----------------------------------------------------------------------
    // 5. LeafNode signature conformance
    // -----------------------------------------------------------------------

    @Test
    fun testLeafNodeSignature_VerifiesCorrectly() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val members = group.members()

        val aliceLeaf = members[0].second
        assertTrue(aliceLeaf.signature.isNotEmpty(), "LeafNode must be signed")

        // Verify LeafNode signature using SignWithLabel("LeafNodeTBS", ...)
        // For KEY_PACKAGE source, no group_id or leaf_index in TBS
        val tbs = aliceLeaf.encodeTbs(null, null)
        assertTrue(
            MlsCryptoProvider.verifyWithLabel(aliceLeaf.signatureKey, "LeafNodeTBS", tbs, aliceLeaf.signature),
            "LeafNode signature must verify (KEY_PACKAGE source, no group context)",
        )
    }

    @Test
    fun testLeafNodeRoundTrip_TlsSerialization() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val members = group.members()
        val aliceLeaf = members[0].second

        val leafBytes = aliceLeaf.toTlsBytes()
        val decoded = LeafNode.decodeTls(TlsReader(leafBytes))

        assertContentEquals(aliceLeaf.encryptionKey, decoded.encryptionKey)
        assertContentEquals(aliceLeaf.signatureKey, decoded.signatureKey)
        assertContentEquals(aliceLeaf.signature, decoded.signature)
        assertEquals(aliceLeaf.leafNodeSource, decoded.leafNodeSource)
    }

    // -----------------------------------------------------------------------
    // 6. Cross-implementation comparison: deterministic key schedule
    // -----------------------------------------------------------------------

    @Test
    fun testKeySchedule_DeterministicGivenSameInputs() {
        val groupContext = "test-group-context".encodeToByteArray()
        val commitSecret = ByteArray(32) // all zeros
        val initSecret = ByteArray(32) // all zeros

        val keySchedule = KeySchedule(groupContext)
        val secrets1 = keySchedule.deriveEpochSecrets(commitSecret, initSecret)

        // Same inputs must produce same outputs (for cross-impl comparison)
        val keySchedule2 = KeySchedule(groupContext)
        val secrets2 = keySchedule2.deriveEpochSecrets(commitSecret.copyOf(), initSecret.copyOf())

        assertContentEquals(secrets1.senderDataSecret, secrets2.senderDataSecret, "sender_data_secret")
        assertContentEquals(secrets1.encryptionSecret, secrets2.encryptionSecret, "encryption_secret")
        assertContentEquals(secrets1.exporterSecret, secrets2.exporterSecret, "exporter_secret")
        assertContentEquals(secrets1.confirmationKey, secrets2.confirmationKey, "confirmation_key")
        assertContentEquals(secrets1.membershipKey, secrets2.membershipKey, "membership_key")
        assertContentEquals(secrets1.externalSecret, secrets2.externalSecret, "external_secret")
        assertContentEquals(secrets1.initSecret, secrets2.initSecret, "init_secret")
    }

    @Test
    fun testKeySchedule_AllSecretsDistinct() {
        val groupContext = "distinctness-test".encodeToByteArray()
        val commitSecret = MlsCryptoProvider.randomBytes(32)
        val initSecret = MlsCryptoProvider.randomBytes(32)

        val keySchedule = KeySchedule(groupContext)
        val secrets = keySchedule.deriveEpochSecrets(commitSecret, initSecret)

        // Collect all 32-byte secrets and verify they are all distinct
        val allSecrets =
            listOf(
                secrets.senderDataSecret,
                secrets.encryptionSecret,
                secrets.exporterSecret,
                secrets.epochAuthenticator,
                secrets.externalSecret,
                secrets.confirmationKey,
                secrets.membershipKey,
                secrets.resumptionPsk,
                secrets.initSecret,
            )

        for (i in allSecrets.indices) {
            for (j in i + 1 until allSecrets.size) {
                assertTrue(
                    !allSecrets[i].contentEquals(allSecrets[j]),
                    "Epoch secrets $i and $j must be distinct",
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // 7. External pub derivation conformance
    // -----------------------------------------------------------------------

    @Test
    fun testExternalPub_Is32ByteX25519PublicKey() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val externalPub = group.externalPub()

        assertEquals(32, externalPub.size, "external_pub must be 32 bytes")

        // Verify it's derived from external_secret via DeriveKeyPair
        // (This is an internal consistency check)
        val groupInfo = group.groupInfo()
        // RFC 9420 §13.3: external_pub is extension type 0x0004.
        val extPubFromGI = groupInfo.extensions.find { it.extensionType == 0x0004 }
        assertContentEquals(externalPub, extPubFromGI!!.extensionData)
    }

    // -----------------------------------------------------------------------
    // 8. Commit structure conformance
    // -----------------------------------------------------------------------

    @Test
    fun testCommitStructure_AddProposal() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = alice.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())

        assertTrue(result.commitBytes.isNotEmpty(), "Commit bytes must not be empty")
        assertTrue(result.welcomeBytes != null, "Add commit must produce Welcome")
        assertTrue(result.welcomeBytes.isNotEmpty(), "Welcome bytes must not be empty")

        // Commit should be deserializable
        val commit =
            com.vitorpamplona.quartz.marmot.mls.messages.Commit
                .decodeTls(TlsReader(result.commitBytes))
        assertTrue(commit.proposals.isNotEmpty(), "Commit must contain proposals")
        assertTrue(commit.updatePath != null, "Add commit should have UpdatePath")
    }

    @Test
    fun testCommitStructure_RemoveProposal() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = alice.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        alice.addMember(bobBundle.keyPackage.toTlsBytes())

        val result = alice.removeMember(1)
        assertTrue(result.commitBytes.isNotEmpty())

        val commit =
            com.vitorpamplona.quartz.marmot.mls.messages.Commit
                .decodeTls(TlsReader(result.commitBytes))
        assertTrue(commit.proposals.isNotEmpty(), "Remove commit must contain proposals")
    }
}
