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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorsMissingOutboxTest : BaseDBTest() {
    @Test
    fun emptyStoreReturnsNoAuthors() =
        forEachDB { db ->
            assertEquals(emptySet(), db.authorsMissingOutbox().toSet())
        }

    @Test
    fun authorWithEventButNoOutboxIsMissing() =
        forEachDB { db ->
            val signer = NostrSignerSync()
            db.insert(signer.sign(TextNoteEvent.build("hello")))

            assertEquals(setOf(signer.pubKey), db.authorsMissingOutbox().toSet())
        }

    @Test
    fun authorWithOutboxIsNotMissing() =
        forEachDB { db ->
            val hasOutbox = NostrSignerSync()
            val noOutbox = NostrSignerSync()

            // Both authors have content; only one advertises a 10002.
            db.insert(hasOutbox.sign(TextNoteEvent.build("with relays")))
            db.insert(AdvertisedRelayListEvent.create(emptyList(), hasOutbox))
            db.insert(noOutbox.sign(TextNoteEvent.build("no relays")))

            assertEquals(setOf(noOutbox.pubKey), db.authorsMissingOutbox().toSet())
        }

    @Test
    fun authorKnownOnlyByTheirOutboxIsNotMissing() =
        forEachDB { db ->
            // The only stored event for this author IS the 10002. They must
            // not appear (the outer scan sees them, the NOT EXISTS excludes
            // them) — the anti-join is symmetric on the same table.
            val signer = NostrSignerSync()
            db.insert(AdvertisedRelayListEvent.create(emptyList(), signer))

            assertEquals(emptySet(), db.authorsMissingOutbox().toSet())
        }

    @Test
    fun outboxDeletedMakesAuthorMissingAgain() =
        forEachDB { db ->
            val signer = NostrSignerSync()
            db.insert(signer.sign(TextNoteEvent.build("content")))
            val relayList = AdvertisedRelayListEvent.create(emptyList(), signer)
            db.insert(relayList)

            assertEquals(emptySet(), db.authorsMissingOutbox().toSet())

            // NIP-09: the author deletes their own relay list. No 10002 row
            // remains, so the anti-join reports them as missing again.
            db.insert(signer.sign(DeletionEvent.build(listOf(relayList))))

            assertEquals(setOf(signer.pubKey), db.authorsMissingOutbox().toSet())
        }

    @Test
    fun mixOfAuthorsReportsOnlyThoseWithoutOutbox() =
        forEachDB { db ->
            val a = NostrSignerSync()
            val b = NostrSignerSync()
            val c = NostrSignerSync()

            db.insert(a.sign(TextNoteEvent.build("a1")))
            db.insert(a.sign(TextNoteEvent.build("a2")))
            db.insert(AdvertisedRelayListEvent.create(emptyList(), a))

            db.insert(b.sign(TextNoteEvent.build("b1")))

            db.insert(c.sign(TextNoteEvent.build("c1")))
            db.insert(AdvertisedRelayListEvent.create(emptyList(), c))

            assertEquals(setOf(b.pubKey), db.authorsMissingOutbox().toSet())
        }
}
