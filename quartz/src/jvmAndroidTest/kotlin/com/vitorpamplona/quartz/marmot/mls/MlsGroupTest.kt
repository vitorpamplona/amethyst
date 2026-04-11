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

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for MlsGroup — the main MLS engine API.
 *
 * Tests the complete lifecycle: group creation, member addition,
 * message encryption/decryption, and exporter key derivation.
 */
class MlsGroupTest {
    @Test
    fun testCreateGroup() {
        val identity = "alice@nostr".encodeToByteArray()
        val group = MlsGroup.create(identity)

        assertEquals(0L, group.epoch)
        assertEquals(1, group.memberCount)
        assertEquals(0, group.leafIndex)
        assertEquals(32, group.groupId.size)
    }

    @Test
    fun testCreateGroupWithSigningKey() {
        val identity = "alice@nostr".encodeToByteArray()
        val sigKp =
            com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
                .generateKeyPair()
        val group = MlsGroup.create(identity, sigKp.privateKey)

        assertEquals(0L, group.epoch)
        assertEquals(1, group.memberCount)
    }

    @Test
    fun testEncryptDecryptSameGroup() {
        val identity = "alice@nostr".encodeToByteArray()
        val group = MlsGroup.create(identity)

        val plaintext = "Hello, MLS world!".encodeToByteArray()
        val encrypted = group.encrypt(plaintext)

        assertTrue(encrypted.isNotEmpty())
        assertTrue(encrypted.size > plaintext.size, "Encrypted should be larger than plaintext")

        val decrypted = group.decrypt(encrypted)
        assertEquals(0, decrypted.senderLeafIndex)
        assertEquals(group.epoch, decrypted.epoch)
        assertContentEquals(plaintext, decrypted.content)
    }

    @Test
    fun testEncryptMultipleMessages() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        val messages =
            listOf(
                "First message",
                "Second message",
                "Third message",
            )

        val encrypted = messages.map { group.encrypt(it.encodeToByteArray()) }

        // Each encrypted message should be different (different nonce/generation)
        for (i in encrypted.indices) {
            for (j in i + 1 until encrypted.size) {
                assertFalse(
                    encrypted[i].contentEquals(encrypted[j]),
                    "Encrypted messages $i and $j should be different",
                )
            }
        }
    }

    @Test
    fun testExporterSecret() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        // Marmot exporter: MLS-Exporter("marmot", "group-event", 32)
        val key = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertEquals(32, key.size)

        // Same call produces same result (deterministic)
        val key2 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(key, key2)

        // Different label produces different key
        val key3 = group.exporterSecret("other", "group-event".encodeToByteArray(), 32)
        assertFalse(key.contentEquals(key3))
    }

    @Test
    fun testExporterSecretDifferentLengths() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        val key16 = group.exporterSecret("marmot", "test".encodeToByteArray(), 16)
        assertEquals(16, key16.size)

        val key48 = group.exporterSecret("marmot", "test".encodeToByteArray(), 48)
        assertEquals(48, key48.size)
    }

    @Test
    fun testMembersListAfterCreation() {
        val identity = "alice@nostr".encodeToByteArray()
        val group = MlsGroup.create(identity)

        val members = group.members()
        assertEquals(1, members.size)
        assertEquals(0, members[0].first) // leaf index
        assertNotNull(members[0].second) // LeafNode present
    }

    @Test
    fun testCreateKeyPackage() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val bundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))

        assertNotNull(bundle.keyPackage)
        assertEquals(1, bundle.keyPackage.version)
        assertEquals(1, bundle.keyPackage.cipherSuite)
        assertEquals(32, bundle.keyPackage.initKey.size)
        assertEquals(32, bundle.initPrivateKey.size)
        assertEquals(32, bundle.encryptionPrivateKey.size)
        assertEquals(64, bundle.signaturePrivateKey.size)
    }

    @Test
    fun testKeyPackageReference() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val bundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))

        val ref = bundle.keyPackage.reference()
        assertEquals(32, ref.size, "KeyPackage reference should be 32 bytes (SHA-256)")

        // Deterministic
        val ref2 = bundle.keyPackage.reference()
        assertContentEquals(ref, ref2)
    }

    @Test
    fun testAddMemberProducesCommitAndWelcome() {
        val aliceGroup = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = aliceGroup.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val bobKpBytes = bobBundle.keyPackage.toTlsBytes()

        val result = aliceGroup.addMember(bobKpBytes)

        assertTrue(result.commitBytes.isNotEmpty(), "Commit bytes should not be empty")
        assertNotNull(result.welcomeBytes, "Welcome bytes should be present for Add")
        assertTrue(result.welcomeBytes!!.isNotEmpty(), "Welcome bytes should not be empty")

        // After commit, epoch should advance
        assertEquals(1L, aliceGroup.epoch)
        assertEquals(2, aliceGroup.memberCount)
    }

    @Test
    fun testRemoveMember() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        // Add bob first
        val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        group.addMember(bobBundle.keyPackage.toTlsBytes())
        assertEquals(2, group.memberCount)

        // Remove bob
        val result = group.removeMember(1)
        assertTrue(result.commitBytes.isNotEmpty())
        assertEquals(2L, group.epoch) // epoch 0 -> addMember epoch 1 -> removeMember epoch 2
        assertEquals(1, group.memberCount)
    }

    @Test
    fun testEpochAdvancesOnCommit() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        assertEquals(0L, group.epoch)

        val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        group.addMember(bobBundle.keyPackage.toTlsBytes())
        assertEquals(1L, group.epoch)

        val carolBundle = group.createKeyPackage("carol".encodeToByteArray(), ByteArray(0))
        group.addMember(carolBundle.keyPackage.toTlsBytes())
        assertEquals(2L, group.epoch)
    }

    @Test
    fun testExporterSecretChangesPerEpoch() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        val key0 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        group.addMember(bobBundle.keyPackage.toTlsBytes())

        val key1 = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        assertFalse(key0.contentEquals(key1), "Exporter secret should change per epoch")
    }

    @Test
    fun testEncryptAfterEpochChange() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        // Encrypt before adding member
        val ct1 = group.encrypt("before".encodeToByteArray())

        // Add member, advancing epoch
        val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        group.addMember(bobBundle.keyPackage.toTlsBytes())

        // Encrypt after epoch change
        val ct2 = group.encrypt("after".encodeToByteArray())

        // Both should produce valid ciphertexts
        assertTrue(ct1.isNotEmpty())
        assertTrue(ct2.isNotEmpty())
    }

    @Test
    fun testExternalJoin() {
        // Alice creates a group
        val alice = MlsGroup.create("alice".encodeToByteArray())
        assertEquals(0L, alice.epoch)
        assertEquals(1, alice.memberCount)

        // Alice publishes GroupInfo for external joiners
        val groupInfoBytes = alice.groupInfo().toTlsBytes()

        // Zara joins via external commit (without a Welcome)
        val (zara, commitBytes) =
            MlsGroup.externalJoin(
                groupInfoBytes,
                "zara".encodeToByteArray(),
            )

        // Zara is now in the group at epoch 1
        assertEquals(1L, zara.epoch)

        // Alice processes Zara's external commit
        alice.processCommit(commitBytes, zara.leafIndex, ByteArray(0))
        assertEquals(1L, alice.epoch)
        assertEquals(2, alice.memberCount)
    }

    @Test
    fun testSelfRemove() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val selfRemoveBytes = group.selfRemove()
        assertTrue(selfRemoveBytes.isNotEmpty())
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        kotlin.test.assertContentEquals(expected, actual)
    }

    private fun assertFalse(
        condition: Boolean,
        message: String = "",
    ) {
        kotlin.test.assertFalse(condition, message)
    }
}
