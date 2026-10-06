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
package com.vitorpamplona.quartz.buzz

import com.vitorpamplona.quartz.buzz.apPersonas.PersonaContent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryContent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentContent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.PresenceSnapshotEvent
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogContent
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamContent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * The per-instance parsed-body caches remember a malformed body as a failure without holding
 * its exception: the nullable accessors stay null, and every throwing accessor throws a fresh
 * exception of the same type a direct decode throws.
 */
class ParsedBodyCacheTest {
    private val id = "0".repeat(64)
    private val author = "f".repeat(64)
    private val sig = "0".repeat(128)
    private val malformed = "{not json"

    private fun assertThrowsFreshEachTime(
        expected: Throwable,
        accessor: () -> Any?,
    ) {
        val first = assertFails { accessor() }
        val second = assertFails { accessor() }
        assertEquals(expected::class, first::class)
        assertEquals(expected::class, second::class)
        assertNotSame(first, second)
    }

    @Test
    fun malformedBodiesAreCachedAsFailuresNotExceptions() {
        val persona = PersonaEvent(id, author, 1, arrayOf(arrayOf("d", "p")), malformed, sig)
        assertNull(persona.personaOrNull())
        assertThrowsFreshEachTime(assertFails { PersonaContent.decodeFromJson(malformed) }) { persona.persona() }

        val team = TeamEvent(id, author, 1, arrayOf(arrayOf("d", "t")), malformed, sig)
        assertNull(team.teamOrNull())
        assertThrowsFreshEachTime(assertFails { TeamContent.decodeFromJson(malformed) }) { team.team() }

        val agent = ManagedAgentEvent(id, author, 1, arrayOf(arrayOf("d", author)), malformed, sig)
        assertNull(agent.agentOrNull())
        assertEquals(listOf(author), agent.linkedPubKeys())
        assertThrowsFreshEachTime(assertFails { ManagedAgentContent.decodeFromJson(malformed) }) { agent.agent() }

        val summary = ThreadSummaryEvent(id, author, 1, emptyArray(), malformed, sig)
        assertNull(summary.summaryOrNull())
        assertThrowsFreshEachTime(assertFails { ThreadSummaryContent.decodeFromJson(malformed) }) { summary.summary() }

        assertNull(SystemMessageEvent(id, author, 1, emptyArray(), malformed, sig).payload())
        assertNull(ChannelSummaryEvent(id, author, 1, emptyArray(), malformed, sig).summary())
        assertNull(PresenceSnapshotEvent(id, author, 1, emptyArray(), malformed, sig).snapshot())
    }

    @Test
    fun aCatalogThatFailsValidationKeepsThrowingIllegalArgument() {
        val v2 = TeamCatalogContent(v = 2, name = "T", members = emptyList()).encodeToJson()
        val catalog = TeamCatalogEvent(id, author, 1, arrayOf(arrayOf("d", "team")), v2, sig)
        assertNull(catalog.catalogOrNull())
        assertThrowsFreshEachTime(IllegalArgumentException()) { catalog.catalog() }

        val garbled = TeamCatalogEvent(id, author, 1, arrayOf(arrayOf("d", "team")), malformed, sig)
        assertNull(garbled.catalogOrNull())
        assertThrowsFreshEachTime(assertFails { TeamCatalogContent.decodeFromJson(malformed) }) { garbled.catalog() }
    }
}
