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
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * End-to-end lifecycle tests for MlsGroup covering the full protocol flow:
 *
 * - Group creation and Welcome-based joins (RFC 9420 Section 12.4.3)
 * - Cross-member encryption/decryption after Welcome processing
 * - Multi-member groups with sequential additions
 * - Commit processing between independent group instances
 * - External join via GroupInfo (RFC 9420 Section 12.4.3.2)
 * - Exporter secret agreement after join
 * - Member removal and re-keying
 *
 * These tests simulate realistic multi-party scenarios where each participant
 * maintains their own independent MlsGroup instance, communicating only through
 * serialized MLS messages (commit bytes, welcome bytes, encrypted ciphertext).
 */
class MlsGroupLifecycleTest {
    // --- Helper: create a standalone KeyPackageBundle for a new joiner ---

    /**
     * Creates a fresh KeyPackageBundle as a prospective group member would.
     * In production this is done by the joiner BEFORE they know which group
     * they will be invited to (MIP-00 key package publishing).
     */
    private fun createStandaloneKeyPackage(identity: String): KeyPackageBundle {
        val tempGroup = MlsGroup.create(identity.encodeToByteArray())
        return tempGroup.createKeyPackage(identity.encodeToByteArray(), ByteArray(0))
    }

    // -----------------------------------------------------------------------
    // 1. Welcome Processing: Alice creates group, adds Bob, Bob joins
    // -----------------------------------------------------------------------

    @Test
    fun testWelcomeProcessing_BobJoinsAliceGroup() {
        // Alice creates a new group
        val alice = MlsGroup.create("alice".encodeToByteArray())
        assertEquals(0L, alice.epoch)
        assertEquals(1, alice.memberCount)

        // Bob creates a KeyPackage (published to relays via MIP-00)
        val bobBundle = createStandaloneKeyPackage("bob")

        // Alice adds Bob: produces a Commit (broadcast) and Welcome (sent to Bob)
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        assertNotNull(result.welcomeBytes, "Welcome must be produced for Add commit")
        assertEquals(1L, alice.epoch, "Alice advances to epoch 1 after commit")
        assertEquals(2, alice.memberCount)

        // Bob processes the Welcome to join the group
        val bob = MlsGroup.processWelcome(result.welcomeBytes, bobBundle)
        assertEquals(1L, bob.epoch, "Bob should be at same epoch as Alice after Welcome")
        assertEquals(2, bob.memberCount, "Bob should see 2 members")
    }

    // -----------------------------------------------------------------------
    // 2. Cross-member encrypt/decrypt after Welcome
    // -----------------------------------------------------------------------

    @Test
    fun testCrossGroupEncryptDecrypt_AfterWelcome() {
        // Setup: Alice creates group, adds Bob
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)

        // Alice encrypts a message
        val plaintext = "Hello Bob, welcome to the group!".encodeToByteArray()
        val ciphertext = alice.encrypt(plaintext)

        // Bob decrypts Alice's message
        val decrypted = bob.decrypt(ciphertext)
        assertContentEquals(plaintext, decrypted.content)
        assertEquals(0, decrypted.senderLeafIndex, "Sender should be Alice at leaf 0")
        assertEquals(1L, decrypted.epoch)
    }

    @Test
    fun testBobEncryptsAliceDecrypts_AfterWelcome() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)

        // Bob encrypts, Alice decrypts
        val plaintext = "Hi Alice, thanks for the invite!".encodeToByteArray()
        val ciphertext = bob.encrypt(plaintext)
        val decrypted = alice.decrypt(ciphertext)
        assertContentEquals(plaintext, decrypted.content)
        assertEquals(1, decrypted.senderLeafIndex, "Sender should be Bob at leaf 1")
    }

    @Test
    fun testMultipleMessagesExchanged_AfterWelcome() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)

        // Exchange multiple messages in both directions
        val messages =
            listOf(
                Pair(0, "Alice: message 1"),
                Pair(1, "Bob: message 1"),
                Pair(0, "Alice: message 2"),
                Pair(1, "Bob: message 2"),
                Pair(0, "Alice: message 3"),
            )

        for ((senderIdx, text) in messages) {
            val plaintext = text.encodeToByteArray()
            val sender = if (senderIdx == 0) alice else bob
            val receiver = if (senderIdx == 0) bob else alice

            val ct = sender.encrypt(plaintext)
            val dec = receiver.decrypt(ct)
            assertContentEquals(plaintext, dec.content, "Failed on: $text")
            assertEquals(senderIdx, dec.senderLeafIndex)
        }
    }

    // -----------------------------------------------------------------------
    // 3. Exporter secret agreement after Welcome
    // -----------------------------------------------------------------------

    @Test
    fun testExporterSecretAgrees_AfterWelcome() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val result = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(result.welcomeBytes!!, bobBundle)

        // Both should derive the same exporter secret (used for Marmot outer encryption)
        val aliceKey = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        val bobKey = bob.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(aliceKey, bobKey, "Exporter secrets must agree after Welcome join")
    }

    // -----------------------------------------------------------------------
    // 4. Three-member group: sequential additions
    // -----------------------------------------------------------------------

    @Test
    fun testThreeMemberGroup_SequentialAdditions() {
        // Alice creates the group
        val alice = MlsGroup.create("alice".encodeToByteArray())

        // Alice adds Bob
        val bobBundle = createStandaloneKeyPackage("bob")
        val addBobResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addBobResult.welcomeBytes!!, bobBundle)
        assertEquals(1L, alice.epoch)
        assertEquals(1L, bob.epoch)

        // Alice adds Carol (Bob processes Alice's commit)
        val carolBundle = createStandaloneKeyPackage("carol")
        val addCarolResult = alice.addMember(carolBundle.keyPackage.toTlsBytes())
        bob.processFramedCommit(addCarolResult.framedCommitBytes)
        val carol = MlsGroup.processWelcome(addCarolResult.welcomeBytes!!, carolBundle)

        assertEquals(2L, alice.epoch)
        assertEquals(2L, bob.epoch)
        assertEquals(2L, carol.epoch)
        assertEquals(3, alice.memberCount)
        assertEquals(3, bob.memberCount)
        assertEquals(3, carol.memberCount)

        // Verify all three can communicate
        val aliceMsg = "Hello from Alice".encodeToByteArray()
        val ct = alice.encrypt(aliceMsg)

        val bobDecrypted = bob.decrypt(ct)
        assertContentEquals(aliceMsg, bobDecrypted.content)

        val carolDecrypted = carol.decrypt(ct)
        assertContentEquals(aliceMsg, carolDecrypted.content)
    }

    // -----------------------------------------------------------------------
    // 5. Commit processing: Bob adds Carol, Alice processes commit
    // -----------------------------------------------------------------------

    @Test
    fun testCommitProcessing_BobAddsCarolAliceProcesses() {
        // Alice creates group, adds Bob
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addBobResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addBobResult.welcomeBytes!!, bobBundle)

        // Bob adds Carol
        val carolBundle = createStandaloneKeyPackage("carol")
        val addCarolResult = bob.addMember(carolBundle.keyPackage.toTlsBytes())

        // Alice processes Bob's commit
        alice.processFramedCommit(addCarolResult.framedCommitBytes)

        assertEquals(2L, alice.epoch)
        assertEquals(2L, bob.epoch)
        assertEquals(3, alice.memberCount)
        assertEquals(3, bob.memberCount)
    }

    // -----------------------------------------------------------------------
    // 6. External join via GroupInfo
    // -----------------------------------------------------------------------

    @Test
    fun testExternalJoin_ZaraJoinsViaGroupInfo() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val groupInfoBytes = alice.groupInfo().toTlsBytes()

        // Zara joins externally
        val externalJoin = MlsGroup.externalJoin(groupInfoBytes, "zara".encodeToByteArray())
        val zara = externalJoin.group
        assertEquals(1L, zara.epoch)

        // Alice processes the external commit
        alice.processFramedCommit(externalJoin.framedCommitBytes)
        assertEquals(1L, alice.epoch)
        assertEquals(2, alice.memberCount)

        // They can now communicate
        val msg = "External join works!".encodeToByteArray()
        val ct = zara.encrypt(msg)
        val dec = alice.decrypt(ct)
        assertContentEquals(msg, dec.content)
    }

    @Test
    fun testExternalJoin_ExporterSecretsAgree() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val groupInfoBytes = alice.groupInfo().toTlsBytes()

        val externalJoin = MlsGroup.externalJoin(groupInfoBytes, "zara".encodeToByteArray())
        val zara = externalJoin.group
        alice.processFramedCommit(externalJoin.framedCommitBytes)

        val aliceKey = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        val zaraKey = zara.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(aliceKey, zaraKey, "Exporter secrets must agree after external join")
    }

    // -----------------------------------------------------------------------
    // 7. Member removal and re-keying
    // -----------------------------------------------------------------------

    @Test
    fun testRemoveMember_EpochAdvancesAndKeysChange() {
        // Alice creates group, adds Bob
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val keyBeforeRemove = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        // Alice removes Bob
        val removeResult = alice.removeMember(bob.leafIndex)
        assertEquals(2L, alice.epoch)
        assertEquals(1, alice.memberCount)

        // Key must change after removal (forward secrecy)
        val keyAfterRemove = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertFalse(
            keyBeforeRemove.contentEquals(keyAfterRemove),
            "Exporter secret must change after member removal for forward secrecy",
        )
    }

    // -----------------------------------------------------------------------
    // 8. Signing key rotation (Update proposal)
    // -----------------------------------------------------------------------

    @Test
    fun testSigningKeyRotation_EpochAdvances() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val keyBefore = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        // Alice rotates her signing key
        alice.proposeSigningKeyRotation()
        val commitResult = alice.commit()

        // Bob processes Alice's rotation commit
        bob.processFramedCommit(commitResult.framedCommitBytes)

        assertEquals(2L, alice.epoch)
        assertEquals(2L, bob.epoch)

        // Exporter secrets must agree after rotation
        val aliceKey = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        val bobKey = bob.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(aliceKey, bobKey, "Exporter secrets must agree after signing key rotation")

        // Key changed from previous epoch
        assertFalse(keyBefore.contentEquals(aliceKey), "Exporter secret should change after rotation")
    }

    @Test
    fun testEncryptDecryptAfterSigningKeyRotation() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Alice rotates her signing key
        alice.proposeSigningKeyRotation()
        val commitResult = alice.commit()
        bob.processFramedCommit(commitResult.framedCommitBytes)

        // Both directions should still work after rotation
        val msg1 = "After rotation from Alice".encodeToByteArray()
        val ct1 = alice.encrypt(msg1)
        val dec1 = bob.decrypt(ct1)
        assertContentEquals(msg1, dec1.content)

        val msg2 = "After rotation from Bob".encodeToByteArray()
        val ct2 = bob.encrypt(msg2)
        val dec2 = alice.decrypt(ct2)
        assertContentEquals(msg2, dec2.content)
    }

    // -----------------------------------------------------------------------
    // 9. State persistence round-trip with lifecycle events
    // -----------------------------------------------------------------------

    @Test
    fun testSaveRestoreAfterWelcome_CanStillDecrypt() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Save and restore Bob's state
        val bobState = bob.saveState()
        val bobRestored = MlsGroup.restore(bobState)

        // Restored Bob should be able to decrypt Alice's messages
        val msg = "Can restored Bob read this?".encodeToByteArray()
        val ct = alice.encrypt(msg)
        val dec = bobRestored.decrypt(ct)
        assertContentEquals(msg, dec.content)
    }

    @Test
    fun testSaveRestoreAfterWelcome_CanStillEncrypt() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Save and restore Bob
        val bobState = bob.saveState()
        val bobRestored = MlsGroup.restore(bobState)

        // Restored Bob should be able to encrypt for Alice
        val msg = "Message from restored Bob".encodeToByteArray()
        val ct = bobRestored.encrypt(msg)
        val dec = alice.decrypt(ct)
        assertContentEquals(msg, dec.content)
    }

    // -----------------------------------------------------------------------
    // 10. PSK proposal: register and use in commit
    // -----------------------------------------------------------------------

    @Test
    fun testPskProposal_EpochAdvancesWithPsk() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Register PSK on both sides
        val pskId = "shared-secret-1".encodeToByteArray()
        val pskValue = "super-secret-value-32-bytes-long".encodeToByteArray()
        alice.registerPsk(pskId, pskValue)
        bob.registerPsk(pskId, pskValue)

        val epochBefore = alice.epoch

        // Alice creates a PSK proposal and commits
        alice.proposePsk(pskId)
        val commitResult = alice.commit()

        // Bob processes the commit
        bob.processFramedCommit(commitResult.framedCommitBytes)

        assertEquals(epochBefore + 1, alice.epoch)
        assertEquals(alice.epoch, bob.epoch)

        // Communication still works after PSK injection
        val msg = "Message after PSK".encodeToByteArray()
        val ct = alice.encrypt(msg)
        val dec = bob.decrypt(ct)
        assertContentEquals(msg, dec.content)
    }

    // -----------------------------------------------------------------------
    // 11. ReInit proposal: marks group for reinitialization
    // -----------------------------------------------------------------------

    @Test
    fun testReInitProposal_MarksGroupForReInit() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        // Alice proposes ReInit
        alice.proposeReInit()
        val commitResult = alice.commit()

        // After commit, Alice's group should be marked as reInit pending
        assertNotNull(alice.reInitPending, "ReInit should be pending after commit")

        // Bob processes and should also see reInit
        bob.processFramedCommit(commitResult.framedCommitBytes)
        assertNotNull(bob.reInitPending, "Bob should also see ReInit pending")
    }

    // -----------------------------------------------------------------------
    // 12. Empty commit (no proposals, just UpdatePath for forward secrecy)
    // -----------------------------------------------------------------------

    @Test
    fun testEmptyCommit_AdvancesEpoch() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = createStandaloneKeyPackage("bob")
        val addResult = alice.addMember(bobBundle.keyPackage.toTlsBytes())
        val bob = MlsGroup.processWelcome(addResult.welcomeBytes!!, bobBundle)

        val epochBefore = alice.epoch

        // Alice commits with no proposals (purely for forward secrecy / UpdatePath)
        val commitResult = alice.commit()
        bob.processFramedCommit(commitResult.framedCommitBytes)

        assertEquals(epochBefore + 1, alice.epoch)
        assertEquals(alice.epoch, bob.epoch)

        // Exporter secrets still agree
        val aliceKey = alice.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        val bobKey = bob.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)
        assertContentEquals(aliceKey, bobKey)
    }
}
