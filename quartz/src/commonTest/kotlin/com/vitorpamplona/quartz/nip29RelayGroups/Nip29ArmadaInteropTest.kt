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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.SupportedRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.PutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.RemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.JoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.LeaveRequestEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Interop tests asserting that Quartz can PARSE every NIP-29 event Armada
 * (gitlab.com/soapbox-pub/armada) produces — with the exact tag shapes Armada
 * emits — and BUILD events whose tags match what Armada expects. Tag shapes are
 * taken verbatim from Armada's `client/src/lib/nip29.ts` and its hooks.
 */
class Nip29ArmadaInteropTest {
    private val relaySelf = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val gid = "0123456789abcdef"

    private fun parse(
        kind: Int,
        tags: Array<Array<String>>,
        content: String = "",
        pubKey: String = relaySelf,
    ): Event =
        EventFactory.create(
            id = "00".repeat(32),
            pubKey = pubKey,
            createdAt = 1_700_000_000L,
            kind = kind,
            tags = tags,
            content = content,
            sig = "22".repeat(64),
        )

    // ── Relay-signed metadata (39000-39004) ──────────────────────────────────

    @Test
    fun parsesGroupMetadataWithEveryFlag() {
        // Armada relay29 emits presence flags as single-element tags.
        val e =
            parse(
                GroupMetadataEvent.KIND,
                arrayOf(
                    arrayOf("d", gid),
                    arrayOf("name", "General"),
                    arrayOf("about", "the main room"),
                    arrayOf("picture", "https://x/y.png"),
                    arrayOf("private"),
                    arrayOf("restricted"),
                    arrayOf("hidden"),
                    arrayOf("closed"),
                    arrayOf("livekit"),
                    arrayOf("supported_kinds", "9", "11"),
                ),
            )
        assertTrue(e is GroupMetadataEvent)
        assertEquals(gid, e.groupId())
        assertEquals("General", e.name())
        assertEquals("the main room", e.about())
        assertEquals("https://x/y.png", e.picture())
        assertTrue(e.isPrivate())
        assertTrue(e.isRestricted())
        assertTrue(e.isHidden())
        assertTrue(e.isClosed())
        assertTrue(e.hasLivekit())
        assertEquals(listOf(9, 11), e.supportedKinds())
    }

    @Test
    fun parsesOpenPublicGroupAsUnrestricted() {
        // Regression: a group with NO flag tags is public/open/visible — the old
        // accessors defaulted several of these to `true`.
        val e = parse(GroupMetadataEvent.KIND, arrayOf(arrayOf("d", gid), arrayOf("name", "Open"))) as GroupMetadataEvent
        assertFalse(e.isPrivate())
        assertFalse(e.isRestricted())
        assertFalse(e.isHidden())
        assertFalse(e.isClosed())
        assertFalse(e.hasLivekit())
        assertNull(e.supportedKinds())
        assertEquals("Open", e.name())
    }

    @Test
    fun fallsBackToGroupIdWhenNoName() {
        val e = parse(GroupMetadataEvent.KIND, arrayOf(arrayOf("d", gid))) as GroupMetadataEvent
        assertNull(e.name())
        assertEquals(gid, e.groupId())
    }

    @Test
    fun parsesGroupAdminsWithRoles() {
        // Armada: ["p", pubkey, "role1", "role2"]
        val e =
            parse(
                GroupAdminsEvent.KIND,
                arrayOf(
                    arrayOf("d", gid),
                    arrayOf("p", alice, "admin"),
                    arrayOf("p", bob, "moderator", "ceo"),
                ),
            ) as GroupAdminsEvent
        val admins = e.admins()
        assertEquals(2, admins.size)
        assertEquals(alice, admins[0].pubKey)
        assertEquals(listOf("admin"), admins[0].roles)
        assertEquals(listOf("moderator", "ceo"), admins[1].roles)
    }

    @Test
    fun parsesGroupMembers() {
        val e =
            parse(
                GroupMembersEvent.KIND,
                arrayOf(arrayOf("d", gid), arrayOf("p", alice), arrayOf("p", bob)),
            ) as GroupMembersEvent
        assertEquals(listOf(alice, bob), e.members())
    }

    @Test
    fun parsesGroupRoles() {
        val e =
            parse(
                SupportedRolesEvent.KIND,
                arrayOf(
                    arrayOf("role", "admin", "Full control"),
                    arrayOf("role", "moderator"),
                ),
            ) as SupportedRolesEvent
        val roles = e.roles()
        assertEquals("admin", roles[0].name)
        assertEquals("Full control", roles[0].description)
        assertEquals("moderator", roles[1].name)
        assertNull(roles[1].description)
    }

    @Test
    fun parsesGroupParticipants() {
        // Armada AV: ["participant", pubkey]
        val e =
            parse(
                GroupParticipantsEvent.KIND,
                arrayOf(arrayOf("d", gid), arrayOf("participant", alice), arrayOf("participant", bob)),
            )
        assertTrue(e is GroupParticipantsEvent)
        assertEquals(gid, e.groupId())
        assertEquals(listOf(alice, bob), e.participants())
    }

    // ── User moderation/request events (9xxx) ────────────────────────────────

    @Test
    fun parsesModerationEvents() {
        assertTrue(parse(CreateGroupEvent.KIND, arrayOf(arrayOf("h", gid)), pubKey = alice) is CreateGroupEvent)
        assertTrue(parse(DeleteGroupEvent.KIND, arrayOf(arrayOf("h", gid)), pubKey = alice) is DeleteGroupEvent)

        val put = parse(PutUserEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("p", bob, "moderator")), pubKey = alice) as PutUserEvent
        assertEquals(gid, put.groupId())
        assertEquals(listOf(bob), put.userPubKeys())

        val remove = parse(RemoveUserEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("p", bob)), content = "spam", pubKey = alice) as RemoveUserEvent
        assertEquals(listOf(bob), remove.userPubKeys())
        assertEquals("spam", remove.content)

        val del = parse(DeleteEventEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("e", "de".repeat(32))), pubKey = alice) as DeleteEventEvent
        assertEquals(listOf("de".repeat(32)), del.deletedEventIds())

        val invite = parse(CreateInviteEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("code", "abc123")), pubKey = alice) as CreateInviteEvent
        assertEquals("abc123", invite.code())

        val edit = parse(EditMetadataEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("name", "N"), arrayOf("public"), arrayOf("open")), pubKey = alice) as EditMetadataEvent
        assertEquals("N", edit.name())
        assertEquals(gid, edit.groupId())
    }

    @Test
    fun parsesJoinAndLeaveRequests() {
        val join = parse(JoinRequestEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("code", "inv1")), content = "let me in", pubKey = alice) as JoinRequestEvent
        assertEquals(gid, join.groupId())
        assertEquals("inv1", join.inviteCode())
        assertEquals("let me in", join.content)

        // Armada also sends a join with no code.
        val joinNoCode = parse(JoinRequestEvent.KIND, arrayOf(arrayOf("h", gid)), pubKey = alice) as JoinRequestEvent
        assertNull(joinNoCode.inviteCode())

        val leave = parse(LeaveRequestEvent.KIND, arrayOf(arrayOf("h", gid)), pubKey = alice) as LeaveRequestEvent
        assertEquals(gid, leave.groupId())
    }

    // ── Group-scoped content (kind 9/11/7) via the `h` tag ───────────────────

    @Test
    fun parsesGroupChatMessageWithHTag() {
        // A NIP-29 chat message is a kind-9 ChatEvent carrying an `h` tag.
        val e = parse(ChatEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("t", "gm")), content = "gm", pubKey = alice)
        assertTrue(e is ChatEvent)
        assertEquals(gid, e.groupId())
        assertTrue(e.isGroupScoped())
    }

    @Test
    fun parsesGroupThreadAndReactionWithHTag() {
        val thread = parse(ThreadEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("title", "T")), content = "body", pubKey = alice)
        assertTrue(thread is ThreadEvent)
        assertEquals(gid, thread.groupId())

        val react = parse(ReactionEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("e", "ee".repeat(32))), content = "🔥", pubKey = alice)
        assertEquals(gid, react.groupId())
    }

    @Test
    fun ungroupedEventHasNoGroupId() {
        val e = parse(ChatEvent.KIND, arrayOf(arrayOf("t", "hi")), content = "hi", pubKey = alice)
        assertNull(e.groupId())
        assertFalse(e.isGroupScoped())
    }

    // ── kind 10009 user group list (NIP-51 simple groups) ────────────────────

    @Test
    fun groupTagIdentityIsIdAndRelayNotName() {
        // Same (id, relay) is the same group regardless of the cached name, so a
        // Set dedups it — otherwise the same group stored as a public tag and a
        // private item would show twice and the joined-list flow would churn.
        val a = GroupTag(gid, "wss://r", "Alpha")
        val b = GroupTag(gid, "wss://r", null)
        val c = GroupTag(gid, "wss://other", "Alpha")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
        assertTrue(a != c)
    }

    @Test
    fun parsesUserGroupListPublicGroups() {
        // Armada: ["group", id, relay]
        val relay = "wss://relay.example.com"
        val e =
            parse(
                SimpleGroupListEvent.KIND,
                arrayOf(arrayOf("group", gid, relay), arrayOf("r", relay)),
                pubKey = alice,
            ) as SimpleGroupListEvent
        val groups = e.publicGroups()
        assertEquals(1, groups.size)
        assertEquals(gid, groups[0].groupId)
        assertEquals(relay, groups[0].relayUrl)
    }

    // ── Build side: tags match what Armada / relay29 expect ──────────────────

    @Test
    fun buildsGroupMetadataWithFlagsAndSupportedKinds() {
        val tags =
            GroupMetadataEvent
                .build(
                    groupId = gid,
                    name = "General",
                    about = "hi",
                    status = setOf(GroupMetadataEvent.GroupStatus.PRIVATE, GroupMetadataEvent.GroupStatus.CLOSED),
                    supportedKinds = listOf(9, 11),
                ).tags
        assertTrue(tags.any { it[0] == "d" && it[1] == gid })
        assertTrue(tags.any { it[0] == "name" && it[1] == "General" })
        assertTrue(tags.any { it.size == 1 && it[0] == "private" })
        assertTrue(tags.any { it.size == 1 && it[0] == "closed" })
        assertTrue(tags.any { it[0] == "supported_kinds" && it.drop(1) == listOf("9", "11") })

        // And it round-trips back through the parser.
        val parsed = parse(GroupMetadataEvent.KIND, tags) as GroupMetadataEvent
        assertTrue(parsed.isPrivate())
        assertTrue(parsed.isClosed())
        assertFalse(parsed.isRestricted())
        assertEquals(listOf(9, 11), parsed.supportedKinds())
    }

    @Test
    fun buildsCreateAndModerationEvents() {
        assertTrue(CreateGroupEvent.build(gid).tags.any { it[0] == "h" && it[1] == gid })
        assertTrue(CreateInviteEvent.build(gid, "code42").tags.any { it[0] == "code" && it[1] == "code42" })

        val put = PutUserEvent.build(gid, listOf(bob to listOf("moderator"))).tags
        assertTrue(put.any { it[0] == "p" && it[1] == bob && it.contains("moderator") })

        val remove = RemoveUserEvent.build(gid, listOf(bob)).tags
        assertTrue(remove.any { it[0] == "p" && it[1] == bob })

        // relay29 forks are avoided by NOT emitting `previous` when none provided.
        assertFalse(put.any { it[0] == "previous" })
    }

    @Test
    fun readsThreadTitleFromTitleOrSubject() {
        // NIP-7D uses a `title` tag; nostrord uses `subject`. We read either.
        val withTitle = parse(ThreadEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("title", "Welcome")), pubKey = alice) as ThreadEvent
        assertEquals("Welcome", withTitle.title())

        val withSubject = parse(ThreadEvent.KIND, arrayOf(arrayOf("h", gid), arrayOf("subject", "Howdy")), pubKey = alice) as ThreadEvent
        assertEquals("Howdy", withSubject.title())

        // `title` wins when both are present.
        val both = parse(ThreadEvent.KIND, arrayOf(arrayOf("title", "T"), arrayOf("subject", "S")), pubKey = alice) as ThreadEvent
        assertEquals("T", both.title())

        // We emit the spec-correct `title` tag, never `subject`.
        val built = ThreadEvent.build("body", "Hello") { hTag(gid) }
        assertTrue(built.tags.any { it[0] == "title" && it[1] == "Hello" })
        assertTrue(built.tags.none { it[0] == "subject" })
    }

    @Test
    fun buildsGroupThreadReplyWithHTagAndRootReference() {
        // A reply to a kind-11 group thread must be a 1111 comment that BOTH roots
        // at the thread AND carries the group `h` tag — the exact composition the
        // Android composer builds (CommentEvent.replyBuilder { hTag(...) }).
        val thread =
            parse(
                ThreadEvent.KIND,
                arrayOf(arrayOf("h", gid), arrayOf("title", "T")),
                content = "body",
                pubKey = alice,
            ) as ThreadEvent

        val reply = CommentEvent.replyBuilder("nice", EventHintBundle(thread)) { hTag(gid) }

        assertEquals(CommentEvent.KIND, reply.kind)
        assertEquals(gid, reply.tags.hTag())
        // References the thread it replies to (NIP-22 root scope carries the id).
        assertTrue(reply.tags.any { it.size > 1 && it[1] == thread.id })
    }

    @Test
    fun buildsJoinLeaveAndGroupScopedChat() {
        val join = JoinRequestEvent.build(gid, reason = "hi", inviteCode = "inv9")
        assertEquals("hi", join.content)
        assertTrue(join.tags.any { it[0] == "h" && it[1] == gid })
        assertTrue(join.tags.any { it[0] == "code" && it[1] == "inv9" })

        assertTrue(LeaveRequestEvent.build(gid).tags.any { it[0] == "h" && it[1] == gid })

        // Group-scope a plain kind-9 chat message via the shared `hTag` helper.
        val chat = ChatEvent.build("gm") { hTag(gid) }
        assertEquals(gid, chat.tags.hTag())
    }
}
