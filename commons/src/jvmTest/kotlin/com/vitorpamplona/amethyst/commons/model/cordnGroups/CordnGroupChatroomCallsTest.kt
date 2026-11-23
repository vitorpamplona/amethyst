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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import com.vitorpamplona.cordn.spec02Envelopes.CordnCallAnnouncement
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnStreamRows
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * cordn-web's group calls (`src/lib/chat/calls.ts`, 351a61c and follow-ups) and the other
 * addressable kinds a cordn group can carry, as the room renders them.
 *
 * The fixtures are cordn-web's own (`calls.test.ts`): the same call id, secret, member, service
 * and start time, and tags built the way `createCallAnnouncementTags` builds them. A call is
 * announced once when it opens and again when anyone closes it, so without a fold every call
 * leaves two empty bubbles behind, and the last one becomes the room's preview.
 */
class CordnGroupChatroomCallsTest {
    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)
    private val coordinator = "c".repeat(64)
    private val callId = "11".repeat(32)
    private val identitySecret = "11".repeat(32)
    private val service = "https://hushwire.exe.xyz"
    private val startsAt = 1_762_000_000L

    /** `createCallAnnouncementTags`, field for field. */
    private fun callTags(
        status: String = "open",
        id: String = callId,
        endsAt: Long? = null,
        title: String? = null,
    ): Array<Array<String>> {
        val tags = mutableListOf(arrayOf("d", id), arrayOf("service", service), arrayOf("status", status))
        if (status == "open") tags += arrayOf("identity-secret", identitySecret)
        tags += arrayOf("starts", startsAt.toString())
        if (endsAt != null) tags += arrayOf("ends", endsAt.toString())
        if (title != null) tags += arrayOf("room", title)
        tags += arrayOf("alt", if (status == "closed") "Call ended" else "Call started")
        return tags.toTypedArray()
    }

    private fun message(
        id: String,
        at: Long,
        cursor: Long,
        author: HexKey = alice,
        content: String = "",
        kind: Int = CordnCallAnnouncement.KIND,
        tags: Array<Array<String>> = callTags(),
    ) = CordnDeliveredMessage(
        CordnEnvelope(id = id.padEnd(64, '0'), pubKey = author, createdAt = at, kind = kind, tags = tags, content = content),
        cursor,
    )

    private fun text(
        id: String,
        at: Long,
        cursor: Long,
        author: HexKey = alice,
    ) = message(id, at, cursor, author, content = "msg-$id", kind = CordnMessageKinds.TEXT, tags = emptyArray())

    private fun room(account: HexKey = "") = CordnGroupChatroom("gid-1", coordinator, accountPubKey = account)

    private fun CordnGroupChatroom.ids() = messages.value.map { it.envelope.id.trimEnd('0') }

    private fun CordnGroupChatroom.lastRow() = messages.value.last().envelope

    private fun CordnGroupChatroom.newestId() = newest.value?.envelope?.id

    @Test
    fun `a call's revisions are one row, the newest across authors`() {
        // calls.test.ts: "uses greatest created_at and then greatest id across authors". Any
        // member may close a call, so the address is `d` alone, not (kind, author, d).
        val room = room()
        room.add(message("b", at = 100, cursor = 1))
        room.add(text("2", at = 150, cursor = 2))
        room.add(message("a", at = 200, cursor = 3, author = bob, tags = callTags("closed", endsAt = 300)))

        assertEquals(listOf("2", "a"), room.ids())
        val call = assertIs<CordnStreamRows.Display.Call>(CordnStreamRows.display(room.lastRow()))
        assertTrue(!call.announcement.isOpen)
    }

    @Test
    fun `a created_at tie goes to the greater id, as cordn-web breaks it`() {
        val room = room()
        room.add(message("a", at = 200, cursor = 1, tags = callTags("closed")))
        room.add(message("f", at = 200, cursor = 2))

        assertEquals(listOf("f"), room.ids())

        // Order of arrival does not matter.
        val reversed = room()
        reversed.add(message("f", at = 200, cursor = 2))
        reversed.add(message("a", at = 200, cursor = 1, tags = callTags("closed")))
        assertEquals(listOf("f"), reversed.ids())
    }

    @Test
    fun `different calls stay apart`() {
        val room = room()
        room.add(message("1", at = 100, cursor = 1))
        room.add(message("2", at = 110, cursor = 2, tags = callTags(id = "22".repeat(32))))
        room.add(message("3", at = 120, cursor = 3, tags = callTags("closed")))

        assertEquals(listOf("2", "3"), room.ids())
    }

    @Test
    fun `a malformed announcement keeps its row, so it can say it is unsupported`() {
        // cordn-web keeps announcements it cannot parse ("Unsupported call announcement") rather
        // than hiding them, and they take no part in the fold.
        val room = room()
        room.add(message("1", at = 100, cursor = 1))
        room.add(message("2", at = 200, cursor = 2, tags = callTags().filter { it[0] != "starts" }.toTypedArray()))

        assertEquals(listOf("1", "2"), room.ids())
        assertIs<CordnStreamRows.Display.UnsupportedCall>(CordnStreamRows.display(room.lastRow()))
    }

    @Test
    fun `the preview is the call's current revision, never a superseded one`() {
        val room = room()
        room.add(text("1", at = 10, cursor = 1))
        room.add(message("2", at = 100, cursor = 2))
        assertEquals("2".padEnd(64, '0'), room.newestId())

        // Closed after someone spoke: the call's row moves to where its newest revision sits.
        room.add(text("3", at = 150, cursor = 3))
        room.add(message("4", at = 200, cursor = 4, author = bob, tags = callTags("closed")))
        assertEquals(listOf("1", "3", "4"), room.ids())
        assertEquals("4".padEnd(64, '0'), room.newestId())

        // A stale revision delivered late (a re-sync) changes neither the row nor the preview.
        room.add(message("5", at = 90, cursor = 5))
        assertEquals(listOf("1", "3", "4"), room.ids())
        assertEquals("4".padEnd(64, '0'), room.newestId())
    }

    @Test
    fun `call revisions are not unread messages`() {
        val room = room(account = alice)
        room.add(message("1", at = 100, cursor = 1, author = bob))
        room.add(message("2", at = 200, cursor = 2, author = bob, tags = callTags("closed")))
        assertEquals(0, room.unreadCount.value)

        room.add(text("3", at = 210, cursor = 3, author = bob))
        assertEquals(1, room.unreadCount.value)
    }

    @Test
    fun `other addressable kinds replace by kind, author and d`() {
        // NIP-01: an addressable event is (kind, pubkey, d); the newest wins and a tie goes to
        // the lower id. Another member's event under the same d is a different event.
        val longForm = 30023
        val d = arrayOf(arrayOf("d", "notes"))
        val room = room()
        room.add(message("1", at = 100, cursor = 1, kind = longForm, tags = d, content = "v1"))
        room.add(message("2", at = 100, cursor = 2, author = bob, kind = longForm, tags = d, content = "bob's"))
        room.add(message("4", at = 200, cursor = 3, kind = longForm, tags = d, content = "v2"))
        room.add(message("3", at = 200, cursor = 4, kind = longForm, tags = d, content = "v2 tie"))

        assertEquals(listOf("2", "3"), room.ids())
    }

    @Test
    fun `an addressable kind without a d is addressed by the empty d`() {
        val room = room()
        room.add(message("1", at = 100, cursor = 1, kind = 30078, tags = emptyArray(), content = "a"))
        room.add(message("2", at = 200, cursor = 2, kind = 30078, tags = arrayOf(arrayOf("d", "")), content = "b"))

        assertEquals(listOf("2"), room.ids())
    }

    @Test
    fun `an unknown kind with nothing to show is hidden, and with an alt shows the alt`() {
        val room = room()
        room.add(text("1", at = 10, cursor = 1))
        room.add(message("2", at = 20, cursor = 2, kind = 4242, tags = emptyArray(), content = "  "))
        assertEquals(listOf("1"), room.ids())
        assertEquals("1".padEnd(64, '0'), room.newestId(), "a blank row is never the preview")

        room.add(message("3", at = 30, cursor = 3, kind = 4242, tags = arrayOf(arrayOf("alt", "A thing happened")), content = ""))
        assertEquals(listOf("1", "3"), room.ids())
        assertEquals(CordnStreamRows.Display.Alt("A thing happened"), CordnStreamRows.display(room.lastRow()))

        // Content of its own renders as before.
        room.add(message("4", at = 40, cursor = 4, kind = 4242, tags = emptyArray(), content = "words"))
        assertEquals(CordnStreamRows.Display.Regular, CordnStreamRows.display(room.lastRow()))
    }

    @Test
    fun `a restored preview that would be a blank row is ignored`() {
        val room = room()
        room.restorePreview(message("1", at = 10, cursor = 1, kind = 4242, tags = emptyArray(), content = ""))
        assertNull(room.newest.value)

        // A call revision is fine: it renders as a call line.
        room.restorePreview(message("2", at = 20, cursor = 2))
        assertEquals("2".padEnd(64, '0'), room.newestId())
    }

    @Test
    fun `calendar events keep cordn-kmp's author-scoped fold`() {
        // 31922/31923 are addressable too, but cordn-kmp's CordnCalendar already folds them
        // (and validates them); a second fold must not fight it.
        val tags = arrayOf(arrayOf("d", "standup"), arrayOf("title", "Standup"), arrayOf("start", "1757000000"))
        val room = room()
        room.add(message("1", at = 10, cursor = 1, kind = CordnMessageKinds.CALENDAR_TIME, tags = tags))
        room.add(message("2", at = 20, cursor = 2, author = bob, kind = CordnMessageKinds.CALENDAR_TIME, tags = tags))

        assertEquals(listOf("1", "2"), room.ids())
    }
}
