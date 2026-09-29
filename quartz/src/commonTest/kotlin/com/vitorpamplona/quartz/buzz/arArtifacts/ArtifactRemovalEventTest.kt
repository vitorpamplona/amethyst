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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactRemovalEventTest {
    private val artifactId = "04737c81-e5e8-4412-bb47-f446813cfeba"
    private val source = "9b353519-f4fe-4757-aef4-bec6cc0ae54c"
    private val replaced = "a".repeat(64)

    @Test
    fun relayShapedRemovalParses() {
        // Exactly the tag list `removal_marker` in buzz-db/src/store/artifact.rs emits.
        val tags =
            arrayOf(
                arrayOf("ar", "1"),
                arrayOf("d", artifactId),
                arrayOf("h", source),
                arrayOf("reason", "moved"),
                arrayOf("prev", replaced),
            )
        val ev = ArtifactRemovalEvent("0".repeat(64), "e".repeat(64), 1, tags, "", "sig")

        assertEquals(45011, ev.kind)
        assertEquals(artifactId, ev.artifactId())
        assertEquals(source, ev.sourceChannel())
        assertEquals("moved", ev.reason())
        assertEquals(replaced, ev.replacedRevision())
        assertTrue(ev.isWellFormed())
    }

    @Test
    fun buildMatchesTheRelayTagOrder() {
        val tpl = ArtifactRemovalEvent.build(artifactId, source, replaced)
        assertEquals(listOf("ar", "d", "h", "reason", "prev"), tpl.tags.map { it[0] })
        assertEquals("", tpl.content)
    }

    @Test
    fun malformedRemovalsAreFlagged() {
        val tpl = ArtifactRemovalEvent.build(artifactId, source, replaced)
        assertFalse(ArtifactRemovalEvent("0".repeat(64), "e".repeat(64), 1, tpl.tags, "content", "sig").isWellFormed())
        val badPrev = tpl.tags.map { if (it[0] == "prev") arrayOf("prev", "A".repeat(64)) else it }.toTypedArray()
        assertFalse(ArtifactRemovalEvent("0".repeat(64), "e".repeat(64), 1, badPrev, "", "sig").isWellFormed())
    }
}
