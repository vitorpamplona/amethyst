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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.appComponents.MessageRetentionV1
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Disappearing messages — `marmot.group.message-retention.v1`, component
 * `0x8005`.
 *
 * The component's own rules are what these assert, and two of them are easy to
 * get wrong in ways nobody notices until a message that should be gone is
 * still there:
 *
 * - every message pins the retention of its OWN source epoch, so changing the
 *   setting later must not shorten, extend, or restore an existing message's
 *   expiry; and
 * - a retry of the same MLS message reuses the same pinned value, which
 *   matters because the ratchet rewinds on restart and relays replay.
 *
 * Expiry is advisory by design: the duration is authenticated but the base is
 * the sender's own `created_at`, so it inherits the trust already placed in an
 * MLS-authenticated sender and is not a guarantee against a hostile one.
 */
class MarmotRetentionTest {
    private val nostrGroupId = "e".repeat(64)

    private class Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val mlsStore = SnapshotStateStore()
        val messageStore = SnapshotMessageStore()
        val manager = MarmotManager(signer, mlsStore, messageStore, SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
    }

    private suspend fun Fixture.createGroup(secs: ULong?) =
        manager.createGroup(
            nostrGroupId,
            // Version 3: the legacy `0xF2EE` blob only carries
            // `disappearing_message_secs` from v3 on, so that v1/v2 stays
            // byte-for-byte what MDK's older parser accepts.
            MarmotGroupData(
                nostrGroupId = nostrGroupId,
                name = "retention",
                relays = listOf("wss://relay.invalid"),
                disappearingMessageSecs = secs,
                version = 3,
            ),
        )

    private suspend fun MarmotManager.storedIds(): List<String> = loadStoredMessages(nostrGroupId).mapNotNull { Event.fromJsonOrNull(it)?.id }

    @Test
    fun `a group with no retention keeps its messages`() =
        runBlocking {
            val f = Fixture()
            f.createGroup(null)
            val sent = f.manager.buildTextMessage(nostrGroupId, "keep me")

            assertEquals(0L, f.manager.retentionSeconds(nostrGroupId))
            assertTrue(f.manager.pruneExpiredMessages(nostrGroupId, TimeUtils.now() + 10_000_000).isEmpty())
            assertTrue(sent.innerEvent.id in f.manager.storedIds())
        }

    @Test
    fun `a message outlives its retention and is deleted, not merely hidden`() =
        runBlocking {
            val f = Fixture()
            f.createGroup(60uL)
            val sent = f.manager.buildTextMessage(nostrGroupId, "gone in a minute")
            assertEquals(60L, f.manager.retentionSeconds(nostrGroupId))

            val after = (sent.innerEvent.createdAt) + 61
            assertEquals(setOf(sent.innerEvent.id), f.manager.pruneExpiredMessages(nostrGroupId, after))

            // Gone from the log itself. A disappearing message that is only
            // filtered out of a read is still on disk, and this store holds the
            // only copy — the ratchet moved past the ciphertext long ago.
            assertFalse(sent.innerEvent.id in f.manager.storedIds())
            assertTrue(f.messageStore.loadExpiries(nostrGroupId).isEmpty())
        }

    @Test
    fun `a message inside its window is untouched`() =
        runBlocking {
            val f = Fixture()
            f.createGroup(3600uL)
            val sent = f.manager.buildTextMessage(nostrGroupId, "still fresh")

            assertTrue(f.manager.pruneExpiredMessages(nostrGroupId, sent.innerEvent.createdAt + 60).isEmpty())
            assertTrue(sent.innerEvent.id in f.manager.storedIds())
        }

    @Test
    fun `expiry is pinned at the source epoch and a later change does not re-time it`() =
        runBlocking {
            // The rule that is easiest to get wrong: recomputing expiry from
            // the CURRENT setting would let one member shorten everyone's
            // history retroactively, or restore what should already be gone.
            val f = Fixture()
            f.createGroup(60uL)
            val sent = f.manager.buildTextMessage(nostrGroupId, "pinned at sixty")

            f.manager.updateGroupMetadata(
                nostrGroupId,
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "retention",
                    relays = listOf("wss://relay.invalid"),
                    disappearingMessageSecs = 86_400uL,
                    version = 3,
                ),
            )
            assertEquals(86_400L, f.manager.retentionSeconds(nostrGroupId))

            // Still expires on the old sixty seconds, not the new day.
            assertEquals(
                setOf(sent.innerEvent.id),
                f.manager.pruneExpiredMessages(nostrGroupId, sent.innerEvent.createdAt + 61),
            )
        }

    @Test
    fun `re-persisting the same message reuses its pinned expiry`() =
        runBlocking {
            // The ratchet rewinds on restart and relays replay recent kind:445
            // events, so the same message really is persisted twice. If the
            // second write re-timed it, a message could keep postponing its own
            // expiry every time it was replayed.
            val f = Fixture()
            f.createGroup(60uL)
            val sent = f.manager.buildTextMessage(nostrGroupId, "replayed")
            val pinned = f.messageStore.loadExpiries(nostrGroupId)[sent.innerEvent.id]

            f.manager.updateGroupMetadata(
                nostrGroupId,
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "retention",
                    relays = listOf("wss://relay.invalid"),
                    disappearingMessageSecs = 86_400uL,
                    version = 3,
                ),
            )
            f.manager.persistDecryptedMessage(nostrGroupId, sent.innerEvent.toJson())

            assertEquals(pinned, f.messageStore.loadExpiries(nostrGroupId)[sent.innerEvent.id])
        }

    @Test
    fun `reading a group expires whatever fell due while it was closed`() =
        runBlocking {
            // The restart case: nothing is running to notice the moment a
            // message falls due, so the read itself has to. The message is
            // back-dated, which is also the component's documented caveat —
            // the base is the sender's own `created_at`, so expiry is only as
            // trustworthy as the MLS-authenticated sender.
            val f = Fixture()
            f.createGroup(60uL)
            val stale =
                Event(
                    id = "1".repeat(64),
                    pubKey = f.signer.pubKey,
                    createdAt = TimeUtils.now() - 3600,
                    kind = 9,
                    tags = emptyArray(),
                    content = "sent an hour ago",
                    sig = "",
                )
            f.manager.persistDecryptedMessage(nostrGroupId, stale.toJson())

            assertFalse(stale.id in f.manager.storedIds())
        }

    @Test
    fun `an expiring client tells the front end which messages went`() =
        runBlocking {
            // A message gone from disk but still on screen has not disappeared.
            val f = Fixture()
            f.createGroup(60uL)
            val sent = f.manager.buildTextMessage(nostrGroupId, "drop me from the view")
            val announced = mutableListOf<String>()
            f.manager.onMessagesExpired = { _, ids -> announced.addAll(ids) }

            f.manager.pruneExpiredMessages(nostrGroupId, sent.innerEvent.createdAt + 61)
            assertEquals(listOf(sent.innerEvent.id), announced)
        }

    @Test
    fun `the current profile reads retention from its own component`() =
        runBlocking {
            // The legacy blob only carries the setting from v3, so in practice
            // a group created by the reference client expresses it as component
            // `0x8005` instead. Both have to reach the same answer or a
            // disappearing message stops disappearing at the profile boundary.
            val f = Fixture()
            f.manager.createCurrentProfileGroup(
                nostrGroupId = nostrGroupId,
                relays = listOf("wss://relay.invalid"),
                retention = MessageRetentionV1(120uL),
            )
            assertEquals(120L, f.manager.retentionSeconds(nostrGroupId))

            val sent = f.manager.buildTextMessage(nostrGroupId, "two minutes")
            assertTrue(f.manager.pruneExpiredMessages(nostrGroupId, sent.innerEvent.createdAt + 60).isEmpty())
            assertEquals(
                setOf(sent.innerEvent.id),
                f.manager.pruneExpiredMessages(nostrGroupId, sent.innerEvent.createdAt + 121),
            )
        }
}
