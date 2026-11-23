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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.AccountMarmotActions
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Marmot message its author deletes leaves a "message deleted" row where it stood (author and
 * time, no content), as White Noise shows, instead of vanishing from the conversation.
 */
class MarmotDeletedPlaceholderTest {
    private val author = NostrSignerSync(KeyPair())
    private val stranger = NostrSignerSync(KeyPair())
    private val groupId = "c".repeat(64)

    /** The path a group's inner events take: index into the cache, then into the group. */
    private fun ingest(
        cache: EventCache,
        list: MarmotGroupList,
        event: Event,
    ) {
        val deleted = list.messagesDeletedBy(groupId, event)
        val note = AccountMarmotActions.indexMarmotInnerEvent(cache, event).note
        list.leaveDeletedPlaceholders(groupId, deleted)
        list.addMessage(groupId, note)
    }

    @Test
    fun anAuthorsDeletionLeavesAPlaceholder() {
        val cache = EventCache()
        val list = MarmotGroupList("a".repeat(64))
        val message = author.sign<ChatEvent>(1_000, ChatEvent.KIND, emptyArray(), "secret words")
        val deletion = author.sign<DeletionRequestEvent>(1_001, DeletionRequestEvent.KIND, arrayOf(arrayOf("e", message.id)), "")

        ingest(cache, list, message)
        ingest(cache, list, deletion)

        val rows = list.getOrCreateGroup(groupId).messages.toList()
        assertEquals(1, rows.size, "the message is replaced, not joined, by its placeholder")
        val placeholder = rows.single() as MarmotDeletedMessageNote
        assertEquals(message.id, placeholder.deletedId)
        assertEquals(author.pubKey, placeholder.author?.pubkeyHex)
        assertEquals(1_000L, placeholder.createdAt())
        assertEquals(null, placeholder.event, "no content survives")
    }

    @Test
    fun someoneElsesDeletionChangesNothing() {
        val cache = EventCache()
        val list = MarmotGroupList("a".repeat(64))
        val message = author.sign<ChatEvent>(1_000, ChatEvent.KIND, emptyArray(), "mine")
        val forged = stranger.sign<DeletionRequestEvent>(1_001, DeletionRequestEvent.KIND, arrayOf(arrayOf("e", message.id)), "")

        ingest(cache, list, message)
        ingest(cache, list, forged)

        val rows = list.getOrCreateGroup(groupId).messages
        assertEquals(listOf(message.id), rows.map { it.idHex })
        assertTrue(rows.none { it is MarmotDeletedMessageNote })
    }
}
