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
package com.vitorpamplona.quartz.nip72ModCommunities

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.RoleProps
import com.vitorpamplona.quartz.nip01Core.links.props.WotProps
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip72ModCommunitiesLinksTest {
    private val id = "0".repeat(64)
    private val me = "f".repeat(64)
    private val sig = "0".repeat(128)

    private val owner = "b1".repeat(32)
    private val moderator = "b2".repeat(32)
    private val member = "b3".repeat(32)
    private val community = "34550:$owner:nostr"
    private val post = "e1".repeat(32)

    @Test
    fun anApprovalSplitsTheCommunityFromTheApprovedPost() {
        val event =
            CommunityPostApprovalEvent(
                id,
                moderator,
                1L,
                arrayOf(
                    arrayOf("a", community, "wss://relay.example/"),
                    arrayOf("e", post, "wss://relay.example/"),
                    arrayOf("a", "30023:$member:post"),
                    arrayOf("p", member),
                    arrayOf("k", "1"),
                ),
                "{}",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.COMMUNITY, LinkTarget.Address(community), "a"),
                Link(Relation.APPROVED, LinkTarget.Event(post), "e"),
                Link(Relation.APPROVED, LinkTarget.Address("30023:$member:post"), "a"),
                Link(Relation.APPROVED_AUTHOR, LinkTarget.User(member), "p"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
            ),
            event.links(),
        )
    }

    @Test
    fun aDefinitionNamesItsModerators() {
        val event =
            CommunityDefinitionEvent(
                id,
                owner,
                1L,
                arrayOf(
                    arrayOf("d", "nostr"),
                    arrayOf("p", moderator, "", "moderator"),
                    arrayOf("p", owner),
                    arrayOf("p", member, "", "member"),
                    arrayOf("relay", "wss://relay.example/"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.MODERATOR, LinkTarget.User(moderator), "p"),
                Link(Relation.MODERATOR, LinkTarget.User(owner), "p"),
                Link(Relation.MENTION, LinkTarget.User(member), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun theCommunitiesListIsSubscribedCommunitiesOnly() {
        val event =
            CommunityListEvent(
                id,
                me,
                1L,
                arrayOf(
                    arrayOf("a", community),
                    arrayOf("a", "30023:$member:post"),
                ),
                "",
                sig,
            )

        assertEquals(listOf(Link(Relation.SUBSCRIBED, LinkTarget.Address(community), "a")), event.links())
    }

    @Test
    fun rulesSplitAllowFromDenyAndCarryTheirValues() {
        val event =
            CommunityRulesEvent(
                id,
                owner,
                1L,
                arrayOf(
                    arrayOf("d", "nostr"),
                    arrayOf("a", community),
                    arrayOf("k", "1", "2000", "10"),
                    arrayOf("p", member, "allow", "contributor"),
                    arrayOf("p", moderator, "deny"),
                    arrayOf("wot", owner, "2"),
                ),
                "",
                sig,
            )

        assertEquals(
            listOf(
                Link(Relation.COMMUNITY, LinkTarget.Address(community), "a"),
                Link(Relation.TAG, LinkTarget.Tag("k", "1"), "k"),
                Link(Relation.ALLOWED, LinkTarget.User(member), "p", RoleProps(listOf("contributor"))),
                Link(Relation.DENIED, LinkTarget.User(moderator), "p"),
                Link(Relation.WOT_ROOT, LinkTarget.User(owner), "wot", WotProps(2)),
            ),
            event.links(),
        )
    }
}
