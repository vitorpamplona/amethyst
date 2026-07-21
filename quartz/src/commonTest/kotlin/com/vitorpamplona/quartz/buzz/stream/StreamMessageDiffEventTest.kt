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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.buzz.stream.tags.BranchTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamMessageDiffEventTest {
    private val channelId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    private val meta =
        DiffMeta(
            repoUrl = "https://github.com/vitorpamplona/amethyst",
            commitSha = "abc1234",
            filePath = "quartz/Foo.kt",
            parentCommit = "def5678",
            branch = BranchTag("feature/x", "main"),
            prNumber = 42L,
            language = "kotlin",
            description = "Adds Foo",
            truncated = true,
            altText = "a diff",
        )

    @Test
    fun buildEmitsAllDiffMetaTags() {
        val template =
            StreamMessageDiffEvent.build(
                channelId = channelId,
                diff = "--- a\n+++ b\n",
                meta = meta,
                createdAt = 1_700_000_000L,
            )

        assertEquals(StreamMessageDiffEvent.KIND, template.kind)
        assertEquals(40008, template.kind)
        assertEquals("--- a\n+++ b\n", template.content)

        val byName = template.tags.groupBy { it[0] }
        assertEquals(channelId, byName["h"]!!.single()[1])
        assertEquals("https://github.com/vitorpamplona/amethyst", byName["repo"]!!.single()[1])
        assertEquals("abc1234", byName["commit"]!!.single()[1])
        assertEquals("quartz/Foo.kt", byName["file"]!!.single()[1])
        assertEquals("def5678", byName["parent-commit"]!!.single()[1])
        assertEquals(listOf("branch", "feature/x", "main"), byName["branch"]!!.single().toList())
        assertEquals("42", byName["pr"]!!.single()[1])
        assertEquals("kotlin", byName["l"]!!.single()[1])
        assertEquals("Adds Foo", byName["description"]!!.single()[1])
        assertEquals("true", byName["truncated"]!!.single()[1])
        assertEquals("a diff", byName["alt"]!!.single()[1])
    }

    @Test
    fun accessorRoundTripsDiffMeta() {
        val template = StreamMessageDiffEvent.build(channelId, "diff", meta, createdAt = 1L)
        val event =
            StreamMessageDiffEvent(
                id = "00",
                pubKey = "00",
                createdAt = 1L,
                tags = template.tags,
                content = "diff",
                sig = "00",
            )

        assertEquals(channelId, event.channel())
        val parsed = event.diffMeta()
        assertNotNull(parsed)
        assertEquals(meta.repoUrl, parsed.repoUrl)
        assertEquals(meta.commitSha, parsed.commitSha)
        assertEquals(meta.filePath, parsed.filePath)
        assertEquals(meta.parentCommit, parsed.parentCommit)
        assertEquals("feature/x", parsed.branch?.source)
        assertEquals("main", parsed.branch?.target)
        assertEquals(42L, parsed.prNumber)
        assertEquals("kotlin", parsed.language)
        assertEquals("Adds Foo", parsed.description)
        assertTrue(parsed.truncated)
        assertEquals("a diff", parsed.altText)
    }
}
