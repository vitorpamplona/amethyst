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
package com.vitorpamplona.amethyst.ui.feeds

import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media hidden behind a secret emoji is invisible to the parser's media list —
 * it only exists inside the emoji's variation selectors — so the prefetcher has to
 * decode it to find anything to warm. These lock in that it does.
 */
class SecretEmojiWarmTargetsTest {
    private val emoji = "\uD83D\uDE00"

    private fun WarmTargets.imageUrls(): List<String> =
        buildList {
            forEachImage { url, _ -> add(url) }
        }

    private fun warmOf(
        content: String,
        tags: ImmutableListOfLists<String> = EmptyTagList,
    ) = WarmTargets().apply { harvest(CachedRichTextParser.parseText(content, tags)) }

    @Test
    fun harvestsImageHiddenBehindASecretEmoji() {
        val secret = EmojiCoder.encode(emoji, "look at this https://example.com/photo.jpg")

        assertEquals(
            listOf("https://example.com/photo.jpg"),
            warmOf("A hidden message $secret").imageUrls(),
        )
    }

    @Test
    fun harvestsLinkPreviewHiddenBehindASecretEmoji() {
        val secret = EmojiCoder.encode(emoji, "read https://example.com/article")

        assertEquals(setOf("https://example.com/article"), warmOf("Psst $secret").links)
    }

    @Test
    fun harvestsVideoPosterHiddenBehindASecretEmoji() {
        // The hidden body is parsed with the *host note's* tags, so the imeta that
        // declares the video's poster still applies inside the secret.
        val tags =
            ImmutableListOfLists(
                arrayOf(
                    arrayOf(
                        "imeta",
                        "url https://example.com/clip.mp4",
                        "m video/mp4",
                        "image https://example.com/poster.jpg",
                    ),
                ),
            )
        val secret = EmojiCoder.encode(emoji, "watch https://example.com/clip.mp4")

        assertEquals(
            listOf("https://example.com/poster.jpg"),
            warmOf("Shhh $secret", tags).imageUrls(),
        )
    }

    @Test
    fun harvestsMediaOfASecretNestedInsideASecret() {
        val inner = EmojiCoder.encode(emoji, "https://example.com/inner.jpg")
        val outer = EmojiCoder.encode(emoji, "one more layer $inner")

        assertEquals(listOf("https://example.com/inner.jpg"), warmOf("Layers $outer").imageUrls())
    }

    @Test
    fun stopsUnwrappingAtTheDepthLimit() {
        val level3 = EmojiCoder.encode(emoji, "https://example.com/level3.jpg")
        val level2 = EmojiCoder.encode(emoji, "deeper $level3")
        val level1 = EmojiCoder.encode(emoji, "deep $level2")

        assertTrue(warmOf("Too deep $level1").imageUrls().isEmpty())
    }

    @Test
    fun harvestsNothingFromAPlainBody() {
        val targets = warmOf("just text with an emoji $emoji")

        assertTrue(targets.imageUrls().isEmpty())
        assertTrue(targets.links.isEmpty())
    }

    /** The kinds we can't render-warm are scanned as raw text; secrets must still be unwrapped. */
    @Test
    fun harvestsContentUrlsHiddenBehindASecretEmoji() {
        val secret = EmojiCoder.encode(emoji, "https://example.com/raw.jpg and https://example.com/page")
        val targets = WarmTargets().apply { harvestContentUrls("Unparsed body $secret") }

        assertEquals(listOf("https://example.com/raw.jpg"), targets.imageUrls())
        assertEquals(setOf("https://example.com/page"), targets.links)
    }
}
