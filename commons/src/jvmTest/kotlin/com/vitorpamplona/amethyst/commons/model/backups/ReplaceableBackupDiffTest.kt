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
        val loss = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(listOf(alice, bob), loss.removedTags.map { it[1] })
    }

    @Test
    fun changedRelayHintOrPetnameIsAnEditNotALoss() {
        val saved = sign(3, 100, arrayOf(arrayOf("p", alice, "wss://a.com", "al")))
        val incoming = sign(3, 200, arrayOf(arrayOf("p", alice, "wss://b.com")))
        assertNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
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
        val loss = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertTrue(loss.contentCleared)
        assertTrue(loss.removedTags.isEmpty())
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
        val loss = assertNotNull(ReplaceableBackupDiff.detectLoss(saved, incoming))
        assertEquals(listOf("lud16"), loss.removedFields)
        assertFalse(loss.contentCleared)
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
