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
package com.vitorpamplona.quartz.nip10Notes.content

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the ICU-free content scanners to the regexes they replaced.
 *
 * [findHashtags] and the `#[n]` walker used to anchor a fresh `Regex.matchAt` at every candidate.
 * On Android that goes through ICU, where `Matcher.region()` copies the whole input into native
 * memory per call — over 2588 real notes that was 43,626 Matchers copying 9.6GB, worst single note
 * 279MB. The scanners now match the grammars directly, so [hashtagSearch] and [tagSearch] survive
 * only as the specification, and this asserts the two agree.
 *
 * The corpus targets where a hand-rolled matcher is most likely to drift: the exact punctuation
 * set that ends a tag, ASCII-vs-Unicode whitespace before the `#`, non-ASCII inside the tag, and
 * the `+`/`[0-9]+` minimum-one-character rules.
 */
class ContentScanRegexEquivalenceTest {
    private fun referenceHashtags(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val out = mutableSetOf<String>()
        hashtagSearch.findAll(content).forEach { m ->
            val tag = m.groups[1]?.value
            if (tag != null && tag.isNotBlank()) out.add(tag)
        }
        return out.toList()
    }

    private fun referenceIndexTags(
        content: String,
        tags: Array<Array<String>>,
        wanted: String,
    ): Set<String> {
        val out = mutableSetOf<String>()
        tagSearch.findAll(content).forEach { m ->
            try {
                val tag = m.groups[1]?.value?.let { tags[it.toInt()] }
                if (tag != null && tag.size > 1 && tag[0] == wanted) out.add(tag[1])
            } catch (e: Exception) {
            }
        }
        return out
    }

    private val tagArray =
        arrayOf(
            arrayOf("p", "pubkey0"),
            arrayOf("e", "event1"),
            arrayOf("a", "addr2"),
            arrayOf("p", "pubkey3"),
            arrayOf("t", "topic4"),
        )

    private fun corpus(): List<String> =
        buildList {
            add("")
            add("   ")
            add("#")
            add("#tag")
            add("hello #tag world")
            add("a#tag")
            add("#tag#other")
            add("#tag #other")
            add("##tag")
            add("#tag.")
            add("#tag, and #more!")
            add("#tag's")
            add("#tag\"quoted\"")
            add("#a")
            add("#1")
            add("#tag-with-dash")
            add("#tag_with_underscore")
            add("#tag~tilde|pipe\\back`tick")
            // non-ASCII is valid tag content: the regex class and its \s are ASCII-only
            add("#café")
            add("#日本語")
            add("#tagéè")
            // ASCII vs Unicode whitespace BEFORE the # decides whether it matches at all
            add("x\u00A0#tag")
            add("x\u2003#tag")
            add("x\t#tag")
            add("x\n#tag")
            add("x\r#tag")
            // Unicode whitespace INSIDE the tag is valid to the regex but blank to Kotlin
            add("#\u00A0")
            add("#\u00A0x")
            // every excluded char must terminate the tag
            for (c in "!@#$%^&*()=+./,[{]};:'\"?><") add("#tag${c}more")
            for (c in "!@#$%^&*()=+./,[{]};:'\"?><") add("#$c")
            // index tags
            add("#[0]")
            add("#[1] and #[2]")
            add("look #[3] here")
            add("#[]")
            add("#[abc]")
            add("#[99]")
            add("#[0")
            add("#[0]]")
            add("x#[0]")
            add("x\u00A0#[0]")
            add("#[0]#[1]")
            add("#[00]")
            // mixed
            add("#tag #[0] #other #[1]")
            add("lorem ipsum ".repeat(500) + "#tail")
            add("#head" + " dolor sit ".repeat(500))
            add("no hashes at all here ".repeat(200))
        }

    @Test
    fun hashtagsMatchRegex() {
        corpus().forEach { c ->
            assertEquals(
                referenceHashtags(c).sorted(),
                findHashtags(c).sorted(),
                "findHashtags diverged on: ${c.take(80)}",
            )
        }
    }

    @Test
    fun indexTagsMatchRegex() {
        corpus().forEach { c ->
            assertEquals(
                referenceIndexTags(c, tagArray, "p").sorted(),
                findIndexTagsWithPeople(c, tagArray).sorted(),
                "findIndexTagsWithPeople diverged on: ${c.take(80)}",
            )
            val refEv = referenceIndexTags(c, tagArray, "e") + referenceIndexTags(c, tagArray, "a")
            assertEquals(
                refEv.sorted(),
                findIndexTagsWithEventsOrAddresses(c, tagArray).sorted(),
                "findIndexTagsWithEventsOrAddresses diverged on: ${c.take(80)}",
            )
        }
    }

    @Test
    fun hashtagsMatchRegexAtEveryOffset() {
        val filler = "a b\tc\nd  "
        for (i in 0..filler.length) {
            val c = filler.substring(0, i) + "#tag" + filler.substring(i)
            assertEquals(referenceHashtags(c).sorted(), findHashtags(c).sorted(), "offset $i")
        }
    }
}
