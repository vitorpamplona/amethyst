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
package com.vitorpamplona.quartz.experimental.trustedLists

import com.vitorpamplona.quartz.experimental.trustedLists.tags.ListStatus
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.tags.PubKeyMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrustedListBuilderTest {
    private val observer = "2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331"
    private val tagEventId = "2f6a8652bde6fb5a974d6e06e4eae3b4f130140fd170b2686a291463f47a7451"
    private val tagAuthor = "e5272de914bd301755c439b88e6959a43c9d2664831f093c51e9c799a16a102f"
    private val member = "b83a28b7e4e5d20bd960c5faeb6625f95529166b8bdb045d42634a2f35919450"

    private fun sign(template: EventTemplate<UserTrustedListEvent>): Event =
        EventFactory.create<Event>(
            id = "00".repeat(32),
            pubKey = "a68dbf561cfe3da1b76f1e65c7d4d9cc116f79921b38a815fd75cb5460b4b599",
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
            sig = "00".repeat(64),
        )

    @Test
    fun buildsAPinnedTagPubKeyList() {
        val content =
            TrustedListContent(
                members = listOf(TrustedListContentMember(pubkey = member, endorsements = 4, disputes = 0, score = 99)),
            )

        val template =
            UserTrustedListEvent.build(
                listId = "tl-pin-2efaa715-e5272de9-podcaster",
                members = listOf(PubKeyMemberTag(member, score = 99)),
                content = content.toContent(),
                createdAt = 1_787_253_028L,
            ) {
                title("Podcaster")
                metric("pinned-tag-membership")
                observer(observer)
                sourceTag(tagEventId, tagAuthor, "podcaster")
                cutoff(1)
                minRank(2)
            }

        val event = sign(template)
        assertIs<UserTrustedListEvent>(event)

        assertEquals(UserTrustedListEvent.KIND, event.kind)
        assertEquals("tl-pin-2efaa715-e5272de9-podcaster", event.listId())
        assertEquals("Podcaster", event.title())
        assertEquals("pinned-tag-membership", event.metric())
        assertEquals(observer, event.observer())
        assertEquals(1, event.cutoff())
        assertEquals(2, event.minRank())
        assertEquals("podcaster", event.sourceTag()?.slug)
        assertEquals(listOf(member), event.memberValues())
        assertEquals(listOf(99), event.members().map { it.score })
        assertFalse(event.isTruncated(), "a list with no truncated tag is complete")
        assertEquals(content, event.contentEcho())
    }

    @Test
    fun buildsATruncatedList() {
        val template =
            UserTrustedListEvent.build(
                listId = "tl-pin-2efaa715-e5272de9-podcaster",
                members = listOf(PubKeyMemberTag(member, score = 99)),
                content = TrustedListContent(partial = true, total = 4211).toContent(),
                createdAt = 1_787_253_028L,
            ) {
                truncated(4211)
            }

        val event = sign(template)
        assertIs<UserTrustedListEvent>(event)

        assertTrue(event.isTruncated())
        assertEquals(4211, event.truncatedTotal())
        assertEquals(true, event.contentEcho()?.partial)
        assertEquals(4211, event.contentEcho()?.total)
    }

    @Test
    fun buildsAnEmptyRetraction() {
        val template =
            UserTrustedListEvent.build(
                listId = "tl-pin-2efaa715-e5272de9-podcaster",
                createdAt = 1_787_253_028L,
            ) {
                status(ListStatus.RETRACTED)
            }

        val event = sign(template)
        assertIs<UserTrustedListEvent>(event)

        assertTrue(event.isRetracted())
        assertEquals(emptyList<String>(), event.memberValues())
    }

    @Test
    fun aCompleteListSerializesNoPartialMarker() {
        val content = TrustedListContent(members = listOf(TrustedListContentMember(pubkey = member, score = 99)))

        val json = content.toContent()

        assertFalse(json.contains("partial"), "encoded content was: $json")
        assertFalse(json.contains("total"), "encoded content was: $json")
        assertFalse(json.contains("\"id\""), "unused member keys must not be encoded: $json")
        assertEquals(content, TrustedListContent.parse(json))
    }
}
