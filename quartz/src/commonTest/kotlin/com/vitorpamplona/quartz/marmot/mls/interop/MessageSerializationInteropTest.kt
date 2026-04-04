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
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.messages.Commit
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interop tests for MLS message serialization/deserialization against IETF test vectors
 * from github.com/mlswg/mls-implementations (messages.json).
 *
 * Verifies that Quartz can decode all MLS wire format types produced by other
 * implementations, and that re-encoding produces identical bytes (round-trip).
 */
class MessageSerializationInteropTest {
    private val vectors: List<MessagesVector> =
        JsonMapper.jsonInstance.decodeFromString<List<MessagesVector>>(
            TestResourceLoader().loadString("mls/messages.json"),
        )

    @Test
    fun testWelcomeDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.mlsWelcome.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(bytes))
            assertEquals(
                WireFormat.WELCOME,
                mlsMsg.wireFormat,
                "Welcome wire format mismatch at vector $idx",
            )
            // Re-encode and verify round-trip
            val reEncoded = mlsMsg.toTlsBytes()
            assertContentEquals(
                bytes,
                reEncoded,
                "Welcome round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testKeyPackageDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.mlsKeyPackage.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(bytes))
            assertEquals(
                WireFormat.KEY_PACKAGE,
                mlsMsg.wireFormat,
                "KeyPackage wire format mismatch at vector $idx",
            )
            val reEncoded = mlsMsg.toTlsBytes()
            assertContentEquals(
                bytes,
                reEncoded,
                "KeyPackage round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testGroupInfoDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.mlsGroupInfo.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(bytes))
            assertEquals(
                WireFormat.GROUP_INFO,
                mlsMsg.wireFormat,
                "GroupInfo wire format mismatch at vector $idx",
            )
            val reEncoded = mlsMsg.toTlsBytes()
            assertContentEquals(
                bytes,
                reEncoded,
                "GroupInfo round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testPublicMessageDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            // Test public message for application
            val appBytes = v.publicMessageApplication.hexToByteArray()
            val appMsg = MlsMessage.decodeTls(TlsReader(appBytes))
            assertEquals(WireFormat.PUBLIC_MESSAGE, appMsg.wireFormat)
            assertContentEquals(
                appBytes,
                appMsg.toTlsBytes(),
                "PublicMessage(application) round-trip mismatch at vector $idx",
            )

            // Test public message for proposal
            val propBytes = v.publicMessageProposal.hexToByteArray()
            val propMsg = MlsMessage.decodeTls(TlsReader(propBytes))
            assertEquals(WireFormat.PUBLIC_MESSAGE, propMsg.wireFormat)
            assertContentEquals(
                propBytes,
                propMsg.toTlsBytes(),
                "PublicMessage(proposal) round-trip mismatch at vector $idx",
            )

            // Test public message for commit
            val commitBytes = v.publicMessageCommit.hexToByteArray()
            val commitMsg = MlsMessage.decodeTls(TlsReader(commitBytes))
            assertEquals(WireFormat.PUBLIC_MESSAGE, commitMsg.wireFormat)
            assertContentEquals(
                commitBytes,
                commitMsg.toTlsBytes(),
                "PublicMessage(commit) round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testPrivateMessageDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.privateMessage.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(bytes))
            assertEquals(WireFormat.PRIVATE_MESSAGE, mlsMsg.wireFormat)
            assertContentEquals(
                bytes,
                mlsMsg.toTlsBytes(),
                "PrivateMessage round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testRatchetTreeDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.ratchetTree.hexToByteArray()
            val tree = RatchetTree.decodeTls(TlsReader(bytes))
            assertNotNull(tree, "RatchetTree decode failed at vector $idx")
            val writer = TlsWriter()
            tree.encodeTls(writer)
            val reEncoded = writer.toByteArray()
            assertContentEquals(
                bytes,
                reEncoded,
                "RatchetTree round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testAddProposalDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            // add_proposal in messages.json is a raw KeyPackage (the body of an Add proposal)
            val bytes = v.addProposal.hexToByteArray()
            val kp = MlsKeyPackage.decodeTls(TlsReader(bytes))
            assertNotNull(kp, "Add proposal KeyPackage decode failed at vector $idx")
            assertContentEquals(
                bytes,
                kp.toTlsBytes(),
                "Add proposal KeyPackage round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testRemoveProposalDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            // remove_proposal in messages.json is just uint32(removed_leaf_index) without type prefix
            val bytes = v.removeProposal.hexToByteArray()
            val reader = TlsReader(bytes)
            val removedIndex = reader.readUint32()
            assertTrue(
                removedIndex >= 0,
                "Remove proposal should have valid leaf index at vector $idx",
            )
        }
    }

    @Test
    fun testCommitDeserialization() {
        for ((idx, v) in vectors.withIndex()) {
            val bytes = v.commit.hexToByteArray()
            val commit = Commit.decodeTls(TlsReader(bytes))
            assertNotNull(commit, "Commit decode failed at vector $idx")
            assertContentEquals(
                bytes,
                commit.toTlsBytes(),
                "Commit round-trip mismatch at vector $idx",
            )
        }
    }
}
