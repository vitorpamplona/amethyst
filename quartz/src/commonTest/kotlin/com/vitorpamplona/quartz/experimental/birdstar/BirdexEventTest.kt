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
package com.vitorpamplona.quartz.experimental.birdstar

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirdexEventTest {
    private fun sampleEvent(): Event =
        EventFactory.create(
            id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
            pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
            createdAt = 1_780_836_939L,
            kind = BirdexEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("alt", "Birdex: 3 species"),
                    arrayOf("i", "https://www.wikidata.org/entity/Q805774"),
                    arrayOf("n", "Icterus galbula"),
                    arrayOf("i", "https://www.wikidata.org/entity/Q738534"),
                    arrayOf("n", "Baeolophus bicolor"),
                    arrayOf("i", "https://www.wikidata.org/entity/Q829683"),
                    arrayOf("n", "Mimus polyglottos"),
                    arrayOf("client", "birdstar.app"),
                ),
            content = "",
            sig = "00".repeat(64),
        )

    @Test
    fun factoryBuildsBirdexForKind12473() {
        val event = sampleEvent()
        assertTrue(
            event is BirdexEvent,
            "Expected a BirdexEvent but got ${event::class.simpleName}",
        )
    }

    @Test
    fun kind12473IsNowKnown() {
        assertTrue(EventFactory.isKnownKind(BirdexEvent.KIND), "kind 12473 should be a known kind")
    }

    @Test
    fun parsesBirdexFields() {
        val event = sampleEvent()
        assertIs<BirdexEvent>(event)

        assertEquals(3, event.speciesCount())
        assertEquals(
            listOf("Icterus galbula", "Baeolophus bicolor", "Mimus polyglottos"),
            event.speciesNames(),
        )
        assertEquals("Birdex: 3 species", event.summary())
    }

    @Test
    fun pairsEachSpeciesWithItsReference() {
        val event = sampleEvent()
        assertIs<BirdexEvent>(event)

        val species = event.species()
        assertEquals(3, species.size)
        assertEquals("Icterus galbula", species[0].name)
        assertEquals("https://www.wikidata.org/entity/Q805774", species[0].reference)
        assertEquals("Baeolophus bicolor", species[1].name)
        assertEquals("https://www.wikidata.org/entity/Q738534", species[1].reference)
        assertEquals("Mimus polyglottos", species[2].name)
        assertEquals("https://www.wikidata.org/entity/Q829683", species[2].reference)
    }

    /**
     * Birdstar writes the `i` first, but the pairing is positional, so a `n`
     * followed by its `i` must bind the same way. An `i` that is not a web URL
     * is dropped (a UI could not open it), as is a name with no `i` at all.
     */
    @Test
    fun pairsSpeciesRegardlessOfTagOrderAndDropsUnopenableReferences() {
        val event: Event =
            EventFactory.create(
                id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
                pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
                createdAt = 1_780_836_939L,
                kind = BirdexEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("alt", "Birdex: 4 species"),
                        arrayOf("n", "Icterus galbula"),
                        arrayOf("i", "https://www.wikidata.org/entity/Q805774"),
                        arrayOf("n", "Baeolophus bicolor"),
                        arrayOf("i", "isbn:9780307957894"),
                        arrayOf("n", "Mimus polyglottos"),
                        arrayOf("n", "Sturnus vulgaris"),
                        arrayOf("client", "birdstar.app"),
                    ),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdexEvent>(event)

        val species = event.species()
        assertEquals(
            listOf("Icterus galbula", "Baeolophus bicolor", "Mimus polyglottos", "Sturnus vulgaris"),
            species.map { it.name },
        )
        assertEquals("https://www.wikidata.org/entity/Q805774", species[0].reference)
        assertNull(species[1].reference, "a non-web `i` is not a link")
        assertNull(species[2].reference, "the dropped `i` must not leak onto the next name")
        assertNull(species[3].reference, "a name with no `i` has no reference")
    }

    /**
     * An `i` that is not next to a name is not that name's reference: a Birdex
     * could carry a NIP-73 identity for the event itself, and pairing it with
     * the next species would send the reader to the wrong page.
     */
    @Test
    fun doesNotPairAnIsolatedReferenceWithADistantName() {
        val event: Event =
            EventFactory.create(
                id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
                pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
                createdAt = 1_780_836_939L,
                kind = BirdexEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("i", "https://birdstar.app/lists/42"),
                        arrayOf("alt", "Birdex: 1 species"),
                        arrayOf("n", "Bubo bubo"),
                    ),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdexEvent>(event)

        val species = event.species()
        assertEquals(1, species.size)
        assertEquals("Bubo bubo", species[0].name)
        assertNull(species[0].reference, "an `i` two tags away is not this name's reference")
    }

    @Test
    fun countsSpeciesWithoutWalkingTheNameList() {
        val event: Event =
            EventFactory.create(
                id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
                pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
                createdAt = 1_780_836_939L,
                kind = BirdexEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("n", "Bubo bubo"),
                        arrayOf("n"),
                        arrayOf("i", "https://www.wikidata.org/entity/Q25469"),
                        arrayOf("n", "Sturnus vulgaris"),
                    ),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdexEvent>(event)

        assertEquals(2, event.speciesCount(), "a valueless `n` tag is not a species")
        assertEquals(event.speciesNames().size, event.speciesCount())
    }

    @Test
    fun toleratesEmptyAndValuelessTags() {
        val event: Event =
            EventFactory.create(
                id = "a099d4db563041bb289d3704f983fc148fc805860303a4f479a8264dc6a2d7cc",
                pubKey = "932614571afcbad4d17a191ee281e39eebbb41b93fac8fd87829622aeb112f4d",
                createdAt = 1_780_836_939L,
                kind = BirdexEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("n"),
                        arrayOf("i"),
                        arrayOf("i", "https://www.wikidata.org/entity/Q805774"),
                        arrayOf("n", "Icterus galbula"),
                    ),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdexEvent>(event)

        val species = event.species()
        assertEquals(1, species.size)
        assertEquals("Icterus galbula", species[0].name)
        assertEquals("https://www.wikidata.org/entity/Q805774", species[0].reference)
    }
}
