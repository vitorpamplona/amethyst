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
package com.vitorpamplona.quartz.buzz.agentProfiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentProfileEventTest {
    @Test
    fun buildIsReplaceableWithEmptyDTagAndContent() {
        val profile = AgentProfileContent(channelAddPolicy = "owner_only")
        val template = AgentProfileEvent.build(profile)

        assertEquals(AgentProfileEvent.KIND, template.kind)
        // Replaceable: no d tag is added by the builder.
        assertTrue(template.tags.none { it[0] == "d" })
        assertEquals(profile, AgentProfileContent.decodeFromJson(template.content))
    }

    @Test
    fun channelAddPolicyIsReadBack() {
        // The only field the relay side effect (handle_agent_profile) reads.
        val event =
            AgentProfileEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = emptyArray(),
                content = """{"channel_add_policy":"anyone"}""",
                sig = "00",
            )
        assertEquals("anyone", event.profile().channelAddPolicy)
    }

    @Test
    fun looseAgentMetadataDecodesAndToleratesUnknownKeys() {
        // Discovery-shaped content with an extra field the model does not declare.
        val json =
            """{"name":"Scout","display_name":"Scout Bot","agent_type":"agent","status":"online","capabilities":["search"],"channel_ids":["c1"],"unknown_extra":42}"""
        val profile = AgentProfileContent.decodeFromJson(json)
        assertEquals("Scout", profile.name)
        assertEquals("Scout Bot", profile.displayName)
        assertEquals("agent", profile.agentType)
        assertEquals("online", profile.status)
        assertEquals(listOf("search"), profile.capabilities)
        assertEquals(listOf("c1"), profile.channelIds)
        assertNull(profile.channelAddPolicy)
    }

    @Test
    fun malformedContentReturnsNull() {
        val event =
            AgentProfileEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = emptyArray(),
                content = "not-json",
                sig = "00",
            )
        assertNull(event.profileOrNull())
    }
}
