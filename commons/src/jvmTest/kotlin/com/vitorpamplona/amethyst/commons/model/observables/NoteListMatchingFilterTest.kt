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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.EventFactory
import java.util.TreeSet
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteListMatchingFilterTest {
    private val author = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"

    private val filter = Filter(kinds = listOf(AppDefinitionEvent.KIND))

    // Amethyst reuses a single AddressableNote instance per address (LocalCache),
    // so a newer replaceable event mutates createdAt on the SAME object.
    private fun noteFor(dTag: String) = AddressableNote(Address(AppDefinitionEvent.KIND, author, dTag))

    private fun appDefinition(
        dTag: String,
        createdAt: Long,
    ): Event =
        EventFactory.create(
            id = "%064x".format(createdAt),
            pubKey = author,
            createdAt = createdAt,
            kind = AppDefinitionEvent.KIND,
            tags = arrayOf(arrayOf("d", dTag)),
            content = "{}",
            sig = "00".repeat(64),
        )

    private fun AddressableNote.load(createdAt: Long) {
        event = appDefinition(dTag(), createdAt)
    }

    private fun newFilter(sink: (List<Note>) -> Unit) =
        NoteListMatchingFilter(
            filter = filter,
            atOnce = { TreeSet(CreatedAtIdHexComparator) },
            update = sink,
        )

    @Test
    fun newerVersionOfAnAddressableDoesNotDuplicateTheKey() {
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        // Three app definitions arrive. Their createdAt spread matters: after the
        // target moves, the sorted set's search path for the new key must be able
        // to bypass the stale node, which is what corrupts a createdAt-ordered set.
        val target = noteFor("nostr-dvm-labeler")
        val newer = noteFor("other-app")
        val newest = noteFor("top-app")

        target.load(1000)
        subject.new(target.event!!, target)
        newer.load(2000)
        subject.new(newer.event!!, newer)
        newest.load(4000)
        subject.new(newest.event!!, newest)
        assertEquals(3, last.size)

        // A newer definition replaces the event on the SAME target instance,
        // moving its createdAt from 1000 to 3000 (now between 2000 and 4000).
        // LocalCache then notifies the observer again. This must NOT insert the
        // note a second time.
        target.load(3000)
        subject.new(target.event!!, target)

        assertEquals(
            listOf(newest.idHex, newer.idHex, target.idHex).sorted(),
            last.map { it.idHex }.sorted(),
            "each addressable must appear exactly once",
        )
        assertEquals(last.size, last.map { it.idHex }.toSet().size, "no duplicate keys")
    }

    @Test
    fun removeDropsTheNoteEvenAfterCreatedAtChanged() {
        var last: List<Note> = emptyList()
        val subject = newFilter { last = it }
        subject.init()

        val target = noteFor("nostr-dvm-labeler")
        target.load(1000)
        subject.new(target.event!!, target)
        assertEquals(1, last.size)

        // The createdAt sort key moves before the delete arrives.
        target.load(2000)
        subject.remove(target)

        assertEquals(0, last.size, "remove must find the note despite the createdAt change")
    }
}
