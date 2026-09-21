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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationRevision
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adding a cache to a hunt that already exists.
 *
 * The order of a curation list's `a` tags is its content — NIP-CC makes it meaningful — so the
 * tests that matter are the ones about position and about what survives the republish.
 */
class GeocacheCurationRevisionTest {
    private val owner = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private fun cache(d: String) = Address(GeocacheListingEvent.KIND, owner, d)

    private fun hunt(extra: List<Array<String>> = emptyList()): GeocacheCurationListEvent {
        val tags =
            (
                listOf(
                    arrayOf("d", "lisbon-miradouros"),
                    arrayOf("title", "Lisbon Miradouros"),
                    arrayOf("a", cache("graca").toValue()),
                    arrayOf("a", cache("senhora-do-monte").toValue()),
                ) + extra
            ).toTypedArray()

        return GeocacheCurationListEvent("id", owner, 1_748_619_568L, tags, "a walk along the viewpoints", "sig")
    }

    private fun reparse(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<GeocacheCurationListEvent>) = GeocacheCurationListEvent("id2", owner, template.createdAt, template.tags, template.content, "sig")

    @Test
    fun theNewCacheLandsAtTheEnd() {
        val revision = GeocacheCurationRevision.withCache(hunt(), cache("santa-catarina"))
        val reparsed = reparse(revision)

        // Appending is the only placement that needs no opinion about where in someone's route
        // the new stop belongs.
        assertEquals(
            listOf("graca", "senhora-do-monte", "santa-catarina"),
            reparsed.geocaches().map { it.dTag },
        )
    }

    @Test
    fun addingACacheTwiceDoesNotDuplicateIt() {
        val revision = GeocacheCurationRevision.withCache(hunt(), cache("graca"))
        val reparsed = reparse(revision)

        assertEquals(2, reparsed.geocaches().size)
        assertEquals(listOf("graca", "senhora-do-monte"), reparsed.geocaches().map { it.dTag })
    }

    @Test
    fun containsAnswersWhatTheSheetAsks() {
        assertTrue(GeocacheCurationRevision.contains(hunt(), cache("graca")))
        assertFalse(GeocacheCurationRevision.contains(hunt(), cache("santa-catarina")))
    }

    @Test
    fun aRevisionKeepsTagsQuartzDoesNotModel() {
        val withForeign =
            hunt(
                listOf(
                    arrayOf("client", "Treasures"),
                    arrayOf("image", "https://example.com/banner.jpg"),
                ),
            )

        val revision = GeocacheCurationRevision.withCache(withForeign, cache("santa-catarina"))

        assertTrue(revision.tags.any { it.size > 1 && it[0] == "client" && it[1] == "Treasures" })
        assertTrue(revision.tags.any { it.size > 1 && it[0] == "image" })
    }

    @Test
    fun theAddressSurvivesSoTheHuntIsReplacedRatherThanForked() {
        val original = hunt()
        val reparsed = reparse(GeocacheCurationRevision.withCache(original, cache("santa-catarina")))

        assertEquals(original.address().toValue(), reparsed.address().toValue())
        assertEquals("Lisbon Miradouros", reparsed.title())
    }
}
