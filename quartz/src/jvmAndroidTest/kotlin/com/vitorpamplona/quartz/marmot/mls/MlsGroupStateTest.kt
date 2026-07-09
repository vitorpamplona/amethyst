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
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.marmot.mls.group.RetainedEpochSecrets
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for MLS group state serialization and restoration.
 *
 * Verifies that group state survives a save/restore cycle and that
 * the restored group can continue encrypting/decrypting messages.
 */
class MlsGroupStateTest {
    @Test
    fun testSaveAndRestoreState() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val plaintext = "Hello from epoch 0".encodeToByteArray()
        val encrypted = group.encrypt(plaintext)

        // Save state
        val state = group.saveState()
        val stateBytes = state.encodeTls()
        assertTrue(stateBytes.isNotEmpty())

        // Restore
        val restoredState = MlsGroupState.decodeTls(stateBytes)
        val restoredGroup = MlsGroup.restore(restoredState)

        assertEquals(group.epoch, restoredGroup.epoch)
        assertEquals(group.leafIndex, restoredGroup.leafIndex)
        assertEquals(group.memberCount, restoredGroup.memberCount)
        assertContentEquals(group.groupId, restoredGroup.groupId)
    }

    @Test
    fun testRestoredGroupCanEncrypt() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        // Save and restore
        val state = group.saveState()
        val restoredGroup = MlsGroup.restore(MlsGroupState.decodeTls(state.encodeTls()))

        // Encrypt with restored group
        val plaintext = "Message after restore".encodeToByteArray()
        val encrypted = restoredGroup.encrypt(plaintext)
        assertTrue(encrypted.isNotEmpty())

        // Decrypt with restored group
        val decrypted = restoredGroup.decrypt(encrypted)
        assertContentEquals(plaintext, decrypted.content)
    }

    @Test
    fun testSaveRestoreAfterAddMember() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        group.addMember(bobBundle.keyPackage.toTlsBytes())

        assertEquals(1L, group.epoch)
        assertEquals(2, group.memberCount)

        // Save and restore
        val state = group.saveState()
        val restoredGroup = MlsGroup.restore(MlsGroupState.decodeTls(state.encodeTls()))

        assertEquals(1L, restoredGroup.epoch)
        assertEquals(2, restoredGroup.memberCount)

        // Can still encrypt/decrypt
        val plaintext = "Post-add message".encodeToByteArray()
        val encrypted = restoredGroup.encrypt(plaintext)
        val decrypted = restoredGroup.decrypt(encrypted)
        assertContentEquals(plaintext, decrypted.content)
    }

    @Test
    fun testSaveRestorePreservesExporterSecret() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        val keyBefore = group.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        val state = group.saveState()
        val restoredGroup = MlsGroup.restore(MlsGroupState.decodeTls(state.encodeTls()))

        val keyAfter = restoredGroup.exporterSecret("marmot", "group-event".encodeToByteArray(), 32)

        assertContentEquals(keyBefore, keyAfter)
    }

    @Test
    fun testRetainedEpochSecretsSerialization() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val retained = group.retainedSecrets()

        assertEquals(0L, retained.epoch)
        assertTrue(retained.senderDataSecret.isNotEmpty())
        assertTrue(retained.encryptionSecret.isNotEmpty())
        assertEquals(1, retained.leafCount)

        // Serialize and deserialize
        val writer =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsWriter()
        retained.encodeTls(writer)
        val bytes = writer.toByteArray()

        val restored =
            RetainedEpochSecrets.decodeTls(
                com.vitorpamplona.quartz.marmot.mls.codec
                    .TlsReader(bytes),
            )

        assertEquals(retained.epoch, restored.epoch)
        assertContentEquals(retained.senderDataSecret, restored.senderDataSecret)
        assertContentEquals(retained.encryptionSecret, restored.encryptionSecret)
        assertEquals(retained.leafCount, restored.leafCount)
    }

    @Test
    fun testMultipleEpochSaveRestore() {
        val group = MlsGroup.create("alice".encodeToByteArray())

        // Advance through multiple epochs
        for (i in 0 until 3) {
            val bundle = group.createKeyPackage("member$i".encodeToByteArray(), ByteArray(0))
            group.addMember(bundle.keyPackage.toTlsBytes())
        }

        assertEquals(3L, group.epoch)
        assertEquals(4, group.memberCount)

        // Save and restore
        val state = group.saveState()
        val restoredGroup = MlsGroup.restore(MlsGroupState.decodeTls(state.encodeTls()))

        assertEquals(3L, restoredGroup.epoch)
        assertEquals(4, restoredGroup.memberCount)

        // Verify encrypt/decrypt still works
        val plaintext = "After 3 epochs".encodeToByteArray()
        val encrypted = restoredGroup.encrypt(plaintext)
        val decrypted = restoredGroup.decrypt(encrypted)
        assertContentEquals(plaintext, decrypted.content)
    }

    @Test
    fun testStateVersionInSerialization() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val state = group.saveState()
        val bytes = state.encodeTls()

        // First two bytes should be the version (uint16 = 2)
        val reader =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsReader(bytes)
        val version = reader.readUint16()
        assertEquals(2, version)
    }

    @Test
    fun testSigningKeyRotation() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        val exporterBefore = group.exporterSecret("marmot", "test".encodeToByteArray(), 32)

        // Rotate signing key
        group.proposeSigningKeyRotation()
        group.commit()

        assertEquals(1L, group.epoch)

        // Exporter secret changes after epoch transition
        val exporterAfter = group.exporterSecret("marmot", "test".encodeToByteArray(), 32)
        assertTrue(!exporterBefore.contentEquals(exporterAfter))

        // Can still encrypt/decrypt after rotation
        val plaintext = "After key rotation".encodeToByteArray()
        val encrypted = group.encrypt(plaintext)
        val decrypted = group.decrypt(encrypted)
        assertContentEquals(plaintext, decrypted.content)
    }

    @Test
    fun testSaveRestoreAfterSigningKeyRotation() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        group.proposeSigningKeyRotation()
        group.commit()

        val state = group.saveState()
        val restoredGroup = MlsGroup.restore(MlsGroupState.decodeTls(state.encodeTls()))

        assertEquals(1L, restoredGroup.epoch)

        val plaintext = "After restore + rotation".encodeToByteArray()
        val encrypted = restoredGroup.encrypt(plaintext)
        val decrypted = restoredGroup.decrypt(encrypted)
        assertContentEquals(plaintext, decrypted.content)
    }

    /**
     * Regression: a restore must NOT rewind the SecretTree ratchet to
     * generation 0. A peer that already consumed generation 0 in this epoch
     * (like openmls / MDK / Whitenoise, which forbid generation reuse) would
     * otherwise reject the restored sender's next message as a replay.
     */
    @Test
    fun testRestorePreservesSenderGeneration_peerAcceptsNextMessage() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle =
            MlsGroup
                .create("bob".encodeToByteArray())
                .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)

        // Alice sends generation 0; Bob consumes it.
        val ct0 = alice.encrypt("msg0".encodeToByteArray())
        assertContentEquals("msg0".encodeToByteArray(), bob.decrypt(ct0).content)

        // Alice "restarts": persist then restore.
        val aliceRestored = MlsGroup.restore(MlsGroupState.decodeTls(alice.saveState().encodeTls()))

        // Alice's next send must be generation 1, which Bob accepts. Before
        // the fix this re-emitted generation 0 and Bob threw "Generation 0
        // already consumed".
        val ct1 = aliceRestored.encrypt("msg1".encodeToByteArray())
        assertContentEquals("msg1".encodeToByteArray(), bob.decrypt(ct1).content)
    }

    /**
     * The ratchet position must survive several sends across a restore, not
     * just one. Covers the case where the app persists (at a commit) after N
     * application messages have already advanced the ratchet.
     */
    @Test
    fun testRestorePreservesSenderGenerationAfterMultipleSends() {
        val alice = MlsGroup.create("alice".encodeToByteArray())
        val bobBundle =
            MlsGroup
                .create("bob".encodeToByteArray())
                .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
        val bob = MlsGroup.processWelcome(alice.addMember(bobBundle.keyPackage.toTlsBytes()).welcomeBytes!!, bobBundle)

        for (i in 0 until 5) {
            val ct = alice.encrypt("m$i".encodeToByteArray())
            assertContentEquals("m$i".encodeToByteArray(), bob.decrypt(ct).content)
        }

        val aliceRestored = MlsGroup.restore(MlsGroupState.decodeTls(alice.saveState().encodeTls()))

        // Continues at generation 5 — Bob (who consumed 0..4) accepts it.
        val ct = aliceRestored.encrypt("m5".encodeToByteArray())
        assertContentEquals("m5".encodeToByteArray(), bob.decrypt(ct).content)
    }

    /**
     * Backward compatibility: a STATE_VERSION 1 blob (no persisted ratchet
     * positions) must still decode, yielding an empty ratchet map and the
     * legacy generation-0 restore behavior.
     */
    @Test
    fun testDecodeLegacyV1StateBlob() {
        val group = MlsGroup.create("alice".encodeToByteArray())
        group.encrypt("advance the ratchet".encodeToByteArray())
        val state = group.saveState()

        val v1Bytes = encodeAsV1(state)
        val decoded = MlsGroupState.decodeTls(v1Bytes)

        assertTrue(decoded.senderRatchetStates.isEmpty(), "v1 blob has no ratchet positions")

        // Restores and can still encrypt/decrypt (legacy behavior).
        val restored = MlsGroup.restore(decoded)
        val ct = restored.encrypt("post-restore".encodeToByteArray())
        assertContentEquals("post-restore".encodeToByteArray(), restored.decrypt(ct).content)
    }

    /**
     * Re-encode a state in the original STATE_VERSION 1 layout: identical to
     * v2 but with the version tag set to 1 and no trailing ratchet section.
     */
    private fun encodeAsV1(state: MlsGroupState): ByteArray {
        val writer =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsWriter()
        writer.putUint16(1)
        state.groupContext.encodeTls(writer)
        writer.putOpaqueVarInt(state.treeBytes)
        writer.putUint32(state.myLeafIndex.toLong())
        val es = state.epochSecrets
        writer.putOpaqueVarInt(es.joinerSecret)
        writer.putOpaqueVarInt(es.welcomeSecret)
        writer.putOpaqueVarInt(es.epochSecret)
        writer.putOpaqueVarInt(es.senderDataSecret)
        writer.putOpaqueVarInt(es.encryptionSecret)
        writer.putOpaqueVarInt(es.exporterSecret)
        writer.putOpaqueVarInt(es.epochAuthenticator)
        writer.putOpaqueVarInt(es.externalSecret)
        writer.putOpaqueVarInt(es.confirmationKey)
        writer.putOpaqueVarInt(es.membershipKey)
        writer.putOpaqueVarInt(es.resumptionPsk)
        writer.putOpaqueVarInt(es.initSecret)
        writer.putOpaqueVarInt(state.initSecret)
        writer.putOpaqueVarInt(state.signingPrivateKey)
        writer.putOpaqueVarInt(state.encryptionPrivateKey)
        writer.putOpaqueVarInt(state.interimTranscriptHash)
        writer.putOpaqueVarInt(state.encryptionSecret)
        return writer.toByteArray()
    }
}
