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
package com.vitorpamplona.quartz.buzz.apPersonas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonaEventTest {
    @Test
    fun buildSetsKindDTagAndContent() {
        val persona =
            PersonaContent(
                displayName = "Code Reviewer",
                systemPrompt = "You review code.",
                avatarUrl = "https://example.com/a.png",
                runtime = "goose",
                model = "claude-opus-4",
                provider = "anthropic",
                namePool = listOf("Ada", "Grace"),
                respondTo = "owner-only",
                respondToAllowlist = listOf("79be667e"),
                parallelism = 3,
            )

        val template = PersonaEvent.build(persona, slug = "code-reviewer")

        assertEquals(PersonaEvent.KIND, template.kind)
        val dTags = template.tags.filter { it[0] == "d" }
        assertEquals(1, dTags.size)
        assertEquals("code-reviewer", dTags.first()[1])

        // Content round-trips through the projection.
        assertEquals(persona, PersonaContent.decodeFromJson(template.content))
    }

    @Test
    fun accessorsReadFromEvent() {
        val json =
            """{"display_name":"Scout","system_prompt":"p","model":"gpt-4o","name_pool":["x"]}"""
        val event =
            PersonaEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "scout")),
                content = json,
                sig = "00",
            )

        assertEquals("scout", event.slug())
        val persona = event.persona()
        assertEquals("Scout", persona.displayName)
        assertEquals("p", persona.systemPrompt)
        assertEquals("gpt-4o", persona.model)
        assertEquals(listOf("x"), persona.namePool)
        // Optionals absent from the JSON decode to null / empty defaults.
        assertNull(persona.provider)
        assertNull(persona.parallelism)
        assertTrue(persona.respondToAllowlist.isEmpty())
    }

    @Test
    fun minimalContentDecodes() {
        // The NIP-AP revision allows a display-name-only (config) persona.
        val persona = PersonaContent.decodeFromJson("""{"display_name":"Config Only"}""")
        assertEquals("Config Only", persona.displayName)
        assertNull(persona.systemPrompt)
    }

    @Test
    fun malformedContentReturnsNull() {
        val event =
            PersonaEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "x")),
                content = "not-json",
                sig = "00",
            )
        assertNull(event.personaOrNull())
    }
}
