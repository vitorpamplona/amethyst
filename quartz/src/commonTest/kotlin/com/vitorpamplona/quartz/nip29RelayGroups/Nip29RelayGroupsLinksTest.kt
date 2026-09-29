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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.event.allLinks
import com.vitorpamplona.quartz.graph.props.OrderProps
import com.vitorpamplona.quartz.graph.props.RoleProps
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip29RelayGroupsLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val relay = "f".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val note = "3".repeat(64)
    private val article = "30023:$alice:post"
    private val group = Link(Relation.GROUP, LinkTarget.Tag("h", "pizza"), "h")

    @Test
    fun putAndRemoveUserLinkTheGroupAndTheMember() {
        val tags = arrayOf(arrayOf("h", "pizza"), arrayOf("p", alice, "admin"), arrayOf("previous", "abcd1234"))
        assertEquals(
            listOf(group, Link(Relation.ADDED_USER, LinkTarget.User(alice), "p", RoleProps(roles = listOf("admin")))),
            GroupPutUserEvent(id, alice, 1, tags, "", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.REMOVED_USER, LinkTarget.User(alice), "p")),
            GroupRemoveUserEvent(id, alice, 1, tags, "", sig).links(),
        )
    }

    @Test
    fun requestsAndGroupCommandsLinkOnlyTheGroup() {
        val tags = arrayOf(arrayOf("h", "pizza"), arrayOf("code", "secret"), arrayOf("name", "Pizza"))
        assertEquals(listOf(group), GroupJoinRequestEvent(id, bob, 1, tags, "", sig).links())
        assertEquals(listOf(group), CreateGroupEvent(id, bob, 1, tags, "", sig).links())
    }

    @Test
    fun deleteEventLinksTheDeletedEvents() {
        val event =
            GroupDeleteEventEvent(
                id,
                alice,
                1,
                arrayOf(arrayOf("h", "pizza"), arrayOf("e", note), arrayOf("e", "short")),
                "",
                sig,
            )
        assertEquals(listOf(group, Link(Relation.DELETED, LinkTarget.Event(note), "e")), event.links())
    }

    @Test
    fun editMetadataLinksSubgroupsAsGroupsAndItsTopics() {
        val event =
            GroupEditMetadataEvent(
                id,
                alice,
                1,
                arrayOf(
                    arrayOf("h", "pizza"),
                    arrayOf("name", "Pizza"),
                    arrayOf("parent", "food"),
                    arrayOf("child", "margherita"),
                    arrayOf("t", "Italian"),
                    arrayOf("g", "u4pr"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                group,
                Link(Relation.PARENT, LinkTarget.Tag("h", "food"), "parent"),
                Link(Relation.CHILD, LinkTarget.Tag("h", "margherita"), "child"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "italian"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
            ),
            event.links(),
        )
    }

    @Test
    fun pinListsCarryTheirOrder() {
        val tags = arrayOf(arrayOf("h", "pizza"), arrayOf("a", article), arrayOf("e", "bad"), arrayOf("e", note, "wss://relay.example/"))
        val pins =
            listOf(
                Link(Relation.PIN, LinkTarget.Address(article), "a", OrderProps(order = 0)),
                Link(Relation.PIN, LinkTarget.Event(note), "e", OrderProps(order = 1)),
            )
        assertEquals(listOf(group) + pins, GroupUpdatePinListEvent(id, alice, 1, tags, "", sig).links())
        // The relay-signed 39005: its group is its own d, which is not linked.
        val pinned = GroupPinnedEvent(id, relay, 1, arrayOf(arrayOf("d", "pizza")) + tags.drop(1), "", sig)
        assertEquals(pins, pinned.links())
    }

    @Test
    fun metadataLinksSubgroupsAndTopicsButNotBuzzChannelTypes() {
        val event =
            GroupMetadataEvent(
                id,
                relay,
                1,
                arrayOf(
                    arrayOf("d", "pizza"),
                    arrayOf("name", "Pizza"),
                    arrayOf("parent", "food"),
                    arrayOf("child", "margherita"),
                    arrayOf("child", "calzone"),
                    arrayOf("t", "forum"),
                    arrayOf("t", "Italian"),
                    arrayOf("g", "u4pr"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.PARENT, LinkTarget.Tag("h", "food"), "parent"),
                Link(Relation.CHILD, LinkTarget.Tag("h", "margherita"), "child"),
                Link(Relation.CHILD, LinkTarget.Tag("h", "calzone"), "child"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "italian"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("g", "u4pr"), "g"),
            ),
            event.links(),
        )
    }

    @Test
    fun relaySignedListsLinkTheirPeopleButNotTheGroupInTheirD() {
        val tags = arrayOf(arrayOf("d", "pizza"), arrayOf("p", alice, "admin", "moderator"), arrayOf("p", "short"))
        assertEquals(listOf(Link(Relation.ADMIN, LinkTarget.User(alice), "p", RoleProps(roles = listOf("admin", "moderator")))), GroupAdminsEvent(id, relay, 1, tags, "", sig).links())
        assertEquals(listOf(Link(Relation.MEMBER, LinkTarget.User(alice), "p")), GroupMembersEvent(id, relay, 1, tags, "", sig).links())
        assertEquals(
            listOf(Link(Relation.PARTICIPANT, LinkTarget.User(bob), "participant")),
            GroupParticipantsEvent(id, relay, 1, arrayOf(arrayOf("d", "pizza"), arrayOf("participant", bob)), "", sig).links(),
        )
        // Only the event's own ADDRESS names the group.
        assertEquals(
            listOf(
                Link(Relation.AUTHOR, LinkTarget.User(relay)),
                Link(Relation.ADDRESS, LinkTarget.Address("39002:$relay:pizza")),
                Link(Relation.MEMBER, LinkTarget.User(alice), "p"),
            ),
            GroupMembersEvent(id, relay, 1, tags, "", sig).allLinks(),
        )
    }
}
