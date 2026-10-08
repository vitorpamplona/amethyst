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
package com.vitorpamplona.quartz.experimental.postingStreak

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostingStreakEventTest {
    private val author = "d70d50091504b992d1838822af245d5f6b3a16b82d917acb7924cef61ed4acee"

    /** A live Ditto 13473 (2026-10-08 census). */
    private fun sample(): Event =
        EventFactory.create(
            id = "72c667987dd92d628def1f70d316f2917e33d0da814926e38ab86391641c0966",
            pubKey = author,
            createdAt = 1791428971L,
            kind = 13473,
            tags =
                arrayOf(
                    arrayOf("start", "1791298795"),
                    arrayOf("end", "1791428754"),
                    arrayOf("alt", "Posting streak"),
                    arrayOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"),
                    arrayOf("published_at", "1790552379"),
                ),
            content = "",
            sig = "eb7b2a7638786a87d923c0757989fd0a8a17bb71875796c8f521fa60c1fed6492f9434acec0b820cd67070d4775c7beabebe87e1fc40caf8eb19ea1c8eee81e4",
        )

    private fun streak(vararg tags: Array<String>) = PostingStreakEvent("00".repeat(32), author, 1L, arrayOf(*tags), "", "00".repeat(64))

    @Test
    fun factoryDispatch() {
        val event = sample()
        assertIs<PostingStreakEvent>(event)
        assertTrue(EventFactory.isKnownKind(PostingStreakEvent.KIND))
        assertFalse(event is SearchableEvent)
        assertFalse(event is PubKeyHintProvider || event is EventHintProvider || event is AddressHintProvider, "a streak references nothing")
        assertEquals("", (event as? PostingStreakEvent)?.dTag())
    }

    @Test
    fun readsTheLiveSample() {
        val event = assertIs<PostingStreakEvent>(sample())
        assertEquals(1791298795L, event.start())
        assertEquals(1791428754L, event.end())

        val now = 1791428971L
        assertEquals(PostingStreak(1791298795L, 1791428754L), event.streak(now))
        // 129959 seconds is 1.5 days: rounds up to 2.
        assertEquals(2L, event.daysAt(now))
        // Broken once 36 hours have passed since `end`.
        assertEquals(0L, event.daysAt(1791428754L + PostingStreak.WINDOW_SECONDS + 1))
        assertEquals(2L, event.daysAt(1791428754L + PostingStreak.WINDOW_SECONDS))
    }

    @Test
    fun readingRules() {
        val single = PostingStreak(100L, 100L)
        assertEquals(1L, single.days(), "max(1, ...) - a single post is a one-day streak")
        assertEquals(1L, PostingStreak(0L, 86_400L).days())
        assertEquals(2L, PostingStreak(0L, 86_401L).days())
        assertEquals(100L + PostingStreak.WINDOW_SECONDS, single.expiresAt())
        assertTrue(single.isLive(100L + PostingStreak.WINDOW_SECONDS))
        assertFalse(single.isLive(101L + PostingStreak.WINDOW_SECONDS))
    }

    @Test
    fun rejectsWhatTheSpecSaysToReject() {
        val now = 1_000_000L
        // start > end
        assertNull(streak(arrayOf("start", "500"), arrayOf("end", "400")).streak(now))
        // end too far in the future, but a little clock skew is fine
        assertNull(streak(arrayOf("start", "500"), arrayOf("end", "${now + 301}")).streak(now))
        assertEquals(PostingStreak(500L, now + 300), streak(arrayOf("start", "500"), arrayOf("end", "${now + 300}")).streak(now))
        // not non-negative integers
        listOf("-1", "1.5", "", "abc", "+5", "1e3").forEach {
            assertNull(streak(arrayOf("start", it), arrayOf("end", "900")).streak(now), "start '$it'")
            assertNull(streak(arrayOf("start", it)).start(), "start '$it'")
        }
        // missing tags
        assertNull(streak(arrayOf("end", "900")).streak(now))
        assertNull(streak(arrayOf("start")).start())
        assertEquals(0L, streak().daysAt(now))
    }

    @Test
    fun updateRule() {
        val w = PostingStreak.WINDOW_SECONDS
        val s = PostingStreak.startingAt(1_000L)
        assertEquals(PostingStreak(1_000L, 1_000L), s)
        assertEquals(s, s.extendedWith(999L), "streaks never move backwards")
        assertEquals(PostingStreak(1_000L, 1_000L + w), s.extendedWith(1_000L + w))
        assertEquals(PostingStreak(1_001L + w, 1_001L + w), s.extendedWith(1_001L + w), "a gap over W breaks it")
    }

    @Test
    fun buildRoundTrip() {
        val template = PostingStreakEvent.build(PostingStreak(1791298795L, 1791428754L), createdAt = 1L)
        assertEquals(PostingStreakEvent.KIND, template.kind)
        assertEquals("", template.content)
        val event = streak(*template.tags)
        assertEquals(1791298795L, event.start())
        assertEquals(1791428754L, event.end())
    }
}
