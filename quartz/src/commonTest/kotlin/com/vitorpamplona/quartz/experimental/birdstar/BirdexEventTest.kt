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
}
