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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.PublishBatch
import com.vitorpamplona.amethyst.cli.commands.RawEventSupport
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `amy publish` accepts one event or many, from an argument, a file, or a
 * pipe. These pin the three input shapes it must keep understanding — a
 * regression here silently changes what a batch publish sends.
 */
class PublishInputTest {
    private fun event(id: String) = """{"id":"$id","pubkey":"aa","created_at":1,"kind":1,"tags":[],"content":"c","sig":"bb"}"""

    private fun ids(blobs: Sequence<String>) =
        blobs
            .map {
                Output.mapper
                    .readTree(it)
                    .get("id")
                    .asText()
            }.toList()

    @Test
    fun singleCompactObject() {
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal(event("a1")))
        assertEquals(listOf("a1"), ids(out))
    }

    @Test
    fun prettyPrintedSingleObjectIsNotSplitPerLine() {
        val pretty = Output.mapper.readTree(event("a2")).toPrettyString()
        assertTrue(pretty.lines().size > 1, "fixture must span lines to be meaningful")
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal(pretty))
        assertEquals(listOf("a2"), ids(out))
    }

    @Test
    fun jsonlYieldsOnePerLine() {
        val blob = listOf("b1", "b2", "b3").joinToString("\n") { event(it) }
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal(blob))
        assertEquals(listOf("b1", "b2", "b3"), ids(out))
    }

    @Test
    fun jsonArrayYieldsEachElement() {
        val blob = listOf("c1", "c2").joinToString(",", "[", "]") { event(it) }
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal(blob))
        assertEquals(listOf("c1", "c2"), ids(out))
    }

    @Test
    fun blankLinesAreSkipped() {
        val blob = "\n${event("d1")}\n\n   \n${event("d2")}\n"
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal(blob))
        assertEquals(listOf("d1", "d2"), ids(out))
    }

    @Test
    fun emptyInputYieldsNothing() {
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.Literal("   \n  "))
        assertEquals(emptyList(), out.toList())
    }

    @Test
    fun fileOfJsonlIsStreamed() {
        val f = File.createTempFile("amy-publish", ".jsonl")
        f.deleteOnExit()
        f.writeText(listOf("e1", "e2").joinToString("\n") { event(it) } + "\n")
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.File(f.path))
        assertEquals(listOf("e1", "e2"), ids(out))
    }

    @Test
    fun fileOfJsonArrayIsParsedWhole() {
        val f = File.createTempFile("amy-publish", ".json")
        f.deleteOnExit()
        f.writeText("\n  [\n${event("f1")},\n${event("f2")}\n]\n")
        val out = RawEventSupport.readEvents(RawEventSupport.EventSource.File(f.path))
        assertEquals(listOf("f1", "f2"), ids(out))
    }

    @Test
    fun missingFileIsBadArgs() {
        assertFailsWith<IllegalArgumentException> {
            RawEventSupport.readEvents(RawEventSupport.EventSource.File("/no/such/amy-events.jsonl")).toList()
        }
    }

    @Test
    fun fileFlagWinsOverPositional() {
        val f = File.createTempFile("amy-publish", ".jsonl")
        f.deleteOnExit()
        f.writeText(event("g1"))
        val source = RawEventSupport.eventSource(Args(arrayOf("--file", f.path, event("ignored"))))
        assertEquals(listOf("g1"), ids(RawEventSupport.readEvents(source)))
    }

    @Test
    fun dashPositionalMeansStdin() {
        assertEquals(RawEventSupport.EventSource.Stdin, RawEventSupport.eventSource(Args(arrayOf("-"))))
        assertEquals(RawEventSupport.EventSource.Stdin, RawEventSupport.eventSource(Args(arrayOf())))
    }
}

/**
 * A relay answering `OK false: duplicate: …` already has the event — for a
 * mirror run that is the goal, not a failure. Getting this wrong makes a
 * re-run of the same batch report 100% failure.
 */
class PublishDuplicateTest {
    @Test
    fun nip01DuplicatePrefixIsRecognised() {
        assertTrue(PublishBatch.isDuplicate("duplicate: already have this event"))
        assertTrue(PublishBatch.isDuplicate("duplicate:already have this event"))
        assertTrue(PublishBatch.isDuplicate("  duplicate: have it"))
        assertTrue(PublishBatch.isDuplicate("DUPLICATE: have it"))
    }

    @Test
    fun nip01ReplacedPrefixIsRecognised() {
        assertTrue(PublishBatch.isSuperseded("replaced: a newer version exists"))
        assertTrue(PublishBatch.isSuperseded("REPLACED: newer"))
        assertFalse(PublishBatch.isSuperseded("blocked: nope"))
    }

    @Test
    fun reasonsBucketByNip01Prefix() {
        assertEquals("replaced:", PublishBatch.reasonBucket("replaced: a newer version exists"))
        assertEquals("rate-limited:", PublishBatch.reasonBucket("rate-limited: slow down there"))
        assertEquals("duplicate:", PublishBatch.reasonBucket("  duplicate: have it"))
        // No machine-readable prefix — fall back to the leading words.
        assertEquals("no response within timeout", PublishBatch.reasonBucket("no response within timeout"))
        assertEquals("(no reason given)", PublishBatch.reasonBucket("   "))
    }

    @Test
    fun realRejectionsAreNotDuplicates() {
        assertFalse(PublishBatch.isDuplicate("blocked: pubkey not allowed"))
        assertFalse(PublishBatch.isDuplicate("rate-limited: slow down"))
        assertFalse(PublishBatch.isDuplicate("invalid: bad signature"))
        assertFalse(PublishBatch.isDuplicate(""))
        assertFalse(PublishBatch.isDuplicate("error: duplicate: nested"))
    }
}
