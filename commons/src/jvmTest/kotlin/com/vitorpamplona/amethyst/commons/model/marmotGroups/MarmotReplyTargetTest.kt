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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Marmot kind 9 replies to what MDK says it replies to (its first `q`, else its first `e`), so
 * the quote bubble names the same parent White Noise shows; any other kind 9 keeps linking every
 * `e` and `q` it carries.
 */
class MarmotReplyTargetTest {
    private val alice = NostrSignerSync(KeyPair())
    private val bob = NostrSignerSync(KeyPair())

    private fun chat(
        signer: NostrSignerSync,
        at: Long,
        text: String,
        tags: Array<Array<String>> = emptyArray(),
    ) = signer.sign<ChatEvent>(at, ChatEvent.KIND, tags, text)

    @Test
    fun whenEAndQDifferTheQIsTheReply() {
        val cache = EventCache()
        val referenced = chat(alice, 1_000, "referenced")
        val parent = chat(alice, 1_001, "parent")
        AccountMarmotActions.indexMarmotInnerEvent(cache, referenced)
        AccountMarmotActions.indexMarmotInnerEvent(cache, parent)

        val reply = chat(bob, 1_002, "reply", arrayOf(arrayOf("e", referenced.id), arrayOf("q", parent.id, "", alice.pubKey)))
        val note = AccountMarmotActions.indexMarmotInnerEvent(cache, reply).note

        assertEquals(listOf(parent.id), note.replyTo?.map { it.idHex })
        assertTrue(note in cache.getOrCreateNote(parent.id).replies)
        assertTrue(note !in cache.getOrCreateNote(referenced.id).replies, "the e is only a reference")
    }

    @Test
    fun aWhiteNoiseReplyWithOnlyAnEStillReplies() {
        val cache = EventCache()
        val parent = chat(alice, 1_000, "parent")
        AccountMarmotActions.indexMarmotInnerEvent(cache, parent)

        val reply = chat(bob, 1_001, "reply", arrayOf(arrayOf("e", parent.id)))
        val note = AccountMarmotActions.indexMarmotInnerEvent(cache, reply).note

        assertEquals(listOf(parent.id), note.replyTo?.map { it.idHex })
    }

    @Test
    fun aReplayLeavesTheLinkAsItWas() {
        val cache = EventCache()
        val referenced = chat(alice, 1_000, "referenced")
        val parent = chat(alice, 1_001, "parent")
        val reply = chat(bob, 1_002, "reply", arrayOf(arrayOf("e", referenced.id), arrayOf("q", parent.id)))

        AccountMarmotActions.indexMarmotInnerEvent(cache, reply)
        val note = AccountMarmotActions.indexMarmotInnerEvent(cache, reply).note

        assertEquals(listOf(parent.id), note.replyTo?.map { it.idHex })
        assertEquals(1, cache.getOrCreateNote(parent.id).replies.size)
    }

    @Test
    fun aKind9OutsideMarmotKeepsEveryLink() {
        val cache = EventCache()
        val referenced = chat(alice, 1_000, "referenced")
        val parent = chat(alice, 1_001, "parent")
        val reply = chat(bob, 1_002, "reply", arrayOf(arrayOf("e", referenced.id), arrayOf("q", parent.id)))

        cache.justConsume(reply, null, false)

        assertEquals(listOf(referenced.id, parent.id), cache.getOrCreateNote(reply.id).replyTo?.map { it.idHex })
    }
}
