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
package com.vitorpamplona.quartz.buzz.teams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamEventTest {
    @Test
    fun buildSetsKindDTagAndContent() {
        val team =
            TeamContent(
                name = "Test Team",
                description = "A test team",
                instructions = "Coordinate carefully.",
                personaIds = listOf("p1", "p2"),
            )

        val template = TeamEvent.build(team, teamId = "team-123")

        assertEquals(TeamEvent.KIND, template.kind)
        val dTags = template.tags.filter { it[0] == "d" }
        assertEquals(1, dTags.size)
        assertEquals("team-123", dTags.first()[1])

        assertEquals(team, TeamContent.decodeFromJson(template.content))
    }

    @Test
    fun accessorsReadFromEvent() {
        val json = """{"name":"Squad","persona_ids":["a","b"],"instructions":"go"}"""
        val event =
            TeamEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "squad-1")),
                content = json,
                sig = "00",
            )

        assertEquals("squad-1", event.teamId())
        val team = event.team()
        assertEquals("Squad", team.name)
        assertEquals(listOf("a", "b"), team.personaIds)
        assertEquals("go", team.instructions)
        assertNull(team.description)
    }

    @Test
    fun legacyContentWithoutPersonaIdsDecodesNull() {
        // Events predating always-publish carry no persona_ids; the field must
        // read back as null (not an empty list) so the caller can preserve local
        // membership. See TeamContent's wire-semantics caveat.
        val team = TeamContent.decodeFromJson("""{"name":"Old Team"}""")
        assertEquals("Old Team", team.name)
        assertNull(team.personaIds)
        assertNull(team.instructions)
    }
}
