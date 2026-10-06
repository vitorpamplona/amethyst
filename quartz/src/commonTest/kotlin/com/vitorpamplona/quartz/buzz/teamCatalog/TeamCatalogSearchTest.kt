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
package com.vitorpamplona.quartz.buzz.teamCatalog

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamCatalogSearchTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun teamAndMemberTextIsIndexed() {
        val catalog =
            TeamCatalogContent(
                v = 1,
                name = "Docs Squad",
                description = "Writes the handbook",
                instructions = "Keep it short",
                members =
                    listOf(
                        TeamCatalogMember(memberKey = "a", displayName = "Editor", systemPrompt = "Edit for clarity"),
                        TeamCatalogMember(memberKey = "b", displayName = "Fact Checker"),
                    ),
            )
        val template = TeamCatalogEvent.build(catalog, "docs", shared = true, createdAt = 1)
        val event = TeamCatalogEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals("Docs Squad\nWrites the handbook\nKeep it short\nEditor\nEdit for clarity\nFact Checker", event.indexableContent())

        val rejoined = mutableListOf<String>()
        event.forEachIndexableField { field ->
            if (field != null) rejoined.add(field)
            true
        }
        assertEquals(event.indexableContent(), rejoined.joinToString(event.indexableSeparator()))

        val fields = SearchFieldExtractor.extract(event) as IndexableFields.Tiered
        assertEquals(listOf("Docs Squad", "Editor", "Fact Checker"), fields.primary)
        assertEquals(listOf("Writes the handbook"), fields.secondary)
        assertEquals("Keep it short\nEdit for clarity", fields.text)
    }

    @Test
    fun anInvalidBodyIndexesNothing() {
        val event = TeamCatalogEvent(id, author, 1, arrayOf(arrayOf("d", "docs")), """{"v":2,"name":"x","members":[]}""", sig)
        assertEquals("", event.indexableContent())
    }
}
