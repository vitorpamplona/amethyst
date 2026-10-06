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
package com.vitorpamplona.quartz.nip58Badges

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AcceptedBadgeSetSearchTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private fun set(
        vararg tags: Array<String>,
        content: String = "",
    ) = AcceptedBadgeSetEvent(
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

    @Test
    fun badgeSetIndexesTitleAndDescription() {
        val event = set(arrayOf("d", "conferences"), arrayOf("title", "Conferences"), arrayOf("description", "Badges from events I attended"), arrayOf("a", "30009:$b:nostrasia"), arrayOf("e", eventId))
        assertEquals("Conferences\nBadges from events I attended", event.indexableContent())
        assertEquals(event.indexableContent(), rejoin(event))

        val fields = assertIs<IndexableFields.Tiered>(SearchFieldExtractor.extract(event))
        assertEquals(listOf("Conferences"), fields.primary)
        assertEquals(listOf("Badges from events I attended"), fields.secondary)
    }

    @Test
    fun factoryBuildsASearchableEvent() {
        assertIs<SearchableEvent>(EventFactory.create<AcceptedBadgeSetEvent>("00".repeat(32), a, 1, AcceptedBadgeSetEvent.KIND, emptyArray(), "", "00".repeat(64)))
    }
}
