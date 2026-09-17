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
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListTheme
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyle
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 37517, checked against the spec's Ren Fest example. */
class GeocacheCurationListEventTest {
    private val curator = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd"
    private val otherAuthor = "b".repeat(64)

    private fun list(vararg tags: Array<String>) = GeocacheCurationListEvent("id", curator, 1_748_619_568L, arrayOf(*tags), "Explore the grounds!", "sig")

    private fun Array<Array<String>>.values(name: String) = filter { it.isNotEmpty() && it[0] == name }.map { it[1] }

    @Test
    fun theKindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(GeocacheCurationListEvent.KIND))
    }

    @Test
    fun parsesTheSpecsCurationListExample() {
        val curated =
            list(
                arrayOf("d", "ren-fest-hunt-1748619568670"),
                arrayOf("title", "Texas Ren Fest Treasure Hunt"),
                arrayOf("description", "Find all the hidden treasures at the festival!"),
                arrayOf("image", "https://blossom.primal.net/banner-example.jpg"),
                arrayOf("g", "9vk"),
                arrayOf("g", "9vk5"),
                arrayOf("g", "9vk5b"),
                arrayOf("g", "9vk5b7"),
                arrayOf("theme", "adventure"),
                arrayOf("map", "adventure"),
                arrayOf("a", "37516:$curator:first-treasure-1748619568668"),
                arrayOf("a", "37516:$curator:verified-treasure-1748619568669"),
            )

        assertEquals("Texas Ren Fest Treasure Hunt", curated.title())
        assertEquals("Find all the hidden treasures at the festival!", curated.description())
        assertEquals("https://blossom.primal.net/banner-example.jpg", curated.image())
        assertEquals(ListTheme.ADVENTURE, curated.theme())
        assertEquals(MapStyle.ADVENTURE, curated.mapStyle())
        assertEquals(4, curated.geohashes().size)
        assertEquals(2, curated.geocaches().size)
        assertTrue(curated.isWellFormed())
    }

    @Test
    fun theOrderOfTheCachesIsPreserved() {
        // "Order is preserved and meaningful" — a trail rendered out of order is a different
        // trail.
        val curated =
            list(
                arrayOf("d", "x"),
                arrayOf("title", "t"),
                arrayOf("a", "37516:$curator:third"),
                arrayOf("a", "37516:$curator:first"),
                arrayOf("a", "37516:$curator:second"),
            )

        assertEquals(listOf("third", "first", "second"), curated.geocaches().map { it.dTag })
    }

    @Test
    fun aListCanSpanCachesFromSeveralAuthors() {
        val curated =
            list(
                arrayOf("d", "x"),
                arrayOf("title", "t"),
                arrayOf("a", "37516:$curator:mine"),
                arrayOf("a", "37516:$otherAuthor:theirs"),
            )

        assertEquals(listOf(curator, otherAuthor), curated.geocaches().map { it.pubKeyHex })
    }

    @Test
    fun anAddressThatIsNotACacheIsNotInTheItinerary() {
        // A list may reference anything; only the cache kinds belong in a treasure hunt.
        val curated =
            list(
                arrayOf("d", "x"),
                arrayOf("title", "t"),
                arrayOf("a", "30023:$curator:a-blog-post"),
                arrayOf("a", "37516:$curator:a-cache"),
            )

        assertEquals(listOf("a-cache"), curated.geocaches().map { it.dTag })
        assertEquals(2, curated.addresses().size)
    }

    @Test
    fun theUndefinedLegacyKindIsAcceptedOnRead() {
        // NIP-CC says a list references "kind 37516 or 37515" but never defines 37515. Readers
        // accept it; the builder never writes it.
        val curated = list(arrayOf("d", "x"), arrayOf("title", "t"), arrayOf("a", "37515:$curator:old-cache"))

        assertEquals(listOf("old-cache"), curated.geocaches().map { it.dTag })
    }

    @Test
    fun anUnknownThemeOrMapStyleIsNotSilentlyADefault() {
        val curated = list(arrayOf("theme", "brutalist"), arrayOf("map", "topographic"))

        assertNull(curated.theme())
        assertNull(curated.mapStyle())
        assertEquals("brutalist", curated.themeCode())
        assertEquals("topographic", curated.mapStyleCode())
    }

    @Test
    fun aListWithNoCachesIsNotWellFormed() {
        assertFalse(list(arrayOf("d", "x"), arrayOf("title", "t")).isWellFormed())
        assertFalse(list(arrayOf("d", "x"), arrayOf("a", "37516:$curator:c")).isWellFormed())
        assertFalse(list(arrayOf("title", "t"), arrayOf("a", "37516:$curator:c")).isWellFormed())
    }

    @Test
    fun aListOfOnlyNonCachesIsNotWellFormed() {
        // The `a` tag is there, so the tag-name gate passes; the list is still empty.
        assertFalse(list(arrayOf("d", "x"), arrayOf("title", "t"), arrayOf("a", "30023:$curator:post")).isWellFormed())
    }

    @Test
    fun buildProducesTheTagsTheSpecRequires() {
        val template =
            GeocacheCurationListEvent.build(
                title = "Texas Ren Fest Treasure Hunt",
                geocaches =
                    listOf(
                        Address(GeocacheListingEvent.KIND, curator, "first-treasure-1748619568668"),
                        Address(GeocacheListingEvent.KIND, curator, "verified-treasure-1748619568669"),
                    ),
                content = "Explore the festival grounds!",
                description = "Find all the hidden treasures at the festival!",
                image = "https://blossom.primal.net/banner-example.jpg",
                geohash = "9vk5b7xy",
                theme = ListTheme.ADVENTURE,
                mapStyle = MapStyle.ADVENTURE,
                dTag = "ren-fest-hunt-1748619568670",
                createdAt = 1_748_619_568L,
            )

        assertEquals(GeocacheCurationListEvent.KIND, template.kind)
        assertEquals(listOf("ren-fest-hunt-1748619568670"), template.tags.values("d"))
        assertEquals(listOf("Texas Ren Fest Treasure Hunt"), template.tags.values("title"))
        assertEquals(listOf("adventure"), template.tags.values("theme"))
        assertEquals(listOf("adventure"), template.tags.values("map"))
        assertEquals(
            listOf(
                "37516:$curator:first-treasure-1748619568668",
                "37516:$curator:verified-treasure-1748619568669",
            ),
            template.tags.values("a"),
        )
    }

    @Test
    fun aListsGeohashLadderStopsAtSix() {
        // A trail's centre point is not a place anybody walks to, so it is published coarser
        // than a listing's 3..9.
        val template =
            GeocacheCurationListEvent.build(
                title = "t",
                geocaches = listOf(Address(GeocacheListingEvent.KIND, curator, "c")),
                geohash = "9vk5b7xyz",
            )

        assertEquals(listOf("9vk", "9vk5", "9vk5b", "9vk5b7"), template.tags.values("g"))
    }
}
