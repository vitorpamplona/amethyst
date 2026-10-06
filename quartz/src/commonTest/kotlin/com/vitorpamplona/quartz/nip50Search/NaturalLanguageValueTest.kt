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
package com.vitorpamplona.quartz.nip50Search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Values taken from the kind 9999/39999 corpus on a Decentralized Lists relay. */
class NaturalLanguageValueTest {
    @Test
    fun phrasesAndNonAsciiTextAreNaturalLanguage() {
        listOf(
            "Social history",
            "Johan Huizinga",
            "Guevara, ernesto, 1928-1967",
            "Climbing The Mountain (1979-1990)",
            "[Live] Closer To Somewhere",
            "Song: Gold by Torcon 7",
            "刘慈欣",
            "老子",
            "Moyen Âge",
            "  padded phrase  ",
        ).forEach { assertTrue(isNaturalLanguageValue(it), it) }
    }

    @Test
    fun capitalizedWordsAreNaturalLanguage() {
        listOf("Fiction", "History", "Aristotle", "Plutarch", "RTL", "PromoDJ", "AT&T").forEach {
            assertTrue(isNaturalLanguageValue(it), it)
        }
    }

    @Test
    fun singleLowercaseOrMixedTokensAreNot() {
        listOf(
            "music",
            "eng",
            "openlibrary",
            "reference",
            "string",
            "forward",
            "IS_THE_CONCEPT_FOR",
            "concept-header",
            "tagassert--ol-ol49463w--science-fiction--6aca05b8",
            "OL19722168W",
            "2026-06-01",
            "sorita67",
            "Musician:",
        ).forEach { assertFalse(isNaturalLanguageValue(it), it) }
    }

    @Test
    fun machineValuesAreNotNaturalLanguageEvenWithSpaces() {
        listOf(
            "",
            "   ",
            "{\"word\": {\"slug\": \"ol-ol98624w\", \"name\": \"Waking with Enemies\"}}",
            "[\"title\", \"artist\"]",
            "Re:Zero",
            // URLs and addresses with unescaped spaces, as feeds publish them
            "https://wlvl.com/assets/images/podcasts/Century Podcast logo.jpg",
            "https://headstarts.uk/msp/longy/Katherines Wheel/katherines wheel.xml",
            "39998:2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331:first one no uuid",
            "ABCDEFABCDEFABCDEFABCDEFABCDEFAB",
        ).forEach { assertFalse(isNaturalLanguageValue(it), it) }
    }

    @Test
    fun machineValues() {
        listOf(
            "473",
            "-1",
            "0.5",
            "9781984820983",
            "057507681X",
            "https://covers.openlibrary.org/b/id/7245546-L.jpg",
            "wss://relay.damus.io",
            "tag:soundcloud,2010:tracks/711391888",
            "urn:isbn:9780679428329",
            "39999:6aca05b812da97601151776d13de04ae71afc9d86da1408f0e72cffef72ece4b:ol-ol2838774w",
            "39998:6aca05b812da97601151776d13de04ae71afc9d86da1408f0e72cffef72ece4b",
            "6aca05b812da97601151776d13de04ae71afc9d86da1408f0e72cffef72ece4b",
            "c65d75f4d058f4746e12e44441cacea0",
            "8ad7c296-67e9-5f57-ba52-2ff732e62e87",
            "npub1f5pre6wl6ad87vr4hr5wppqq30sh58m4p33mthnjreh03qadcajs7gwt3z",
            "https://wlvl.com/assets/images/podcasts/Century Podcast logo.jpg",
            "39998:2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331:first one no uuid",
            "",
        ).forEach { assertTrue(isMachineValue(it), it) }
    }

    @Test
    fun wordsAndPhrasesAreNotMachineValues() {
        listOf("history", "literary-fiction", "Song: Gold", "Fiction", "Paris, 1920", "1984 Orwell", "{ राधेय }Rishi Verma").forEach {
            assertFalse(isMachineValue(it), it)
        }
    }
}
