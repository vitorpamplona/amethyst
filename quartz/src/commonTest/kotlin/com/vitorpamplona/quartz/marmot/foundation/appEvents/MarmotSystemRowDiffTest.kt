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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Group system rows are derived, not received, so the derivation itself is the
 * interoperability surface: two clients that applied the same commits must
 * write the same rows, in the same order, without exchanging one.
 */
class MarmotSystemRowDiffTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun snapshot(
        members: Set<String> = setOf(alice),
        admins: Set<String> = setOf(alice),
        name: String = "group",
        avatar: String = "",
        disbanded: Boolean = false,
    ) = MarmotGroupSnapshot(
        members = members,
        admins = admins,
        name = name,
        hasAvatar = avatar.isNotEmpty(),
        avatarFingerprint = avatar,
        isDisbanded = disbanded,
    )

    @Test
    fun `an unchanged snapshot produces no rows`() {
        // The baseline case, and the one that keeps a timeline from filling
        // with captions every time state is merely re-observed.
        assertEquals(emptyList(), MarmotSystemRowDiff.diff(snapshot(), snapshot()))
    }

    @Test
    fun `an added member produces one member_added row naming them`() {
        val rows = MarmotSystemRowDiff.diff(snapshot(), snapshot(members = setOf(alice, bob)), actor = alice)
        assertEquals(1, rows.size)
        assertEquals(MarmotSystemType.MEMBER_ADDED, rows[0].systemType)
        assertEquals(bob, rows[0].subject)
        assertEquals(alice, rows[0].actor)
    }

    @Test
    fun `a member who removed themselves left, anyone else was removed`() {
        // The registry distinguishes these and the only thing that can tell
        // them apart is whether the committer is the departing account.
        val before = snapshot(members = setOf(alice, bob))
        val after = snapshot(members = setOf(alice))

        assertEquals(MarmotSystemType.MEMBER_LEFT, MarmotSystemRowDiff.diff(before, after, actor = bob)[0].systemType)
        assertEquals(MarmotSystemType.MEMBER_REMOVED, MarmotSystemRowDiff.diff(before, after, actor = alice)[0].systemType)
        assertEquals(MarmotSystemType.MEMBER_REMOVED, MarmotSystemRowDiff.diff(before, after)[0].systemType)
    }

    @Test
    fun `admin changes are about the policy, not about presence`() {
        // Bob is added and promoted in one commit: both rows are true, and a
        // client that collapsed them would lose who can act in the group.
        val rows =
            MarmotSystemRowDiff.diff(
                snapshot(),
                snapshot(members = setOf(alice, bob), admins = setOf(alice, bob)),
                actor = alice,
            )
        assertEquals(
            listOf(MarmotSystemType.MEMBER_ADDED, MarmotSystemType.ADMIN_ADDED),
            rows.map { it.systemType },
        )
    }

    @Test
    fun `rows come out in a fixed order regardless of set iteration`() {
        // Departures, then arrivals, then rights, then the group-wide changes.
        // Nothing here may depend on hash order: two clients would then show
        // the same history differently forever.
        val rows =
            MarmotSystemRowDiff.diff(
                snapshot(members = setOf(alice, bob), admins = setOf(alice, bob), name = "old"),
                snapshot(members = setOf(alice, carol), admins = setOf(alice), name = "new", avatar = "url:https://x.test/a.png"),
                actor = alice,
            )
        assertEquals(
            listOf(
                MarmotSystemType.MEMBER_REMOVED,
                MarmotSystemType.MEMBER_ADDED,
                MarmotSystemType.ADMIN_REMOVED,
                MarmotSystemType.GROUP_RENAMED,
                MarmotSystemType.GROUP_AVATAR_CHANGED,
            ),
            rows.map { it.systemType },
        )
        assertEquals("new", rows.first { it.systemType == MarmotSystemType.GROUP_RENAMED }.name)
    }

    @Test
    fun `replacing one avatar with another is a change`() {
        // A bare "does it have one" flag would call this a no-op and the
        // group's picture would change with nothing said about it.
        val rows =
            MarmotSystemRowDiff.diff(
                snapshot(avatar = "url:https://x.test/a.png"),
                snapshot(avatar = "blossom:aabb"),
            )
        assertEquals(listOf(MarmotSystemType.GROUP_AVATAR_CHANGED), rows.map { it.systemType })
    }

    @Test
    fun `disband is emitted once because the state is absorbing`() {
        val disbanded = snapshot(disbanded = true)
        assertEquals(
            listOf(MarmotSystemType.GROUP_DISBANDED),
            MarmotSystemRowDiff.diff(snapshot(), disbanded).map { it.systemType },
        )
        assertEquals(emptyList(), MarmotSystemRowDiff.diff(disbanded, disbanded))
    }

    @Test
    fun `a snapshot survives a round trip through its stored form`() {
        val original = snapshot(members = setOf(alice, bob), admins = setOf(bob), name = "a \"quoted\" name", avatar = "url:https://x.test/a.png")
        assertEquals(original, MarmotGroupSnapshot.decode(original.encode()))
        // Encoding is stable, so re-storing unchanged state cannot look like a change.
        assertEquals(original.encode(), original.copy(members = setOf(bob, alice)).encode())
    }

    @Test
    fun `unreadable stored state is no baseline rather than a crash`() {
        assertNull(MarmotGroupSnapshot.decode("not json"))
    }

    @Test
    fun `a row with a quote in its text stays valid JSON`() {
        // The escape was written as the literal text `ESC"`, which produced a
        // content string no decoder could read back — and a 1210's content is
        // inside the app event's id preimage, so a peer would reject the row
        // outright rather than merely mis-render it.
        val row = MarmotSystemEvent(MarmotSystemType.GROUP_RENAMED, actor = alice, name = "x", text = "renamed to \"quoted\"")
        val json = row.toContentJson()
        assertTrue(!json.contains("ESC"), json)

        val event = row.toAppEvent(alice, 1700000000L)
        val decoded = MarmotSystemEvent.fromAppEvent(MarmotAppEvent.decode(event.toJson()))
        assertEquals("renamed to \"quoted\"", decoded?.text)
    }
}
