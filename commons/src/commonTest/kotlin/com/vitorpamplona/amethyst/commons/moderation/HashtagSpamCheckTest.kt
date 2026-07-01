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
package com.vitorpamplona.amethyst.commons.moderation

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashtagSpamCheckTest {
    private val author = "a".repeat(64)
    private val other = "b".repeat(64)

    private fun event(
        kind: Int = 1,
        hashtags: List<String> = emptyList(),
        pubkey: String = author,
    ): Event =
        Event(
            id = "id".padEnd(64, '0'),
            pubKey = pubkey,
            createdAt = 0L,
            kind = kind,
            tags = hashtags.map { arrayOf("t", it) }.toTypedArray(),
            content = "",
            sig = "sig".padEnd(128, '0'),
        )

    @Test
    fun returnsFalseWhenDisabled() {
        val e = event(hashtags = (1..20).map { "h$it" })
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = false,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsFalseForNullDisplayedEvent() {
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = null,
                authorPubkey = author,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsFalseForLongformEvenWithManyTags() {
        // kind 30023 = long-form content; exempt regardless of tag count.
        val e = event(kind = 30023, hashtags = (1..20).map { "h$it" })
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsFalseForFollowedAuthor() {
        val e = event(hashtags = (1..20).map { "h$it" })
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = setOf(author),
            ),
        )
    }

    @Test
    fun returnsFalseUnderThreshold() {
        val e = event(hashtags = listOf("nostr", "bitcoin", "amethyst"))
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsFalseAtThresholdExactly() {
        // hasMoreHashtagsThan(5) returns true only for count > 5.
        val e = event(hashtags = (1..5).map { "h$it" })
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsTrueOverThresholdWithDistinctTags() {
        val e = event(hashtags = (1..10).map { "h$it" })
        assertTrue(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun returnsFalseOverThresholdWhenAllTagsAreDuplicates() {
        // hasMoreHashtagsThan checks count AND unique count > limit.
        // 20 copies of the same hashtag → unique = 1 → not spam.
        val e = event(hashtags = List(20) { "bitcoin" })
        assertFalse(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = emptySet(),
            ),
        )
    }

    @Test
    fun authorNotInExemptKeysIsNotExempt() {
        val e = event(hashtags = (1..10).map { "h$it" })
        assertTrue(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = e.pubKey,
                enabled = true,
                threshold = 5,
                exemptKeys = setOf(other),
            ),
        )
    }

    @Test
    fun nullAuthorPubkeyDoesNotShortCircuit() {
        val e = event(hashtags = (1..10).map { "h$it" })
        assertTrue(
            HashtagSpamCheck.isHashtagSpam(
                displayedEvent = e,
                authorPubkey = null,
                enabled = true,
                threshold = 5,
                exemptKeys = setOf(author),
            ),
        )
    }
}
