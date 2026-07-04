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
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BirdDetectionEventTest {
    // Real event from the wild (relay.ditto.pub), published by birdstar.app
    private fun sampleEvent(): Event =
        EventFactory.create(
            id = "2b1450b8886c997ff14ab57d46fc5a60929ab8bba3dc0c7f1ebfd5916e41a0cc",
            pubKey = "0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd",
            createdAt = 1_783_089_908L,
            kind = BirdDetectionEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("alt", "Bird detection: Purple Gallinule (Porphyrio martinica)"),
                    arrayOf("i", "https://www.wikidata.org/entity/Q27074644"),
                    arrayOf("n", "Porphyrio martinica"),
                    arrayOf("g", "9vk"),
                    arrayOf("client", "birdstar.app"),
                ),
            content = "",
            sig =
                "7a30bea2cb4b5b2a826448294bfae072d2948f02e3d259f824c7706fbc1e2a32" +
                    "5661efef9a63d5127e7fc26abbd15df55b98df9f44017f19de91616e97b5cad7",
        )

    @Test
    fun factoryBuildsBirdDetectionForKind2473() {
        val event = sampleEvent()
        assertTrue(
            event is BirdDetectionEvent,
            "Expected a BirdDetectionEvent but got ${event::class.simpleName}",
        )
    }

    @Test
    fun kind2473IsNowKnown() {
        assertTrue(EventFactory.isKnownKind(BirdDetectionEvent.KIND), "kind 2473 should be a known kind")
    }

    @Test
    fun parsesCommonNameFromAltTag() {
        val event = sampleEvent()
        assertIs<BirdDetectionEvent>(event)

        assertEquals("Purple Gallinule", event.commonName())
    }

    @Test
    fun commonNameIsNullWithoutAltTag() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = "00".repeat(32),
                createdAt = 1_783_089_908L,
                kind = BirdDetectionEvent.KIND,
                tags = arrayOf(arrayOf("n", "Porphyrio martinica")),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdDetectionEvent>(event)

        assertEquals(null, event.commonName())
    }

    @Test
    fun commonNameHandlesAltWithoutParenthetical() {
        val event: Event =
            EventFactory.create(
                id = "00".repeat(32),
                pubKey = "00".repeat(32),
                createdAt = 1_783_089_908L,
                kind = BirdDetectionEvent.KIND,
                tags = arrayOf(arrayOf("alt", "Bird detection: Purple Gallinule")),
                content = "",
                sig = "00".repeat(64),
            )
        assertIs<BirdDetectionEvent>(event)

        assertEquals("Purple Gallinule", event.commonName())
    }

    @Test
    fun parsesBirdDetectionFields() {
        val event = sampleEvent()
        assertIs<BirdDetectionEvent>(event)

        assertEquals("Porphyrio martinica", event.speciesName())
        assertEquals("https://www.wikidata.org/entity/Q27074644", event.speciesReference())
        assertEquals("Bird detection: Purple Gallinule (Porphyrio martinica)", event.summary())
        assertEquals(listOf("9vk"), event.geohashes())
    }
}
