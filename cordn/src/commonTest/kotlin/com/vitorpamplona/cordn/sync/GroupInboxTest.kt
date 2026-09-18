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
package com.vitorpamplona.cordn.sync

import com.vitorpamplona.cordn.spec00Coordinator.GroupMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The ingestion rules, which are the part of cordn where a mistake is silent.
 *
 * Every wrong answer here produces a client that works until it doesn't: an
 * epoch applied twice desynchronizes MLS several commits later, and a cursor
 * that refuses to advance stalls a group forever with no error anywhere.
 */
class GroupInboxTest {
    private var cursor = 0L

    private fun msg(
        sealed: String,
        gid: String = "g1",
    ) = GroupMessage(gid = gid, cursor = ++cursor, sealedBase64 = sealed, at = 1_700_000_000L + cursor)

    @Test
    fun ourOwnCommitComesBackAsConfirmationNotAsWork() {
        // The failure this prevents: feeding our own Commit through MLS again
        // advances the epoch twice, and nothing complains until messages stop
        // decrypting several epochs later.
        val inbox = GroupInbox()
        val commit = msg("Y29tbWl0")
        inbox.expectEcho(PendingEpochOperation(commit.sealedBase64, localStateApplied = true))

        assertIs<Ingestion.SelfEchoConfirmed>(inbox.accept(commit))
    }

    @Test
    fun anUnappliedCommitEchoIsWorkNotConfirmation() {
        // Crash recovery: we posted, the coordinator took it, we died before
        // adopting the new epoch. That echo is the only copy of the Commit we
        // will ever be handed.
        val inbox = GroupInbox()
        val commit = msg("Y29tbWl0")
        inbox.expectEcho(PendingEpochOperation(commit.sealedBase64, localStateApplied = false))

        assertIs<Ingestion.SelfEchoUnapplied>(inbox.accept(commit))
    }

    @Test
    fun echoesAreMatchedOnCiphertextNotOnCursor() {
        // spec/03.md §4 requires a fresh nonce per payload, so the sealed form
        // is unique to one posting. Matching on cursor would break the moment a
        // coordinator renumbered; matching on plaintext would mean decrypting
        // our own traffic to recognise it.
        val inbox = GroupInbox()
        val mine = msg("bWluZQ==")
        inbox.expectEcho(PendingEpochOperation(mine.sealedBase64))

        // Same cursor space, different bytes: somebody else's Commit.
        assertIs<Ingestion.Process>(inbox.accept(msg("dGhlaXJz")))
        // Ours, whenever it shows up.
        assertIs<Ingestion.SelfEchoConfirmed>(inbox.accept(mine.copy(cursor = ++cursor)))
    }

    @Test
    fun anEchoIsConsumedOnceSoAReplayIsStillProcessed() {
        // A coordinator that served the same record twice must not be able to
        // make us skip a genuine Commit that happens to repeat bytes.
        val inbox = GroupInbox()
        val commit = msg("Y29tbWl0")
        inbox.expectEcho(PendingEpochOperation(commit.sealedBase64))

        assertIs<Ingestion.SelfEchoConfirmed>(inbox.accept(commit))
        assertIs<Ingestion.Process>(inbox.accept(commit.copy(cursor = ++cursor)))
    }

    @Test
    fun theCursorAdvancesOnEveryOutcome() {
        // Including the ones that do no work. A cursor that only moved for
        // messages we processed would re-fetch every skipped one forever.
        val inbox = GroupInbox()
        val mine = msg("bWluZQ==")
        inbox.expectEcho(PendingEpochOperation(mine.sealedBase64))
        inbox.accept(mine)
        assertEquals(mine.cursor, inbox.cursor.fetchCursor)

        val own = msg("b3du")
        inbox.recordOwnMessage(own.cursor)
        inbox.accept(own)
        assertEquals(own.cursor, inbox.cursor.fetchCursor)

        val theirs = msg("dGhlaXJz")
        inbox.accept(theirs)
        assertEquals(theirs.cursor, inbox.cursor.fetchCursor)
    }

    @Test
    fun anUndecryptableMessageStillAdvancesTheCursor() {
        // A message sealed under an epoch we never had is unreadable forever.
        // Refusing to move past it stalls the group behind it permanently.
        val inbox = GroupInbox()
        val stale = msg("dW5yZWFkYWJsZQ==")
        assertIs<Ingestion.Process>(inbox.accept(stale))
        inbox.skipUnprocessable(stale.cursor)
        assertEquals(stale.cursor, inbox.cursor.fetchCursor)
    }

    @Test
    fun lastCursorNeverGoesBackwards() {
        val inbox = GroupInbox()
        inbox.accept(msg("YQ==").copy(cursor = 10))
        inbox.accept(msg("Yg==").copy(cursor = 4))
        assertEquals(10L, inbox.cursor.lastCursor, "lastCursor is a high-water mark")
        assertEquals(4L, inbox.cursor.fetchCursor, "fetchCursor follows delivery order")
    }

    @Test
    fun aFirstFetchSendsNoCursorAtAll() {
        // The coordinator's schema types `after` as a POSITIVE int, so 0 is not
        // "from the beginning" -- it is out of range.
        assertEquals(null, GroupCursor().afterOrNull())
        assertEquals(7L, GroupCursor(fetchCursor = 7).afterOrNull())
    }

    @Test
    fun ourOwnApplicationMessagesAreRecognisedByCursor() {
        val inbox = GroupInbox()
        val sent = msg("aGVsbG8=")
        inbox.recordOwnMessage(sent.cursor)
        assertIs<Ingestion.OwnMessage>(inbox.accept(sent))
        assertTrue(inbox.pending().isEmpty())
    }
}
