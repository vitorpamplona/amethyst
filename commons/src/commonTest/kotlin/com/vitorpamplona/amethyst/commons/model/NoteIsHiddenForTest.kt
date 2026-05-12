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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Note.isHiddenFor() focusing on the mutedThreads check.
 *
 * Fixture approach:
 * - TextNoteEvent is constructed directly via its primary constructor (no signing needed —
 *   the sig field is not validated in isHiddenFor).
 * - A Note is created with the event's id, then note.event is set.
 * - LiveHiddenUsers is a plain data class — no Android plumbing required.
 */
class NoteIsHiddenForTest {
    // 64-char hex constants
    private val rootId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val replyId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private val authorPubKey = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

    // Minimal LiveHiddenUsers with all filters off (used as a baseline)
    private val noHidden =
        LiveHiddenUsers(
            showSensitiveContent = null,
            hiddenWordsCase = emptyList(),
            hiddenUsersHashCodes = emptySet(),
            spammersHashCodes = emptySet(),
            mutedThreads = emptySet(),
        )

    /**
     * Build a TextNoteEvent.
     *
     * @param id The event id (64 hex chars).
     * @param eTags Raw `e` tag arrays included in the event's tags.
     */
    private fun textNoteEvent(
        id: String,
        pubKey: String = authorPubKey,
        eTags: Array<Array<String>> = emptyArray(),
    ) = TextNoteEvent(
        id = id,
        pubKey = pubKey,
        createdAt = 1_700_000_000L,
        tags = eTags,
        content = "hello",
        sig = "sig",
    )

    /** A marked `e` tag array with marker="root" at index 3. */
    private fun rootETag(eventId: String) = arrayOf("e", eventId, "", "root")

    // -------------------------------------------------------------------------

    @Test
    fun reply_inMutedThread_isHidden() {
        // A reply whose NIP-10 root points to `rootId`, which is muted.
        val event =
            textNoteEvent(
                id = replyId,
                eTags = arrayOf(rootETag(rootId)),
            )
        val note = Note(replyId).also { it.event = event }

        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertTrue(note.isHiddenFor(choices), "Reply inside a muted thread must be hidden")
    }

    @Test
    fun topLevelNote_ownIdMuted_isHidden() {
        // A top-level note (no e-tags) whose own id is in mutedThreads.
        val event =
            textNoteEvent(
                id = rootId,
                eTags = emptyArray(),
            )
        val note = Note(rootId).also { it.event = event }

        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertTrue(note.isHiddenFor(choices), "Top-level note whose id is muted must be hidden")
    }

    @Test
    fun note_inUnmutedThread_isNotHidden() {
        // A reply note whose root is NOT in mutedThreads, author not hidden.
        val otherRoot = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val event =
            textNoteEvent(
                id = replyId,
                eTags = arrayOf(rootETag(otherRoot)),
            )
        val note = Note(replyId).also { it.event = event }

        // rootId is muted, but this note's root is otherRoot
        val choices = noHidden.copy(mutedThreads = setOf(rootId))

        assertFalse(note.isHiddenFor(choices), "Reply in an un-muted thread must not be hidden")
    }

    @Test
    fun authorHidden_isHidden_regression() {
        // Regression guard: author-hidden notes must still return true.
        val event = textNoteEvent(id = replyId)
        val note = Note(replyId).also { it.event = event }

        // Put the author's pubKey hashCode into hiddenUsersHashCodes
        val choices =
            noHidden.copy(
                hiddenUsersHashCodes = setOf(authorPubKey.hashCode()),
            )

        assertTrue(note.isHiddenFor(choices), "Note whose author is hidden must still be hidden")
    }
}
