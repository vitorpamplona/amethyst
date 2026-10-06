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
package com.vitorpamplona.quartz.nip52Calendar

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CalendarSlotSearchTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private fun dateSlot(
        vararg tags: Array<String>,
        content: String = "",
    ) = CalendarDateSlotEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun timeSlot(
        vararg tags: Array<String>,
        content: String = "",
    ) = CalendarTimeSlotEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun rejoin(event: SearchableEvent): String {
        val fields = mutableListOf<String>()
        event.forEachIndexableField {
            if (it != null) fields.add(it)
            true
        }
        return fields.joinToString(event.indexableSeparator())
    }

    private val tags =
        arrayOf(
            arrayOf("d", "party"),
            arrayOf("title", "Nostr meetup"),
            arrayOf("summary", "Drinks and demos"),
            arrayOf("location", "Lisbon"),
            arrayOf("location", "Cafe Nostr"),
            arrayOf("t", "nostr"),
        )

    @Test
    fun slotsIndexLocationsAndTopics() {
        listOf<SearchableEvent>(dateSlot(*tags, content = "Bring a laptop"), timeSlot(*tags, content = "Bring a laptop")).forEach { event ->
            assertEquals("Nostr meetup\nDrinks and demos\nBring a laptop\nLisbon\nCafe Nostr\nnostr", event.indexableContent())
            assertEquals(event.indexableContent(), rejoin(event))

            // the extractor keeps locations and hashtags in their own roles, not the body
            val fields = assertIs<IndexableFields.Tiered>(SearchFieldExtractor.extract(event as com.vitorpamplona.quartz.nip01Core.core.Event))
            assertEquals("Bring a laptop", fields.text)
            assertEquals(listOf("Lisbon", "Cafe Nostr"), fields.locations)
            assertEquals(listOf("nostr"), fields.hashtags)
        }
    }

    @Test
    fun emptyContentStaysInTheJoin() {
        val event = timeSlot(arrayOf("d", "x"), arrayOf("location", "Lisbon"))
        assertEquals("\nLisbon", event.indexableContent())
        assertEquals(event.indexableContent(), rejoin(event))
        assertNull(event.title())
    }
}
