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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TransformedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The offset mapping is the one part of the field that Compose can crash on: it asks the mapping
 * where the caret goes on every keystroke, selection and click, and an answer outside the
 * transformed string throws. So every offset of every fixture is walked here, in both directions.
 */
class SearchTokenTransformationTest {
    private companion object {
        const val NPUB = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        val STYLES =
            SearchTokenStyles(
                person = SpanStyle(),
                pointer = SpanStyle(),
                hashtag = SpanStyle(),
                date = SpanStyle(),
                label = SpanStyle(),
                scope = SpanStyle(),
                group = SpanStyle(),
            )
    }

    private fun transform(
        text: String,
        caret: Int? = null,
        name: (String) -> String? = { null },
        groupName: (String) -> String? = { null },
        scopeName: (String, String) -> String? = { _, _ -> null },
    ): TransformedText = SearchTokenTransformation(caret, STYLES, name, groupName, scopeName).filter(AnnotatedString(text))

    /** Every offset must map into the other string's bounds, in both directions. */
    private fun assertMapsSafely(
        text: String,
        caret: Int? = null,
        name: (String) -> String? = { null },
        groupName: (String) -> String? = { null },
        scopeName: (String, String) -> String? = { _, _ -> null },
    ) {
        val out = transform(text, caret, name, groupName, scopeName)
        val drawn = out.text.text
        (0..text.length).forEach {
            val mapped = out.offsetMapping.originalToTransformed(it)
            assertTrue(mapped in 0..drawn.length, "originalToTransformed($it) = $mapped out of 0..${drawn.length} for \"$text\"")
        }
        (0..drawn.length).forEach {
            val mapped = out.offsetMapping.transformedToOriginal(it)
            assertTrue(mapped in 0..text.length, "transformedToOriginal($it) = $mapped out of 0..${text.length} for \"$text\"")
        }
    }

    @Test
    fun plainTextIsDrawnUnchangedAndMapsOneToOne() {
        val out = transform("bitcoin lightning")
        assertEquals("bitcoin lightning", out.text.text)
        (0..17).forEach { assertEquals(it, out.offsetMapping.originalToTransformed(it)) }
    }

    @Test
    fun sameLengthTokensDoNotMoveAnyOffset() {
        // A hashtag, a date, a group and a label all draw as themselves — only tinted.
        val text = "#bitcoin since:2026-04-01 group:dev label:en"
        val out = transform(text)
        assertEquals(text, out.text.text)
        (0..text.length).forEach { assertEquals(it, out.offsetMapping.originalToTransformed(it)) }
    }

    @Test
    fun aKnownKeyDrawsAsItsOwnersName() {
        val out = transform("from:$NPUB", name = { "Alice" })
        assertEquals("from:Alice", out.text.text)
    }

    @Test
    fun anUnknownKeyDrawsShortenedRatherThanNamed() {
        val out = transform("from:$NPUB")
        assertTrue(out.text.text.startsWith("from:npub1"))
        assertTrue(out.text.text.contains("…"))
        assertTrue(out.text.text.length < "from:$NPUB".length)
    }

    @Test
    fun theCaretAfterAShortenedKeyLandsAfterTheChip() {
        val text = "from:$NPUB rest"
        val out = transform(text, name = { "Alice" })
        assertEquals("from:Alice rest", out.text.text)
        // The space after the token, and everything past it, shift by the chip's saving.
        assertEquals("from:Alice".length, out.offsetMapping.originalToTransformed("from:$NPUB".length))
        assertEquals(out.text.text.length, out.offsetMapping.originalToTransformed(text.length))
    }

    @Test
    fun aCaretInsideAChipGoesToItsFarEndRatherThanIntoANameWithNoMiddle() {
        val out = transform("from:$NPUB", name = { "Alice" })
        // Offset 20 is inside the npub, which the drawn name has no character for.
        assertEquals("from:Alice".length, out.offsetMapping.originalToTransformed(20))
        // And back: an offset inside the drawn name lands at the end of the token it stands for.
        assertEquals("from:$NPUB".length, out.offsetMapping.transformedToOriginal(7))
    }

    @Test
    fun twoChipsBothShiftWhatFollowsThem() {
        val text = "from:$NPUB to:$NPUB tail"
        val out = transform(text, name = { "Al" })
        assertEquals("from:Al to:Al tail", out.text.text)
        assertEquals(out.text.text.length, out.offsetMapping.originalToTransformed(text.length))
        assertEquals(text.length, out.offsetMapping.transformedToOriginal(out.text.text.length))
    }

    @Test
    fun everyOffsetOfEveryFixtureStaysInBounds() {
        listOf(
            "",
            "bitcoin",
            "from:$NPUB",
            "from:$NPUB #bitcoin since:2026-04-01",
            "to:$NPUB group:dev label:review/app site:example.com tail",
        ).forEach { text ->
            assertMapsSafely(text)
            assertMapsSafely(text, name = { "A very long display name indeed" })
            (0..text.length).forEach { caret -> assertMapsSafely(text, caret) }
        }
    }

    @Test
    fun aKnownGroupDrawsAsItsNameAndAnUnknownOneKeepsItsId() {
        // An id typed from memory is unverifiable; the name is the only part a reader can check
        // against the room they meant.
        assertEquals("group:Dev Chat", transform("group:abc123", groupName = { "Dev Chat" }).text.text)
        assertEquals("group:abc123", transform("group:abc123").text.text)
    }

    @Test
    fun aGeohashDrawsAsItsCityOnceTheCacheHasOne() {
        assertEquals("geo:San Francisco", transform("geo:9q8yy", scopeName = { f, _ -> if (f == "geo") "San Francisco" else null }).text.text)
        // Null means not resolved yet, which must leave the geohash showing rather than a guess.
        assertEquals("geo:9q8yy", transform("geo:9q8yy").text.text)
    }

    @Test
    fun namedTokensStillMapEveryOffsetSafely() {
        listOf("group:abc123 tail", "geo:9q8yy tail", "group:abc123 geo:9q8yy #tag").forEach { text ->
            assertMapsSafely(text, groupName = { "A Much Longer Group Name Than The Id" }, scopeName = { _, _ -> "Reykjavik" })
            assertMapsSafely(text, groupName = { "x" }, scopeName = { _, _ -> "y" })
        }
    }

    @Test
    fun aSettlingTokenUnderTheCaretIsDrawnAsPlainText() {
        val text = "#bitcoin"
        assertEquals(text, transform(text, caret = text.length).text.text)
    }
}
