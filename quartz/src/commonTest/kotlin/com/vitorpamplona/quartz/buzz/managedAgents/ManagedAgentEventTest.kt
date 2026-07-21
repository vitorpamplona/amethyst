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
package com.vitorpamplona.quartz.buzz.managedAgents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ManagedAgentEventTest {
    @Test
    fun buildSetsKindDTagAndContent() {
        val agent =
            ManagedAgentContent(
                name = "Test Agent",
                personaId = "persona-1",
                parallelism = 24,
                respondTo = RespondTo.ALLOWLIST,
                respondToAllowlist = listOf("79be667e"),
            )

        val template = ManagedAgentEvent.build(agent, agentPubKey = "agentpubkeyhex")

        assertEquals(ManagedAgentEvent.KIND, template.kind)
        val dTags = template.tags.filter { it[0] == "d" }
        assertEquals(1, dTags.size)
        assertEquals("agentpubkeyhex", dTags.first()[1])

        assertEquals(agent, ManagedAgentContent.decodeFromJson(template.content))
    }

    @Test
    fun respondToWireValuesAreKebabCase() {
        // owner-only is the harness/default wire spelling.
        val json =
            ManagedAgentContent(
                name = "A",
                parallelism = 1,
                respondTo = RespondTo.OWNER_ONLY,
            ).encodeToJson()
        assertEquals(
            RespondTo.OWNER_ONLY,
            ManagedAgentContent.decodeFromJson(json).respondTo,
        )
        // JSON must carry the kebab-case string, not the Kotlin enum name.
        assertEquals(true, json.contains("\"owner-only\""))
        assertFalse(json.contains("OWNER_ONLY"))
    }

    @Test
    fun accessorsReadFromEvent() {
        val json =
            """{"name":"Scout","persona_id":"p1","parallelism":2,"respond_to":"anyone"}"""
        val event =
            ManagedAgentEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "agentpubkeyhex")),
                content = json,
                sig = "00",
            )

        assertEquals("agentpubkeyhex", event.agentPubKey())
        val agent = event.agent()
        assertEquals("Scout", agent.name)
        assertEquals("p1", agent.personaId)
        assertEquals(2, agent.parallelism)
        assertEquals(RespondTo.ANYONE, agent.respondTo)
        // Definition-linked events omit the definition quad.
        assertNull(agent.systemPrompt)
        assertNull(agent.model)
    }

    @Test
    fun standaloneAgentKeepsDefinitionQuad() {
        val json =
            """{"name":"Solo","system_prompt":"sp","model":"m","provider":"pr","persona_source_version":"v1","parallelism":1,"respond_to":"owner-only"}"""
        val agent = ManagedAgentContent.decodeFromJson(json)
        assertEquals("sp", agent.systemPrompt)
        assertEquals("m", agent.model)
        assertEquals("pr", agent.provider)
        assertEquals("v1", agent.personaSourceVersion)
    }
}
