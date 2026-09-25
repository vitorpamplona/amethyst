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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The name a coordinator this account already uses calls itself.
 *
 * Discovery reads this from a live sweep, but a coordinator already in the list
 * never goes through discovery again -- so the rule has to hold on whatever a
 * one-shot fetch happens to return, including a lagging relay's stale copy.
 */
class AnnouncedServerNameTest {
    private val coordinator = "c".repeat(64)

    @Test
    fun `the name comes off the server announcement`() {
        assertEquals("Cordn Demo", announcedServerName(listOf(announcement(name = "Cordn Demo"))))
    }

    @Test
    fun `a coordinator that announces no name has none, rather than an empty one`() {
        // Absent and blank have to read the same, or the display chain stops at
        // a name that renders as nothing instead of falling through to the
        // profile and then the key.
        assertNull(announcedServerName(listOf(announcement(name = ""))))
        assertNull(announcedServerName(listOf(announcement(name = "   "))))
        assertNull(announcedServerName(emptyList()))
    }

    @Test
    fun `the newest announcement wins, whatever order the relays answered in`() {
        // These kinds are replaceable. A relay that is behind will happily hand
        // back last month's name after a fresher one already arrived, and taking
        // the last event seen would show the old one.
        val stale = announcement(name = "Old Name", createdAt = 1_000)
        val fresh = announcement(name = "New Name", createdAt = 2_000)

        assertEquals("New Name", announcedServerName(listOf(fresh, stale)))
        assertEquals("New Name", announcedServerName(listOf(stale, fresh)))
    }

    @Test
    fun `the other announcement kinds do not supply a name`() {
        // CEP-6 has five announcement kinds and only the server announcement
        // carries the surface. A tools list with a name tag is not this server
        // saying what it is called.
        val tools =
            event(
                kind = CvmKinds.TOOLS_LIST,
                createdAt = 5_000,
                content = """{"tools":[]}""",
                tags = arrayOf(arrayOf("name", "Tools List")),
            )

        assertNull(announcedServerName(listOf(tools)))
    }

    private fun announcement(
        name: String,
        createdAt: Long = 1_000,
    ) = event(
        kind = CvmKinds.SERVER_ANNOUNCEMENT,
        createdAt = createdAt,
        content = """{"protocolVersion":"2025-11-25"}""",
        tags = arrayOf(arrayOf("name", name)),
    )

    /** Unsigned: nothing here verifies a signature, and an announcement proves nothing either way. */
    private fun event(
        kind: Int,
        createdAt: Long,
        content: String,
        tags: Array<Tag> = emptyArray(),
        pubKey: HexKey = coordinator,
    ) = Event(
        id = "${kind}_$createdAt",
        pubKey = pubKey,
        createdAt = createdAt,
        kind = kind,
        tags = tags,
        content = content,
        sig = "00".repeat(32),
    )
}
