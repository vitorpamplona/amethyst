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
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.Commit
import com.vitorpamplona.quartz.marmot.mls.messages.GroupContext
import com.vitorpamplona.quartz.marmot.mls.messages.Proposal
import com.vitorpamplona.quartz.marmot.mls.messages.ProposalOrRef
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource
import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.marmot.mls.tree.ParentNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Tests for MLS type serialization/deserialization.
 * Verifies round-trip encoding matches the expected wire format.
 */
class MlsTypesTest {
    @Test
    fun testCredentialBasicRoundTrip() {
        val identity = "alice@example.com".encodeToByteArray()
        val cred = Credential.Basic(identity)
        val bytes = cred.toTlsBytes()

        val decoded = Credential.decodeTls(TlsReader(bytes))
        assertTrue(decoded is Credential.Basic)
        assertContentEquals(identity, (decoded as Credential.Basic).identity)
    }

    @Test
    fun testCapabilitiesRoundTrip() {
        val caps =
            Capabilities(
                versions = listOf(1),
                ciphersuites = listOf(1, 3),
                extensions = listOf(0xF2EE),
                proposals = listOf(1, 2, 3, 8),
                credentials = listOf(1),
            )

        val bytes = caps.toTlsBytes()
        val decoded = Capabilities.decodeTls(TlsReader(bytes))

        assertEquals(caps.versions, decoded.versions)
        assertEquals(caps.ciphersuites, decoded.ciphersuites)
        assertEquals(caps.extensions, decoded.extensions)
        assertEquals(caps.proposals, decoded.proposals)
        assertEquals(caps.credentials, decoded.credentials)
    }

    @Test
    fun testExtensionRoundTrip() {
        val ext = Extension(0xF2EE, byteArrayOf(0x01, 0x02, 0x03))
        val bytes = ext.toTlsBytes()
        val decoded = Extension.decodeTls(TlsReader(bytes))

        assertEquals(ext.extensionType, decoded.extensionType)
        assertContentEquals(ext.extensionData, decoded.extensionData)
    }

    @Test
    fun testLeafNodeRoundTrip() {
        val leaf =
            LeafNode(
                encryptionKey = ByteArray(32) { it.toByte() },
                signatureKey = ByteArray(32) { (it + 32).toByte() },
                credential = Credential.Basic("test".encodeToByteArray()),
                capabilities = Capabilities(),
                leafNodeSource = LeafNodeSource.KEY_PACKAGE,
                lifetime = Lifetime(1000, 2000),
                extensions = emptyList(),
                signature = ByteArray(64) { (it + 64).toByte() },
            )

        val bytes = leaf.toTlsBytes()
        val decoded = LeafNode.decodeTls(TlsReader(bytes))

        assertContentEquals(leaf.encryptionKey, decoded.encryptionKey)
        assertContentEquals(leaf.signatureKey, decoded.signatureKey)
        assertEquals(leaf.leafNodeSource, decoded.leafNodeSource)
        assertEquals(leaf.lifetime?.notBefore, decoded.lifetime?.notBefore)
        assertEquals(leaf.lifetime?.notAfter, decoded.lifetime?.notAfter)
        assertContentEquals(leaf.signature, decoded.signature)
    }

    @Test
    fun testParentNodeRoundTrip() {
        val parent =
            ParentNode(
                encryptionKey = ByteArray(32) { it.toByte() },
                parentHash = ByteArray(32) { (it + 1).toByte() },
                unmergedLeaves = listOf(1, 3, 5),
            )

        val bytes = parent.toTlsBytes()
        val decoded = ParentNode.decodeTls(TlsReader(bytes))

        assertContentEquals(parent.encryptionKey, decoded.encryptionKey)
        assertContentEquals(parent.parentHash, decoded.parentHash)
        assertEquals(parent.unmergedLeaves, decoded.unmergedLeaves)
    }

    @Test
    fun testGroupContextRoundTrip() {
        val ctx =
            GroupContext(
                groupId = ByteArray(32) { it.toByte() },
                epoch = 42,
                treeHash = ByteArray(32) { (it + 1).toByte() },
                confirmedTranscriptHash = ByteArray(32) { (it + 2).toByte() },
                extensions = listOf(Extension(0xF2EE, byteArrayOf(0x01))),
            )

        val bytes = ctx.toTlsBytes()
        val decoded = GroupContext.decodeTls(TlsReader(bytes))

        assertContentEquals(ctx.groupId, decoded.groupId)
        assertEquals(ctx.epoch, decoded.epoch)
        assertContentEquals(ctx.treeHash, decoded.treeHash)
        assertContentEquals(ctx.confirmedTranscriptHash, decoded.confirmedTranscriptHash)
        assertEquals(1, decoded.extensions.size)
        assertEquals(0xF2EE, decoded.extensions[0].extensionType)
    }

    @Test
    fun testProposalRemoveRoundTrip() {
        val proposal = Proposal.Remove(5)
        val bytes = proposal.toTlsBytes()
        val decoded = Proposal.decodeTls(TlsReader(bytes))

        assertTrue(decoded is Proposal.Remove)
        assertEquals(5, (decoded as Proposal.Remove).removedLeafIndex)
    }

    @Test
    fun testProposalSelfRemoveRoundTrip() {
        val proposal = Proposal.SelfRemove()
        val bytes = proposal.toTlsBytes()
        val decoded = Proposal.decodeTls(TlsReader(bytes))

        assertTrue(decoded is Proposal.SelfRemove)
    }

    @Test
    fun testCommitRoundTrip() {
        val commit =
            Commit(
                proposals =
                    listOf(
                        ProposalOrRef.Inline(Proposal.Remove(2)),
                    ),
                updatePath = null,
            )

        val bytes = commit.toTlsBytes()
        val decoded = Commit.decodeTls(TlsReader(bytes))

        assertEquals(1, decoded.proposals.size)
        assertEquals(null, decoded.updatePath)
    }

    @Test
    fun testPrivateMessageRoundTrip() {
        val msg =
            PrivateMessage(
                groupId = ByteArray(16) { it.toByte() },
                epoch = 5,
                contentType = ContentType.APPLICATION,
                authenticatedData = ByteArray(0),
                encryptedSenderData = ByteArray(24) { it.toByte() },
                ciphertext = ByteArray(100) { it.toByte() },
            )

        val bytes = msg.toTlsBytes()
        val decoded = PrivateMessage.decodeTls(TlsReader(bytes))

        assertContentEquals(msg.groupId, decoded.groupId)
        assertEquals(msg.epoch, decoded.epoch)
        assertEquals(msg.contentType, decoded.contentType)
        assertContentEquals(msg.encryptedSenderData, decoded.encryptedSenderData)
        assertContentEquals(msg.ciphertext, decoded.ciphertext)
    }

    @Test
    fun testMlsMessageRoundTrip() {
        val privMsg =
            PrivateMessage(
                groupId = ByteArray(16),
                epoch = 1,
                contentType = ContentType.APPLICATION,
                authenticatedData = ByteArray(0),
                encryptedSenderData = ByteArray(8),
                ciphertext = ByteArray(32),
            )

        val mlsMsg = MlsMessage.fromPrivateMessage(privMsg)
        assertEquals(WireFormat.PRIVATE_MESSAGE, mlsMsg.wireFormat)
        assertEquals(1, mlsMsg.version)
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
