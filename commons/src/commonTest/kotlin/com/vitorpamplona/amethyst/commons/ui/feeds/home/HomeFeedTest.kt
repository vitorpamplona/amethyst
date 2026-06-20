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
package com.vitorpamplona.amethyst.commons.ui.feeds.home

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedTest {
    private val author: HexKey = "a".repeat(64)
    private val stranger: HexKey = "b".repeat(64)
    private val sig: HexKey = "f".repeat(128)

    private fun id(c: Char) = c.toString().repeat(64)

    private fun rootNote(
        idChar: Char = '1',
        who: HexKey = author,
        createdAt: Long = 1000L,
        content: String = "hello",
    ) = TextNoteEvent(id(idChar), who, createdAt, emptyArray(), content, sig)

    private fun emptyHidden(
        hiddenUsers: Set<String> = emptySet(),
        mutedThreads: Set<String> = emptySet(),
    ) = LiveHiddenUsers(
        showSensitiveContent = null,
        hiddenWordsCase = emptyList(),
        hiddenUsersHashCodes = emptySet(),
        spammersHashCodes = emptySet(),
        hiddenUsers = hiddenUsers,
        mutedThreads = mutedThreads,
    )

    @Test
    fun renderableKind_acceptsTextNote_rejectsReaction() {
        assertTrue(rootNote().isHomeFeedRenderableKind())
        // Reactions (kind 7) are not home-feed content.
        assertFalse(ReactionEvent(id('7'), author, 1000L, emptyArray(), "+", sig).isHomeFeedRenderableKind())
        // A null event is never renderable.
        assertFalse((null as Event?).isHomeFeedRenderableKind())
    }

    @Test
    fun renderableKind_longFormNeedsContent() {
        assertFalse(LongTextNoteEvent(id('3'), author, 1000L, emptyArray(), "", sig).isHomeFeedRenderableKind())
        assertTrue(LongTextNoteEvent(id('4'), author, 1000L, emptyArray(), "body", sig).isHomeFeedRenderableKind())
    }

    @Test
    fun newThread_rootIsNewThread_replyIsNot() {
        assertTrue(rootNote().isNewThreadEvent())
        // A reply carries a root marker e-tag, so its thread root != its own id.
        val reply = TextNoteEvent(id('5'), author, 1001L, arrayOf(arrayOf("e", id('1'), "", "root")), "re", sig)
        assertFalse(reply.isNewThreadEvent())
    }

    @Test
    fun params_acceptsFollowedRoot() {
        val params = HomeFeedParams(setOf(author), emptyHidden())
        assertTrue(params.match(rootNote()))
    }

    @Test
    fun params_rejectsNonFollowedAuthor() {
        val params = HomeFeedParams(setOf(author), emptyHidden())
        assertFalse(params.match(rootNote(who = stranger)))
    }

    @Test
    fun params_rejectsMutedAuthor() {
        val params = HomeFeedParams(setOf(author), emptyHidden(hiddenUsers = setOf(author)))
        assertFalse(params.match(rootNote()))
    }

    @Test
    fun params_rejectsMutedThread() {
        // A root note is its own thread root, so muting its id mutes the thread.
        val params = HomeFeedParams(setOf(author), emptyHidden(mutedThreads = setOf(id('1'))))
        assertFalse(params.match(rootNote(idChar = '1')))
    }

    @Test
    fun params_rejectsFutureEvent() {
        val params = HomeFeedParams(setOf(author), emptyHidden())
        assertFalse(params.match(rootNote(createdAt = TimeUtils.now() + 3600)))
    }

    @Test
    fun sortedByHomeFeedOrder_dedupsAndOrdersNewestFirst() {
        val older = rootNote(idChar = '1', createdAt = 1000L)
        val newer = rootNote(idChar = '2', createdAt = 2000L)
        val sorted = listOf(older, newer, older).sortedByHomeFeedOrder()
        assertEquals(listOf(id('2'), id('1')), sorted.map { it.id })
    }

    @Test
    fun homeFeedKinds_includesCoreKinds() {
        assertTrue(TextNoteEvent.KIND in HomeFeedKinds)
        assertTrue(LongTextNoteEvent.KIND in HomeFeedKinds)
    }
}
