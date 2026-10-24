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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.marmot.appComponents.MediaLocatorV2
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** NIP-30 custom emoji in a Marmot message, as MDK 0.12 sends them. */
class MarmotCustomEmojiTest {
    private fun reference(url: String) =
        EncryptedMediaReferenceV2(
            locators = listOf(MediaLocatorV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, url)),
            ciphertextSha256 = ByteArray(32) { 1 },
            plaintextSha256 = ByteArray(32) { 2 },
            nonce = ByteArray(12) { 3 },
            mediaType = "image/png",
            filename = "party.png",
        )

    private fun chat(vararg tags: Array<String>) = Event("1".repeat(64), "a".repeat(64), 100, 9, arrayOf(*tags), "let's :party: now", "")

    @Test
    fun emojiNamedByAttachmentLocatorIsArtwork() {
        val event = chat(reference("https://blob/x").toImetaTag(), arrayOf("emoji", "party", "https://blob/x"))
        assertEquals(setOf("https://blob/x"), MarmotCustomEmoji.matchedEmojiAttachments(event).keys)
        assertTrue(MarmotCustomEmoji.hasOnlyEmojiAttachments(event))
        assertEquals(2, MarmotCustomEmoji.safeRenderTags(event).size)
    }

    @Test
    fun emojiWithoutAttachmentIsDroppedSoItIsNeverFetched() {
        val event = chat(arrayOf("emoji", "party", "https://public/party.png"))
        assertTrue(MarmotCustomEmoji.matchedEmojiAttachments(event).isEmpty())
        assertEquals(0, MarmotCustomEmoji.safeRenderTags(event).size)
    }

    @Test
    fun realAttachmentBesideEmojiIsStillMedia() {
        val event =
            chat(
                reference("https://blob/photo").toImetaTag(),
                reference("https://blob/x").toImetaTag(),
                arrayOf("emoji", "party", "https://blob/x"),
            )
        assertFalse(MarmotCustomEmoji.hasOnlyEmojiAttachments(event))
    }

    @Test
    fun tagLimitsAreMdks() {
        assertTrue(MarmotCustomEmoji.withinTagLimits(List(64) { arrayOf("t", "x") }))
        assertFalse(MarmotCustomEmoji.withinTagLimits(List(65) { arrayOf("t", "x") }))
        assertFalse(MarmotCustomEmoji.withinTagLimits(listOf(arrayOf("t", "x".repeat(16 * 1024)))))
    }

    /** Each reaction carries its own encrypted copy under its own URL, so the shortcode decides "the same". */
    @Test
    fun reactionShortcodeIgnoresTheUrl() {
        assertEquals(":party:", MarmotCustomEmoji.reactionShortcode(":party:https://blossom.example/aaa"))
        assertEquals(":party:", MarmotCustomEmoji.reactionShortcode(":party:https://blossom.example/bbb"))
        assertEquals(":party:", MarmotCustomEmoji.reactionShortcode(":party:"))
        assertEquals(null, MarmotCustomEmoji.reactionShortcode("+"))
        assertEquals(null, MarmotCustomEmoji.reactionShortcode("\uD83D\uDE00"))
        assertEquals(null, MarmotCustomEmoji.reactionShortcode("::"))
        assertEquals(null, MarmotCustomEmoji.reactionShortcode(":"))
    }
}
