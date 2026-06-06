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
package com.vitorpamplona.amethyst.desktop.filters

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopProfileFeedFilter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfileFeedFilterTest {
    private val author = "0000000000000000000000000000000000000000000000000000000000000001"
    private val parentId = "1111111111111111111111111111111111111111111111111111111111111111"
    private val cache = DesktopLocalCache()

    private fun user(hex: String): User = User(hex) { addr -> Note(addr.toValue()) }

    private fun textNote(
        id: String,
        pubkey: String,
        tags: Array<Array<String>>,
        content: String = "hi",
    ): TextNoteEvent = TextNoteEvent(id, pubkey, 0L, tags, content, "")

    private fun replyNote(id: String): Note {
        val u = user(author)
        val event =
            textNote(
                id,
                author,
                arrayOf(arrayOf("e", parentId, "", "reply"), arrayOf("p", author)),
            )
        val n = Note(id)
        n.loadEvent(event, u, listOf(Note(parentId)))
        return n
    }

    private fun rootNote(id: String): Note {
        val u = user(author)
        val event = textNote(id, author, emptyArray())
        val n = Note(id)
        n.loadEvent(event, u, emptyList())
        return n
    }

    @Test
    fun repliesOnly_includesReply() {
        val filter = DesktopProfileFeedFilter(author, cache, repliesOnly = true)
        val reply = replyNote("aa")
        val result = filter.applyFilter(setOf(reply))
        assertEquals(setOf(reply), result)
    }

    @Test
    fun repliesOnly_excludesRoot() {
        val filter = DesktopProfileFeedFilter(author, cache, repliesOnly = true)
        val root = rootNote("aa")
        val result = filter.applyFilter(setOf(root))
        assertTrue("Root post must NOT be in Replies tab", result.isEmpty())
    }

    @Test
    fun repliesOnly_excludesOtherAuthor() {
        val filter = DesktopProfileFeedFilter(author, cache, repliesOnly = true)
        val otherAuthor = "0000000000000000000000000000000000000000000000000000000000000002"
        val u = user(otherAuthor)
        val event =
            textNote(
                "bb",
                otherAuthor,
                arrayOf(arrayOf("e", parentId, "", "reply"), arrayOf("p", author)),
            )
        val n = Note("bb")
        n.loadEvent(event, u, listOf(Note(parentId)))
        val result = filter.applyFilter(setOf(n))
        assertTrue("Reply by another author must be excluded", result.isEmpty())
    }

    @Test
    fun notesMode_includesBoth() {
        val filter = DesktopProfileFeedFilter(author, cache, repliesOnly = false)
        val root = rootNote("aa")
        val reply = replyNote("bb")
        val result = filter.applyFilter(setOf(root, reply))
        assertEquals(setOf(root, reply), result)
    }

    /**
     * Regression: an unmarked e-tag is the legacy positional NIP-10 form,
     * but modern clients use it for QUOTES/MENTIONS, not replies. The Replies
     * tab must NOT include such posts. The looser `!isNewThread()` check
     * (which Android's conversations filter uses) would falsely include them.
     */
    @Test
    fun repliesOnly_excludesQuoteWithUnmarkedETag() {
        val filter = DesktopProfileFeedFilter(author, cache, repliesOnly = true)
        val u = user(author)
        val event =
            textNote(
                "cc",
                author,
                // Unmarked e-tag — typical of an inline `nostr:note1...` quote.
                arrayOf(arrayOf("e", parentId)),
            )
        val n = Note("cc")
        // Cache would populate replyTo from tagsWithoutCitations(), which
        // includes unmarked tags. Mirror that here.
        n.loadEvent(event, u, listOf(Note(parentId)))
        val result = filter.applyFilter(setOf(n))
        assertTrue("Quote (unmarked e-tag) must NOT appear in Replies tab", result.isEmpty())
    }

    @Test
    fun feedKeysDifferByMode() {
        val notes = DesktopProfileFeedFilter(author, cache, repliesOnly = false)
        val replies = DesktopProfileFeedFilter(author, cache, repliesOnly = true)
        assertFalse(notes.feedKey() == replies.feedKey())
    }
}
