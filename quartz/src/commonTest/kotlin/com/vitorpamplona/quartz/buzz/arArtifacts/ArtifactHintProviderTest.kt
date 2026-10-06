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
package com.vitorpamplona.quartz.buzz.arArtifacts

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val anchor = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val head = "a7a12e7bbd4b4c2ae6cd5ae1c8a5ab8a4e7f0f53bd21cd6f4b51f2cc1f6fe0e2"
    private val artifactId = "0b6c3f1e-2d4a-4b5c-8d6e-7f8091a2b3c4"
    private val channel = "6f1a8a3c-9f1e-4d2b-8c1a-2b3c4d5e6f70"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun artifact(
        content: String,
        prev: Boolean,
    ): ArtifactEvent {
        val tags =
            buildList {
                add(arrayOf("ar", "1"))
                add(arrayOf("d", artifactId))
                add(arrayOf("h", channel))
                add(arrayOf("type", "buzz.task"))
                add(arrayOf("title", "Ship the beta"))
                add(arrayOf("op", if (prev) "update" else "create"))
                add(arrayOf("root", anchor))
                if (prev) add(arrayOf("prev", head))
            }.toTypedArray()
        return ArtifactEvent(id, author, 1, tags, content, sig)
    }

    @Test
    fun rootAndPrevAreLinkedWithoutHints() {
        val event = artifact("notes", prev = true)
        assertEquals(listOf(anchor, head), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())

        val removal = ArtifactRemovalEvent(id, author, 1, arrayOf(arrayOf("d", artifactId), arrayOf("prev", head)), "", sig)
        assertEquals(listOf(head), removal.linkedEventIds())
    }

    @Test
    fun titleAndTextBodyAreSearchableButJsonIsNot() {
        val text = artifact("Finish QA and tag the build", prev = false)
        assertEquals("Ship the beta\nFinish QA and tag the build", text.indexableContent())
        val fields = SearchFieldExtractor.extract(text) as IndexableFields.Tiered
        assertEquals(listOf("Ship the beta"), fields.primary)
        assertEquals("Finish QA and tag the build", fields.text)

        val json = artifact("""{"status":"open","assignee":"x"}""", prev = false)
        assertEquals("Ship the beta", json.indexableContent())
    }

    @Test
    fun proseThatOpensWithABracketIsStillText() {
        for (body in listOf("[Draft] Q3 roadmap", "[x] ship it", "  {wip} notes", "[1] see the spec [2]", "[todo] fix [this]", "{a} and {b}")) {
            assertEquals(body, artifact(body, prev = false).textBody(), body)
            assertEquals("Ship the beta\n$body", artifact(body, prev = false).indexableContent())
        }
    }

    @Test
    fun jsonObjectsAndArraysAreNotText() {
        for (body in listOf("""  {"a":1}  """, "{}", "[]", "[1, 2]", """["x", {"y": "]"}]""", "[true]", "\n[\n  null\n]\n", """{"s":"a \" } b"}""")) {
            assertNull(artifact(body, prev = false).textBody(), body)
            assertTrue(ArtifactEvent.isJsonDocument(body), body)
        }
        assertFalse(ArtifactEvent.isJsonDocument("{"))
        assertFalse(ArtifactEvent.isJsonDocument("[a]"))
        assertFalse(ArtifactEvent.isJsonDocument("""{"open": 1"""))
        assertFalse(ArtifactEvent.isJsonDocument("""["unterminated]"""))
    }
}
