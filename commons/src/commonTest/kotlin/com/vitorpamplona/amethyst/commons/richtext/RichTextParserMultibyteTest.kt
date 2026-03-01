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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextParserMultibyteTest {
    @Test
    fun testFullTextWithMultibyte() {
        // Multibyte characters around an email address should not produce URL/Link segments
        val text =
            "マルチバイト文字テストuser@example.com ほげほげ"

        val state =
            RichTextParser()
                .parseText(text, EmptyTagList, null)

        val allSegments =
            state.paragraphs
                .flatMap { it.words }

        // user@example.com should be EmailSegment
        assertTrue(
            "user@example.com should be EmailSegment",
            allSegments.any { it is EmailSegment && it.segmentText == "user@example.com" },
        )

        // user@example.com should NOT be a LinkSegment
        assertTrue(
            "user@example.com should not be a LinkSegment",
            allSegments.none { it is LinkSegment && it.segmentText == "user@example.com" },
        )

        // user@example.com should not be in urlSet
        assertTrue(
            "user@example.com should not be in urlSet",
            !state.urlSet.contains("user@example.com"),
        )
    }

    @Test
    fun testEmailSegmentStandalone() {
        val text = "user@example.com"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }
        assertTrue(
            "user@example.com should be EmailSegment",
            allSegments.any { it is EmailSegment && it.segmentText == "user@example.com" },
        )
    }

    @Test
    fun testMultibytePrefix_SchemelessUrl() {
        // ああexample.com → RegularText(ああ) + SchemelessUrl(example.com)
        val text = "ああexample.com"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val urlSegments = allSegments.filterIsInstance<SchemelessUrlSegment>()
        assertTrue("Should have SchemelessUrlSegment", urlSegments.isNotEmpty())
        assertTrue("URL should be example.com", urlSegments.any { it.url == "example.com" })

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue("Should have prefix ああ", textSegments.any { it.segmentText == "ああ" })
    }

    @Test
    fun testMultibyteSuffix_SchemelessUrl() {
        // example.comああ → SchemelessUrl(example.com) + RegularText(ああ)
        val text = "example.comああ"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val urlSegments = allSegments.filterIsInstance<SchemelessUrlSegment>()
        assertTrue("Should have SchemelessUrlSegment", urlSegments.isNotEmpty())
        assertTrue("URL should be example.com", urlSegments.any { it.url == "example.com" })

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue("Should have suffix ああ", textSegments.any { it.segmentText == "ああ" })
    }

    @Test
    fun testMultibytePrefix_Email() {
        // ほむほむuser@example.comほげほげ → RegularText(ほむほむ) + Email(user@example.com) + RegularText(ほげほげ)
        val text = "ほむほむuser@example.comほげほげ"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val emailSegment = allSegments.filterIsInstance<EmailSegment>()
        assertTrue("Should have EmailSegment", emailSegment.isNotEmpty())
        assertTrue("Email should be user@example.com", emailSegment.any { it.segmentText == "user@example.com" })

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue("Should have prefix ほむほむ", textSegments.any { it.segmentText == "ほむほむ" })
        assertTrue("Should have suffix ほげほげ", textSegments.any { it.segmentText == "ほげほげ" })
    }

    @Test
    fun testEmailWithSpaceAndMultibyteText() {
        // user@example.com ふがふが → Email(user@example.com) + RegularText(ふがふが)
        val text = "user@example.com ふがふが"
        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val allSegments = state.paragraphs.flatMap { it.words }

        val emailSegment = allSegments.filterIsInstance<EmailSegment>()
        assertTrue("Should have EmailSegment", emailSegment.isNotEmpty())
        assertTrue("Email should be user@example.com", emailSegment.any { it.segmentText == "user@example.com" })

        val textSegments = allSegments.filterIsInstance<RegularTextSegment>()
        assertTrue("Should have suffix ふがふが", textSegments.any { it.segmentText == "ふがふが" })
    }
}
