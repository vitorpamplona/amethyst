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
package com.vitorpamplona.amethyst.commons.drafts

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.supersedes
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftDeletionTest {
    private val signer = NostrSignerInternal(KeyPair())

    private suspend fun draftAt(createdAt: Long): DraftWrapEvent {
        val rumor = signer.sign<Event>(createdAt, 14, emptyArray(), "hello")
        return DraftWrapEvent.create("draft-tag", rumor, signer, createdAt)
    }

    @Test
    fun aMessageSentInTheSameSecondAsTheLastAutoSaveStillReplacesTheDraft() =
        runTest {
            val draft = draftAt(1_792_620_399L)

            val signed = signDraftDeletion(draft, signer, now = draft.createdAt)

            assertTrue(signed.emptied.createdAt > draft.createdAt)
            assertTrue(signed.emptied.supersedes(draft), "a relay would keep the draft over its empty replacement")
            assertEquals(signed.emptied.createdAt, signed.deletion.createdAt)
        }

    @Test
    fun theDeletionNamesTheDraftByIdAndByAddress() =
        runTest {
            val draft = draftAt(1_792_620_399L)

            val deletion = signDraftDeletion(draft, signer, now = draft.createdAt + 30).deletion

            assertEquals(listOf(draft.id), deletion.deleteEventIds())
            assertEquals(listOf(draft.addressTag()), deletion.deleteAddressIds())
            assertTrue(DeletionIndex().also { it.add(deletion, true) }.hasBeenDeleted(draft))
        }

    @Test
    fun aDeletionIsNotBackdated() =
        runTest {
            val draft = draftAt(1_792_620_399L)

            assertEquals(draft.createdAt + 30, signDraftDeletion(draft, signer, now = draft.createdAt + 30).emptied.createdAt)
        }
}
