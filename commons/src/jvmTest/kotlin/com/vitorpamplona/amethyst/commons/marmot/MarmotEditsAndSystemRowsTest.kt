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

import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotAppEvent
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotSystemEvent
import com.vitorpamplona.quartz.marmot.foundation.appEvents.MarmotSystemType
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Message edits (kind 1009) and group system rows (kind 1210) through the app
 * layer.
 *
 * Both are places where getting the RULE wrong is invisible until two clients
 * disagree: who may replace a message, which of two edits wins, and whether a
 * caption gets written once or on every look.
 */
class MarmotEditsAndSystemRowsTest {
    private val nostrGroupId = "e".repeat(64)

    private class Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val mlsStore = SnapshotStateStore()
        val messageStore = SnapshotMessageStore()

        /**
         * A commit only becomes canonical state once a relay took it — a
         * manager with no publisher discards its pending state and never
         * advances the epoch, so there would be nothing for a row to describe.
         */
        val manager = MarmotManager(signer, mlsStore, messageStore, SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
    }

    private suspend fun Fixture.createGroup(name: String = "edits") =
        manager.createGroup(
            nostrGroupId,
            MarmotGroupData(nostrGroupId = nostrGroupId, name = name, relays = listOf("wss://relay.invalid")),
        )

    private suspend fun MarmotManager.storedEvents(): List<Event> = loadStoredMessages(nostrGroupId).mapNotNull { Event.fromJsonOrNull(it) }

    @Test
    fun `an author's own edit replaces their message`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val original = f.manager.buildTextMessage(nostrGroupId, "frist post")
            f.manager.buildMessageEdit(nostrGroupId, original.innerEvent.id, "first post")

            val overlays = f.manager.editOverlays(f.manager.storedEvents())
            assertEquals("first post", overlays[original.innerEvent.id])
        }

    @Test
    fun `an edit from another account is ignored`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val original = f.manager.buildTextMessage(nostrGroupId, "mine")

            // Hand-built rather than sent, because the point is a receiver
            // refusing it: if authorship were checked only at send time, any
            // member could rewrite anyone's words and no reader would notice.
            val impostor = "f".repeat(64)
            val forged =
                MarmotAppEvent.build(
                    pubKey = impostor,
                    kind = MarmotAppEvent.KIND_EDIT,
                    content = "not mine",
                    createdAt = 1_800_000_000L,
                    tags = arrayOf(arrayOf("e", original.innerEvent.id)),
                )
            f.manager.persistDecryptedMessage(nostrGroupId, forged.toJson().dropLast(1) + ",\"sig\":\"\"}")

            assertNull(f.manager.editOverlays(f.manager.storedEvents())[original.innerEvent.id])
        }

    @Test
    fun `the latest edit wins`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val original = f.manager.buildTextMessage(nostrGroupId, "v1")
            val author = f.signer.pubKey
            for ((at, text) in listOf(1_800_000_000L to "v2", 1_800_000_100L to "v3", 1_800_000_050L to "v2b")) {
                val edit =
                    MarmotAppEvent.build(
                        pubKey = author,
                        kind = MarmotAppEvent.KIND_EDIT,
                        content = text,
                        createdAt = at,
                        tags = arrayOf(arrayOf("e", original.innerEvent.id)),
                    )
                f.manager.persistDecryptedMessage(nostrGroupId, edit.toJson().dropLast(1) + ",\"sig\":\"\"}")
            }
            assertEquals("v3", f.manager.editOverlays(f.manager.storedEvents())[original.innerEvent.id])
        }

    @Test
    fun `an edit for a message we do not hold overlays nothing`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            f.manager.buildMessageEdit(nostrGroupId, "9".repeat(64), "orphan")
            assertTrue(f.manager.editOverlays(f.manager.storedEvents()).isEmpty())
        }

    @Test
    fun `the first look at a group writes no system rows`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            // createGroup already established a baseline through the commit
            // path; looking again with nothing changed must stay silent.
            assertEquals(emptyList(), f.manager.syncGroupSystemRows(nostrGroupId))
            assertTrue(f.manager.storedEvents().none { it.kind == MarmotAppEvent.KIND_SYSTEM })
        }

    @Test
    fun `a rename derives one row and only one`() =
        runBlocking {
            val f = Fixture()
            f.createGroup(name = "before")
            f.manager.syncGroupSystemRows(nostrGroupId)

            f.manager.setGroupProfile(nostrGroupId, "after", "")

            val rows = f.manager.storedEvents().filter { it.kind == MarmotAppEvent.KIND_SYSTEM }
            assertEquals(1, rows.size, "one rename, one row")
            val decoded = MarmotSystemEvent.fromAppEvent(MarmotAppEvent.fromEvent(rows.single()))
            assertEquals(MarmotSystemType.GROUP_RENAMED, decoded?.systemType)
            assertEquals("after", decoded?.name)
            assertEquals(f.signer.pubKey, decoded?.actor)

            // Deriving again against the recorded baseline must not re-write
            // the caption. Without that, every sync would add a row for a
            // change that happened once.
            f.manager.syncGroupSystemRows(nostrGroupId)
            assertEquals(1, f.manager.storedEvents().count { it.kind == MarmotAppEvent.KIND_SYSTEM })
        }

    @Test
    fun `the baseline survives a restart, so a change across one is still described`() =
        runBlocking {
            val f = Fixture()
            f.createGroup(name = "before")
            f.manager.syncGroupSystemRows(nostrGroupId)

            // A fresh manager over the same stores: the snapshot is what
            // carries "where I left off" across the process boundary, and
            // without it a restart would either lose the caption or re-derive
            // the group from nothing.
            val restarted = MarmotManager(f.signer, f.mlsStore, f.messageStore, SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
            restarted.restoreAll()
            restarted.setGroupProfile(nostrGroupId, "after", "")

            val rows = restarted.loadStoredMessages(nostrGroupId).mapNotNull { Event.fromJsonOrNull(it) }.filter { it.kind == MarmotAppEvent.KIND_SYSTEM }
            assertEquals(1, rows.size)
        }
}
