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

        // First two bytes should be the version (uint16 = 1)
        val reader =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsReader(bytes)
        val version = reader.readUint16()
        assertEquals(1, version)
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
}
