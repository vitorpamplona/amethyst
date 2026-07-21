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
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceChannel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * The Buzz dialect of NIP-29 in `LocalCache`: dialect detection from event shape,
 * `BuzzWorkspaceChannel` materialization (including the upgrade of a channel that was
 * first created as a plain [com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel]),
 * timeline attachment, and the 40003 edit overlay.
 */
class BuzzWorkspaceChannelTest {
    private val buzzRelay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val vanillaRelay = RelayUrlNormalizer.normalizeOrNull("wss://groups.example.com/")!!
    private val signer = NostrSignerInternal(KeyPair())

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
        BuzzRelayDialect.clearForTesting()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
        BuzzRelayDialect.clearForTesting()
    }

    private fun newChannelId() = UUID.randomUUID().toString()

    private suspend fun streamMessage(
        channelId: String,
        text: String,
    ) = signer.sign(StreamMessageV2Event.build(channelId, text))

    @Test
    fun buzzKindMarksDialectAndMaterializesWorkspaceChannel() =
        runBlocking {
            val channelId = newChannelId()
            val msg = streamMessage(channelId, "hello workspace")

            assertFalse(BuzzRelayDialect.isBuzz(buzzRelay))

            LocalCache.checkDeletionAndConsume(msg, buzzRelay, false)

            assertTrue("consuming a 40002 must mark the serving relay as Buzz", BuzzRelayDialect.isBuzz(buzzRelay))

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay))
            assertNotNull("the group channel must exist after a 40002 lands", channel)
            assertTrue("a Buzz relay's group must be a BuzzWorkspaceChannel", channel is BuzzWorkspaceChannel)
            assertTrue("the message must be attached to the channel timeline", channel!!.notes.containsKey(msg.id))
        }

    @Test
    fun vanillaRelayStaysPlainRelayGroup() {
        val key = GroupId("plain-group", vanillaRelay)
        val channel = LocalCache.getOrCreateRelayGroupChannel(key)
        assertFalse(
            "a group on an unmarked relay must NOT materialize as a Buzz channel",
            channel is BuzzWorkspaceChannel,
        )
    }

    @Test
    fun plainChannelUpgradesWhenDialectIsDiscoveredLater() =
        runBlocking {
            val channelId = newChannelId()
            val key = GroupId(channelId, buzzRelay)

            // Arrival order: the group materializes plain (e.g. via its 39000 metadata)
            // BEFORE any Buzz kind reveals the dialect...
            val plain = LocalCache.getOrCreateRelayGroupChannel(key)
            assertFalse(plain is BuzzWorkspaceChannel)

            // A kind-9 note is already attached to the plain channel.
            val vanillaMsg =
                signer.sign(
                    ChatEvent.build("kind 9 from a vanilla client") {
                        add(arrayOf("h", channelId))
                    },
                )
            LocalCache.checkDeletionAndConsume(vanillaMsg, buzzRelay, false)

            // ...then the first 40002 arrives and upgrades it, migrating the timeline.
            val buzzMsg = streamMessage(channelId, "kind 40002 from a Buzz client")
            LocalCache.checkDeletionAndConsume(buzzMsg, buzzRelay, false)

            val upgraded = LocalCache.getRelayGroupChannelIfExists(key)
            assertTrue("channel must be upgraded in place", upgraded is BuzzWorkspaceChannel)
            assertTrue("pre-upgrade kind-9 note must be migrated", upgraded!!.notes.containsKey(vanillaMsg.id))
            assertTrue("the Buzz message shares the same timeline", upgraded.notes.containsKey(buzzMsg.id))
        }

    @Test
    fun editOverlayTracksNewestEdit() =
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

            LocalCache.checkDeletionAndConsume(edit2, buzzRelay, false)
            LocalCache.checkDeletionAndConsume(edit1, buzzRelay, false)

            val channel = LocalCache.getRelayGroupChannelIfExists(GroupId(channelId, buzzRelay)) as BuzzWorkspaceChannel

            assertEquals(
                "the newest edit wins regardless of arrival order",
                "the fix",
                channel.effectiveContentFor(original.id),
            )
            assertEquals(edit2.id, channel.editFor(original.id)?.idHex)
        }
}
