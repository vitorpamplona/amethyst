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
package com.vitorpamplona.quartz.nip25Reactions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalReactionEventTest {
    private fun reaction(
        content: String,
        vararg tags: Array<String>,
    ) = ExternalReactionEvent("id", "pk", 0L, arrayOf(*tags), content, "sig")

    @Test
    fun kindIsRegisteredWithTheEventFactory() {
        assertTrue(EventFactory.isKnownKind(ExternalReactionEvent.KIND))
    }

    @Test
    fun parsesTheSpecWebExample() {
        val json =
            """
            {"id":"${"a".repeat(64)}","pubkey":"${"b".repeat(64)}","created_at":1788807940,"kind":17,
             "tags":[["k","web"],["i","https://example.com"]],
             "content":"⭐","sig":"${"c".repeat(128)}"}
            """.trimIndent()

        val event = Event.fromJson(json)
        assertTrue(event is ExternalReactionEvent, "EventFactory produced ${event::class.simpleName}")
        assertEquals(listOf("https://example.com"), event.externalIds())
        assertEquals(listOf("web"), event.externalKinds())
        assertEquals("https://example.com", event.openableUrl())
        assertTrue(event.hasTarget())
    }

    @Test
    fun keepsEveryTargetOfAMultiPartReaction() {
        // The spec's podcast example names both the show and the episode.
        val event =
            reaction(
                "+",
                arrayOf("k", "podcast:guid"),
                arrayOf("i", "podcast:guid:917393e3", "https://fountain.fm/show/QRT0l2Ef"),
                arrayOf("k", "podcast:item:guid"),
                arrayOf("i", "podcast:item:guid:PC20-229", "https://fountain.fm/episode/DQqBg5sD"),
            )

        assertEquals(2, event.externalIds().size)
        assertEquals(listOf("podcast:guid", "podcast:item:guid"), event.externalKinds())
    }

    @Test
    fun prefersAnExplicitHintOverGuessingFromTheId() {
        // A podcast GUID is not openable; the hint beside it is.
        val event =
            reaction(
                "+",
                arrayOf("k", "podcast:guid"),
                arrayOf("i", "podcast:guid:917393e3", "https://fountain.fm/show/QRT0l2Ef"),
            )

        assertEquals("https://fountain.fm/show/QRT0l2Ef", event.openableUrl())
    }

    @Test
    fun anIdentifierWithNoLinkIsNotOpenable() {
        val event = reaction("+", arrayOf("k", "isbn"), arrayOf("i", "isbn:9780765382030"))

        assertNull(event.openableUrl())
        assertTrue(event.hasTarget())
    }

    @Test
    fun readsLikeAndDislike() {
        assertTrue(reaction("+", arrayOf("i", "https://a.example")).isLike())
        assertTrue(reaction("", arrayOf("i", "https://a.example")).isLike(), "empty content is a like per NIP-25")
        assertTrue(reaction("-", arrayOf("i", "https://a.example")).isDislike())
        assertTrue(!reaction("⭐", arrayOf("i", "https://a.example")).isLike())
    }

    @Test
    fun aReactionWithNoTargetIsNotAttributable() {
        assertTrue(!reaction("+").hasTarget())
    }
}
