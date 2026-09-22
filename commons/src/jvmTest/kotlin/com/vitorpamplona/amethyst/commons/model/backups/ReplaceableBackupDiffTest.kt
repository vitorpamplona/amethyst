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
package com.vitorpamplona.amethyst.commons.model.backups

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplaceableBackupDiffTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun sign(
        kind: Int,
        createdAt: Long,
        tags: Array<Array<String>>,
        content: String = "",
    ): Event = signer.sign<Event>(createdAt, kind, tags, content)

    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    @Test
    fun followListThatOnlyAddsIsNotALoss() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", alice), arrayOf("p", bob), arrayOf("p", carol)))
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun followListRewrittenFromScratchIsALoss() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice), arrayOf("p", bob)))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", carol)))
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(BackupEventType.FOLLOW_LIST, diff.eventType)
        assertEquals(listOf(alice, bob), diff.removed.map { it.value })
        assertTrue(diff.removed.all { it.type == BackupEntryType.PERSON })
        assertEquals(listOf(carol), diff.added.map { it.value })
    }

    @Test
    fun changedRelayHintOrPetnameIsAnEditNotALoss() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice, "wss://a.com", "al")))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", alice, "wss://b.com")))
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun relayMarkerChangeIsReportedAsChanged() {
        val saved =
            sign(
                10002,
                100,
                arrayOf(arrayOf("r", "wss://a.com"), arrayOf("r", "wss://b.com", "read"), arrayOf("r", "wss://c.com")),
            )
        val incoming = sign(10002, 200, arrayOf(arrayOf("r", "wss://a.com", "write"), arrayOf("r", "wss://b.com", "read")))
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(BackupEventType.OUTBOX_INBOX_RELAYS, diff.eventType)
        assertEquals(listOf("wss://c.com"), diff.removed.map { it.value })
        assertEquals(BackupEntryType.RELAY, diff.removed.single().type)
        val change = diff.changed.single()
        assertEquals("wss://a.com", change.before.value)
        assertNull(change.before.detail)
        assertEquals("write", change.after.detail)
    }

    @Test
    fun muteListEntriesAreTyped() {
        val saved =
            sign(
                10000,
                100,
                arrayOf(arrayOf("p", alice), arrayOf("t", "spam"), arrayOf("word", "crypto"), arrayOf("e", bob)),
            )
        val incoming = sign(10000, 200, arrayOf())
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(BackupEventType.MUTE_LIST, diff.eventType)
        assertEquals(
            listOf(BackupEntryType.PERSON, BackupEntryType.HASHTAG, BackupEntryType.WORD, BackupEntryType.THREAD),
            diff.removed.map { it.type },
        )
    }

    @Test
    fun bookkeepingTagsDoNotCount() {
        val saved = sign(10002, 100, arrayOf(arrayOf("r", "wss://a.com"), arrayOf("client", "Amethyst"), arrayOf("alt", "x")))
        val incoming = sign(10002, 200, arrayOf(arrayOf("r", "wss://a.com")))
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun olderVersionIsNeverALoss() {
        val saved = sign(3, 200, arrayOf(arrayOf("p", alice)))
        val incoming = sign(3, 100, arrayOf())
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun clearedPrivateItemsAreALoss() {
        val saved = sign(10000, 100, arrayOf(arrayOf("p", alice)), "encrypted-private-items")
        val incoming = sign(10000, 200, arrayOf(arrayOf("p", alice)), "")
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertTrue(diff.privateItemsCleared)
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun contactListContentIsIgnored() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice)), "{\"wss://a.com\":{}}")
        val incoming = sign(3, 200, arrayOf(arrayOf("p", alice)), "")
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun profileMissingFieldsIsALoss() {
        val saved = sign(0, 100, arrayOf(), """{"name":"vitor","about":"hi","lud16":"v@x.com","banner":""}""")
        val incoming = sign(0, 200, arrayOf(), """{"name":"vitor2","about":"hi","banner":"https://b"}""")
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(BackupEventType.PROFILE, diff.eventType)
        assertEquals(listOf("lud16"), diff.removed.map { it.value })
        assertEquals("v@x.com", diff.removed.single().detail)
        assertEquals(listOf("banner"), diff.added.map { it.value })
        val nameChange = diff.changed.single()
        assertEquals("vitor", nameChange.before.detail)
        assertEquals("vitor2", nameChange.after.detail)
        assertFalse(diff.privateItemsCleared)
    }

    @Test
    fun privateItemsRewrittenButNotClearedAreFlaggedAsChanged() {
        val saved = sign(10000, 100, arrayOf(arrayOf("p", alice)), "cipher-a")
        val incoming = sign(10000, 200, arrayOf(), "cipher-b")
        val diff = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertTrue(diff.privateItemsChanged)
        assertFalse(diff.privateItemsCleared)
    }

    @Test
    fun trustProviderEntriesCarryTheService() {
        val saved = sign(10040, 100, arrayOf(arrayOf("30382:rank", alice, "wss://a.com")))
        val incoming = sign(10040, 200, arrayOf())
        val entry = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming)).removed.single()
        assertEquals(BackupEntryType.TRUST_PROVIDER, entry.type)
        assertEquals(alice, entry.value)
        assertEquals("rank", entry.detail)
    }

    @Test
    fun locallySignedEventsForgetTheOldestBeyondCapacity() {
        val first = sign(10000, 1, arrayOf())
        LocallySignedEvents.mark(first)
        repeat(600) { LocallySignedEvents.mark(sign(10000, 2L + it, arrayOf())) }
        assertFalse(LocallySignedEvents.contains(first.id))
    }

    @Test
    fun profileEditThatKeepsFieldsIsNotALoss() {
        val saved = sign(0, 100, arrayOf(), """{"name":"vitor","about":"hi"}""")
        val incoming = sign(0, 200, arrayOf(), """{"name":"vitor","about":"hello","website":"https://v"}""")
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
    }

    @Test
    fun locallySignedEventsTrackOnlyReplaceables() {
        val list = sign(10000, 100, arrayOf())
        val note = sign(1, 100, arrayOf())
        LocallySignedEvents.mark(list)
        LocallySignedEvents.mark(note)
        assertTrue(LocallySignedEvents.contains(list.id))
        assertFalse(LocallySignedEvents.contains(note.id))
    }
}
