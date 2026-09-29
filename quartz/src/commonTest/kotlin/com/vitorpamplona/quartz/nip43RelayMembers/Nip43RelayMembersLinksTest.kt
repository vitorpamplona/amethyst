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
package com.vitorpamplona.quartz.nip43RelayMembers

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.MemberProps
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip43RelayMembersLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val relay = "f".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)

    @Test
    fun addAndRemoveLinkTheMember() {
        val tags = arrayOf(arrayOf("-"), arrayOf("p", alice), arrayOf("p", "short"))
        assertEquals(listOf(Link(Relation.ADDED_USER, LinkTarget.User(alice), "p")), RelayAddMemberEvent(id, relay, 1, tags, "", sig).links())
        assertEquals(listOf(Link(Relation.REMOVED_USER, LinkTarget.User(alice), "p")), RelayRemoveMemberEvent(id, relay, 1, tags, "", sig).links())
    }

    @Test
    fun membershipListLinksEveryMember() {
        val event =
            RelayMembershipListEvent(
                id,
                relay,
                1,
                arrayOf(arrayOf("-"), arrayOf("member", alice, "moderator"), arrayOf("member", bob), arrayOf("p", "3".repeat(64))),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.MEMBER, LinkTarget.User(alice), "member", MemberProps(roles = listOf("moderator"))),
                Link(Relation.MEMBER, LinkTarget.User(bob), "member"),
            ),
            event.links(),
        )
    }
}
