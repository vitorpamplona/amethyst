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
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import java.util.TreeSet
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteListMatchingFilterTest {
    private val pubKey = "d0d0a746b44c9de8422165aef520b1fe041eedf5794f7592505477eeac122c18"
    private val author = User(pubKey, UserContext { Note("placeholder") })

    private fun appDefinition(
        dTag: String,
        id: String,
        createdAt: Long,
    ) = AppDefinitionEvent(
        id = id,
        pubKey = pubKey,
        createdAt = createdAt,
        content = "{\"name\":\"$dTag\"}",
        sig = "00",
        tags = arrayOf(arrayOf("d", dTag), arrayOf("k", "5300")),
    )

    private fun loadedNote(
        dTag: String,
        id: String,
        createdAt: Long,
    ): AddressableNote =
        AddressableNote(Address(AppDefinitionEvent.KIND, pubKey, dTag)).also {
            it.loadEvent(appDefinition(dTag, id, createdAt), author, emptyList())
        }

    private fun newFilter(
        seed: Collection<Note>,
        onUpdate: (List<Note>) -> Unit,
    ): NoteListMatchingFilter {
        val filter =
            NoteListMatchingFilter(
                filter = Filter(kinds = listOf(AppDefinitionEvent.KIND)),
                atOnce = { TreeSet(CreatedAtIdHexComparator).apply { addAll(seed) } },
                update = onUpdate,
            )
        filter.init()
        return filter
    }

    /**
     * A DVM re-announcing its kind-31990 definition mutates the SAME AddressableNote
     * in place (bumping created_at) and re-notifies the observer. When that bump moves
     * the note past the others, a set ordered by the now-stale created_at no longer
     * finds the existing node and inserts a second one for the same address — the list
     * then carried the address twice and a LazyColumn keyed by idHex crashed with
     * "Key ... was already used". The emitted list must keep every address exactly once.
     */
    @Test
    fun addressableUpdateThatReordersDoesNotDuplicate() {
        // labeler starts at the BACK of the list (lowest created_at).
        val labeler = loadedNote("nostr-dvm-labeler", id = "aa", createdAt = 100)
        val decoys =
            listOf(
                loadedNote("dvm-a", id = "a1", createdAt = 200),
                loadedNote("dvm-b", id = "b1", createdAt = 300),
                loadedNote("dvm-c", id = "c1", createdAt = 400),
                loadedNote("dvm-d", id = "d1", createdAt = 500),
            )

        var emitted: List<Note> = emptyList()
        val filter = newFilter(decoys + labeler) { emitted = it }
        assertEquals(5, emitted.size)

        // Newer version: LocalCache swaps the event on the same note with a higher
        // created_at (now the FRONT), then re-notifies with that same instance.
        labeler.loadEvent(appDefinition("nostr-dvm-labeler", id = "bb", createdAt = 999), author, emptyList())
        filter.new(labeler.event as AppDefinitionEvent, labeler)

        assertNoDuplicateAddresses(emitted)
        assertEquals(5, emitted.size)
    }

    /**
     * The DVM heartbeat pattern: many re-announcements, each bumping created_at. The
     * list must never accumulate duplicates no matter how the reordering shakes out.
     */
    @Test
    fun repeatedReorderingUpdatesStayDeduplicated() {
        val labeler = loadedNote("nostr-dvm-labeler", id = "v0", createdAt = 100)
        val decoys =
            (1..4).map { loadedNote("dvm-$it", id = "d$it", createdAt = 100L + it * 100) }

        var emitted: List<Note> = emptyList()
        val filter = newFilter(decoys + labeler) { emitted = it }

        // Alternately bump above and below the decoys to force reordering both ways.
        repeat(30) { i ->
            val createdAt = if (i % 2 == 0) 900L + i else 10L + i
            labeler.loadEvent(appDefinition("nostr-dvm-labeler", id = "v${i + 1}", createdAt = createdAt), author, emptyList())
            filter.new(labeler.event as AppDefinitionEvent, labeler)
        }

        assertNoDuplicateAddresses(emitted)
        assertEquals(5, emitted.size)
    }

    private fun assertNoDuplicateAddresses(list: List<Note>) {
        assertEquals(
            list.map { it.idHex },
            list.map { it.idHex }.distinct(),
            "Emitted feed contains a duplicate idHex (would crash a LazyColumn keyed by idHex)",
        )
    }
}
