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
package com.vitorpamplona.amethyst.model

import android.os.Looper
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The Buzz dialect of NIP-29 in `LocalCache`: dialect detection off VERIFIED events,
 * timeline attachment into the group's (stable, never-swapped) `RelayGroupChannel`, and
 * the kind-40003 edit overlay held in `BuzzWorkspaceStates` keyed by the channel id.
 */
class BuzzWorkspaceChannelTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val signer = NostrSignerInternal(KeyPair())

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        BuzzRelayDialect.clearForTesting()
        BuzzWorkspaceStates.clearForTesting()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        BuzzRelayDialect.clearForTesting()
        BuzzWorkspaceStates.clearForTesting()
    }

    private fun newChannelId() = UUID.randomUUID().toString()

    private suspend fun streamMessage(
        channelId: String,
        text: String,
    ) = signer.sign(StreamMessageV2Event.build(channelId, text))

    @Test
    fun verifiedBuzzKindMarksDialectAndAttachesToTheSameChannel() =
        runBlocking {
            val channelId = newChannelId()
            val msg = streamMessage(channelId, "hello workspace")

            assertFalse(BuzzRelayDialect.isBuzz(buzzRelay))

            LocalCache.checkDeletionAndConsume(msg, buzzRelay, false)

            assertTrue("a verified 40002 marks the serving relay as Buzz", BuzzRelayDialect.isBuzz(buzzRelay))

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))
            assertNotNull("the group channel exists after a 40002 lands", channel)
            // The channel type never changes on dialect discovery — screens capture it for life.
            assertTrue(channel is RelayGroupChannel)
            assertTrue("the message is on the channel timeline", channel!!.notes.containsKey(msg.id))
        }

    @Test
    fun channelInstanceIsStableAcrossDialectDiscovery() =
        runBlocking {
            val channelId = newChannelId()
            val key = GroupId(channelId, buzzRelay)

            // The group materializes plain (e.g. via 39000) BEFORE any Buzz kind reveals the dialect.
            val before = LocalCache.getOrCreateRelayGroupChannel(key)

            LocalCache.checkDeletionAndConsume(streamMessage(channelId, "first buzz kind"), buzzRelay, false)

            val after = LocalCache.getRelayGroupChannelIfExists(key)
            // Same object: no swap, so every live screen/feed/composer reference stays valid.
            assertTrue("dialect discovery must NOT swap the channel instance", before === after)
        }

    @Test
    fun unverifiedEventDoesNotMarkTheDialect() =
        runBlocking {
            val channelId = newChannelId()
            // Tamper the signature so verification fails.
            val real = streamMessage(channelId, "spoof")
            val forged = StreamMessageV2Event(real.id, real.pubKey, real.createdAt, real.tags, real.content, "00".repeat(64))

            LocalCache.checkDeletionAndConsume(forged, buzzRelay, false)

            assertFalse("an unverifiable event must not flip the dialect", BuzzRelayDialect.isBuzz(buzzRelay))
        }

    @Test
    fun editOverlayTracksNewestEditAndIsNotATimelineRow() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "teh typo")
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)

            val edit1 =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "the typo", createdAt = original.createdAt + 10),
                )
            val edit2 =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "the fix", createdAt = original.createdAt + 20),
                )

            // Out-of-order arrival: newer first, older second.
            LocalCache.checkDeletionAndConsume(edit2, buzzRelay, false)
            LocalCache.checkDeletionAndConsume(edit1, buzzRelay, false)

            val state = BuzzWorkspaceStates.getIfExists(channelId)!!
            assertEquals("newest edit wins regardless of arrival order", "the fix", state.effectiveContentFor(original.id))
            assertEquals(edit2.id, state.editFor(original.id)?.idHex)

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))!!
            assertFalse("edits are overlays, never timeline rows", channel.notes.containsKey(edit1.id))
            assertFalse("edits are overlays, never timeline rows", channel.notes.containsKey(edit2.id))
        }

    @Test
    fun overlayIsKeyedByChannelIdSoOwnSendsWithNoRelayLand() =
        runBlocking {
            // An edit consumed with a null provenance relay (own optimistic send) must
            // still record its overlay — the registry is keyed by channel id, not relay.
            val channelId = newChannelId()
            val original = streamMessage(channelId, "original")
            LocalCache.checkDeletionAndConsume(original, null, true)

            val edit =
                signer.sign(
                    StreamMessageEditEvent.build(channelId, original.id, "edited offline", createdAt = original.createdAt + 5),
                )
            LocalCache.checkDeletionAndConsume(edit, null, true)

            assertEquals("edited offline", BuzzWorkspaceStates.getIfExists(channelId)?.effectiveContentFor(original.id))
        }

    @Test
    fun pruneDropsOverlaysForMessagesNoLongerInTheChannel() =
        runBlocking {
            val channelId = newChannelId()
            val original = streamMessage(channelId, "will be pruned")
            LocalCache.checkDeletionAndConsume(original, buzzRelay, false)
            val edit = signer.sign(StreamMessageEditEvent.build(channelId, original.id, "edit", createdAt = original.createdAt + 5))
            LocalCache.checkDeletionAndConsume(edit, buzzRelay, false)

            val state = BuzzWorkspaceStates.getIfExists(channelId)!!
            assertNotNull(state.editFor(original.id))

            // Simulate the message having been reaped from the channel.
            state.pruneEdits(emptySet())
            assertNull("overlay for a pruned message must be dropped", state.editFor(original.id))
        }
}
