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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.utils.EventFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What every searchable kind puts into a store's index, pinned character for character.
 *
 * `indexableContent()` is not an internal detail: the SQLite and filesystem stores index through
 * it, and `references/searchable-kinds.md` — which external engines mirror at version bumps — is a
 * transcription of it. A body that changes silently ships stale results downstream and needs a
 * `reindexFullTextSearch()` on every existing database. So the output of all 126 kinds is recorded
 * in `indexable-content.golden` and compared here.
 *
 * A deliberate change to what a kind indexes is fine — regenerate the golden file with
 * `-Dgolden=write`, and update the kind table in the same commit. An *accidental* change, which is
 * what this exists to catch, then shows up as a diff nobody asked for.
 */
class IndexableContentGoldenTest {
    private val golden = File("src/jvmTest/resources/indexable-content.golden")

    /**
     * One event per kind, carrying every tag name the accessors read plus a content body, so the
     * recorded string exercises each field a kind joins rather than just its content.
     */
    private fun sample(kind: Int) =
        EventFactory.create<com.vitorpamplona.quartz.nip01Core.core.Event>(
            "9".repeat(64),
            "a".repeat(64),
            1700000000L,
            kind,
            arrayOf(
                arrayOf("d", "the-d-tag"),
                arrayOf("title", "The Title"),
                arrayOf("subject", "The Subject"),
                arrayOf("summary", "The Summary"),
                arrayOf("description", "The Description"),
                arrayOf("name", "The Name"),
                arrayOf("about", "The About"),
                arrayOf("comment", "The Comment"),
                arrayOf("context", "The Context"),
                arrayOf("rules", "The Rules"),
                arrayOf("text", "The Text"),
                arrayOf("instructions", "The Instructions"),
                arrayOf("alt", "The Alt"),
                arrayOf("t", "hashtag1"),
                arrayOf("t", "hashtag2"),
                arrayOf("image", "https://example.com/i.png"),
                arrayOf("published_at", "1700000000"),
            ),
            "The content body.",
            "",
        )

    private fun record(): String =
        KINDS.joinToString("\n") { kind ->
            val event = sample(kind)
            val indexed =
                if (event is SearchableEvent) {
                    // Escaped, so a multi-line body stays one golden line and a change to the
                    // separator between fields is visible rather than invisible whitespace.
                    event
                        .indexableContent()
                        .replace("\\", "\\\\")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t")
                } else {
                    "<not searchable>"
                }
            "$kind\t$indexed"
        }

    @Test
    fun everySearchableKindIndexesExactlyWhatItIndexedBefore() {
        val actual = record()
        // Self-bootstrapping: an absent golden is recorded rather than failed, so regenerating
        // after a deliberate change is `rm` plus a test run and needs no build-script flag.
        if (!golden.isFile) {
            golden.parentFile.mkdirs()
            golden.writeText(actual + "\n")
            println("recorded ${KINDS.size} kinds into ${golden.path}")
            return
        }
        assertEquals(
            "a kind's indexed text changed. If deliberate: rerun with -Dgolden=write, update " +
                "references/searchable-kinds.md in the same commit, and schedule a reindex " +
                "(IEventStore.reindexFullTextSearch) — existing databases keep their old text.",
            // Normalize CRLF: Windows checkouts (autocrlf) would otherwise fail this comparison
            // on invisible line endings alone.
            golden.readText().replace("\r\n", "\n").trim(),
            actual.trim(),
        )
    }

    @Test
    fun everyKindsVisitorAgreesWithItsIndexedContent() {
        // The read path and the write path must never disagree about what an event says. They
        // cannot share code — one refuses to build a string at all — so the agreement is asserted
        // here, over every searchable kind rather than the handful that happen to override.
        val disagreements =
            KINDS.mapNotNull { kind ->
                val event = sample(kind)
                if (event !is SearchableEvent) return@mapNotNull null
                val visited =
                    buildList {
                        event.forEachIndexableField { f ->
                            if (f != null) add(f)
                            true
                        }
                    }
                val rejoined = visited.joinToString(event.indexableSeparator())
                if (rejoined == event.indexableContent()) null else "kind $kind: visitor=${rejoined.take(120)} content=${event.indexableContent().take(120)}"
            }
        assertEquals("visitor and indexed content disagree", emptyList<String>(), disagreements)
    }

    @Test
    fun everyKindStopsWalkingWhenToldTo() {
        // The point of the read path is that a hit on the first field does not build the rest. A
        // class that ignores the visitor's `false` still agrees with its indexed content, so
        // nothing above would catch it — only this does.
        val kept =
            KINDS.mapNotNull { kind ->
                val event = sample(kind)
                if (event !is SearchableEvent) return@mapNotNull null
                var seen = 0
                event.forEachIndexableField {
                    seen++
                    false
                }
                if (seen <= 1) null else "kind $kind kept walking for $seen fields after being stopped"
            }
        assertEquals("a visitor ignored its stop signal", emptyList<String>(), kept)
    }

    companion object {
        /**
         * Every searchable kind, from the machine-checked list rather than a second hand-kept one.
         *
         * This was a list of its own until it drifted: seventeen searchable kinds — every video
         * kind among them — were absent and so were never pinned by any of the tests below, and
         * one entry (31890) was a kind the factory does not build at all.
         *
         * It holds only *searchable* kinds now, so a kind pinned here as `<not searchable>` — 11998,
         * the heartbeat, was one — drops out of the golden. Nothing is lost by that: pinning the
         * row caught a kind that started being indexed, and `SearchableKindsTest` catches the same
         * thing across the whole 16-bit space rather than for the kinds someone remembered to list.
         */
        val KINDS = SearchableKinds.ALL
    }
}
