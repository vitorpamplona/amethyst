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
package com.vitorpamplona.quartz.experimental.nip85TrustedAssertions

import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssertionEventTest {
    val servicePubKey = "4fd5e210530e4f6b2cb083795834bfe5108324f1ed9f00ab73b9e8fcfe5f12fe"
    val dummySig = "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun parseUserAssertionWithAllTags() {
        val targetUser = "e88a691e98d9987c964521dff60025f60700378a4879180dcbbb4a5027850411"
        val event =
            ContactCardEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", targetUser),
                        arrayOf("rank", "89"),
                        arrayOf("followers", "15000"),
                        arrayOf("first_created_at", "1609459200"),
                        arrayOf("post_cnt", "5000"),
                        arrayOf("reply_cnt", "3200"),
                        arrayOf("reactions_cnt", "12000"),
                        arrayOf("zap_amt_recd", "5000000"),
                        arrayOf("zap_amt_sent", "2000000"),
                        arrayOf("zap_cnt_recd", "500"),
                        arrayOf("zap_cnt_sent", "200"),
                        arrayOf("zap_avg_amt_day_recd", "10000"),
                        arrayOf("zap_avg_amt_day_sent", "5000"),
                        arrayOf("reports_cnt_recd", "3"),
                        arrayOf("reports_cnt_sent", "1"),
                        arrayOf("t", "bitcoin"),
                        arrayOf("t", "nostr"),
                        arrayOf("active_hours_start", "8"),
                        arrayOf("active_hours_end", "22"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(targetUser, event.aboutUser())
        assertEquals(89, event.rank())
        assertEquals(15000, event.followerCount())
        assertEquals(1609459200L, event.firstCreatedAt())
        assertEquals(5000, event.postCount())
        assertEquals(3200, event.replyCount())
        assertEquals(12000, event.reactionsCount())
        assertEquals(5000000L, event.zapAmountReceived())
        assertEquals(2000000L, event.zapAmountSent())
        assertEquals(500, event.zapCountReceived())
        assertEquals(200, event.zapCountSent())
        assertEquals(10000L, event.zapAvgAmountDayReceived())
        assertEquals(5000L, event.zapAvgAmountDaySent())
        assertEquals(3, event.reportsCountReceived())
        assertEquals(1, event.reportsCountSent())
        assertEquals(listOf("bitcoin", "nostr"), event.topics())
        assertEquals(8, event.activeHoursStart())
        assertEquals(22, event.activeHoursEnd())
    }

    @Test
    fun parseUserAssertionMinimalTags() {
        val event =
            ContactCardEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", "e88a691e98d9987c964521dff60025f60700378a4879180dcbbb4a5027850411"),
                        arrayOf("rank", "42"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(42, event.rank())
        assertNull(event.followerCount())
        assertNull(event.firstCreatedAt())
        assertNull(event.postCount())
        assertNull(event.zapAmountReceived())
        assertEquals(emptyList(), event.topics())
    }

    @Test
    fun parseEventAssertion() {
        val targetEventId = "f00dcafe00000000000000000000000000000000000000000000000000000000"
        val event =
            EventAssertionEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", targetEventId),
                        arrayOf("rank", "75"),
                        arrayOf("comment_cnt", "42"),
                        arrayOf("quote_cnt", "10"),
                        arrayOf("repost_cnt", "25"),
                        arrayOf("reaction_cnt", "300"),
                        arrayOf("zap_cnt", "15"),
                        arrayOf("zap_amount", "150000"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(targetEventId, event.aboutEvent())
        assertEquals(75, event.rank())
        assertEquals(42, event.commentCount())
        assertEquals(10, event.quoteCount())
        assertEquals(25, event.repostCount())
        assertEquals(300, event.reactionCount())
        assertEquals(15, event.zapCount())
        assertEquals(150000L, event.zapAmount())
    }

    @Test
    fun parseAddressableAssertion() {
        val targetAddress = "30023:e88a691e98d9987c964521dff60025f60700378a4879180dcbbb4a5027850411:my-article"
        val event =
            AddressableAssertionEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", targetAddress),
                        arrayOf("rank", "92"),
                        arrayOf("comment_cnt", "100"),
                        arrayOf("quote_cnt", "20"),
                        arrayOf("repost_cnt", "50"),
                        arrayOf("reaction_cnt", "500"),
                        arrayOf("zap_cnt", "30"),
                        arrayOf("zap_amount", "300000"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(targetAddress, event.aboutAddress())
        assertEquals(92, event.rank())
        assertEquals(100, event.commentCount())
        assertEquals(20, event.quoteCount())
        assertEquals(50, event.repostCount())
        assertEquals(500, event.reactionCount())
        assertEquals(30, event.zapCount())
        assertEquals(300000L, event.zapAmount())
    }

    @Test
    fun parseExternalIdAssertion() {
        val targetId = "isbn:978-0-13-468599-1"
        val event =
            ExternalIdAssertionEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", targetId),
                        arrayOf("rank", "85"),
                        arrayOf("comment_cnt", "50"),
                        arrayOf("reaction_cnt", "200"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(targetId, event.aboutExternalId())
        assertEquals(85, event.rank())
        assertEquals(50, event.commentCount())
        assertEquals(200, event.reactionCount())
    }

    @Test
    fun parseEventAssertionWithMissingTags() {
        val event =
            EventAssertionEvent(
                id = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
                pubKey = servicePubKey,
                createdAt = 1700000000,
                tags =
                    arrayOf(
                        arrayOf("d", "f00dcafe00000000000000000000000000000000000000000000000000000000"),
                        arrayOf("rank", "50"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertEquals(50, event.rank())
        assertNull(event.commentCount())
        assertNull(event.quoteCount())
        assertNull(event.repostCount())
        assertNull(event.reactionCount())
        assertNull(event.zapCount())
        assertNull(event.zapAmount())
    }

    @Test
    fun eventKindsAreCorrect() {
        assertEquals(30382, ContactCardEvent.KIND)
        assertEquals(30383, EventAssertionEvent.KIND)
        assertEquals(30384, AddressableAssertionEvent.KIND)
        assertEquals(30385, ExternalIdAssertionEvent.KIND)
    }
}
