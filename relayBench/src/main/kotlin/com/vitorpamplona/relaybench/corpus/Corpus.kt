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
package com.vitorpamplona.relaybench.corpus

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import java.io.File
import java.security.MessageDigest

/**
 * The benchmark corpus: a flat, chronologically ordered list of *fully
 * signed* Nostr events plus where it came from.
 *
 * Corpora are cached/shared as NDJSON (one event JSON per line) with a
 * `manifest.json` sibling describing how they were built and a fingerprint
 * (sha256 over all event ids) so two parties can confirm they benchmarked
 * against the same data. Synthetic corpora are fully deterministic given
 * (seed, size, baseTime) — same inputs, same event ids on any machine —
 * which is what makes results comparable across relay implementations and
 * across the community.
 */
class Corpus(
    val events: List<Event>,
    val source: String,
    val spec: CorpusSpec?,
) {
    val fingerprint: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        events.forEach { digest.update(it.id.toByteArray()) }
        digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    fun kindHistogram(): Map<Int, Int> = events.groupingBy { it.kind }.eachCount().toSortedMap()
}

/** Deterministic inputs of a synthetic corpus. */
data class CorpusSpec(
    val seed: Long,
    val events: Int,
    /**
     * Newest timestamp in the corpus. Fixed by default (not wall clock) so
     * the same spec yields byte-identical event ids forever. Must stay
     * inside strfry's default accept window (now - 3 years, now + 15 min).
     */
    val baseTime: Long,
    /** Corpus spans this many seconds ending at [baseTime]. */
    val spanSeconds: Long,
) {
    fun cacheName(): String = "corpus-v$VERSION-seed$seed-n$events-t$baseTime.ndjson"

    companion object {
        /** Bump when the generator's output changes for the same spec. */
        const val VERSION = 1

        /** 2026-06-01T00:00:00Z. */
        const val DEFAULT_BASE_TIME = 1_780_272_000L

        const val DEFAULT_SPAN_SECONDS = 30L * 24 * 3600
    }
}

object CorpusIO {
    fun write(
        file: File,
        corpus: Corpus,
    ) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { out ->
            corpus.events.forEach { event ->
                out.write(OptimizedJsonMapper.toJson(event))
                out.write("\n")
            }
        }
        writeManifest(File(file.parentFile, file.nameWithoutExtension + ".manifest.json"), corpus)
    }

    fun read(
        file: File,
        source: String = file.name,
    ): Corpus {
        var skipped = 0
        val events =
            file.useLines { lines ->
                lines
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        runCatching { OptimizedJsonMapper.fromJson(line) }
                            .onFailure { skipped++ }
                            .getOrNull()
                    }.toList()
            }
        if (skipped > 0) println("  ! skipped $skipped unparseable lines in ${file.name}")
        return Corpus(events, source, spec = null)
    }

    private fun writeManifest(
        file: File,
        corpus: Corpus,
    ) {
        val kinds =
            corpus
                .kindHistogram()
                .entries
                .joinToString(",\n") { (k, v) -> "    \"$k\": $v" }
        val specJson =
            corpus.spec?.let {
                """
                |  "generator": "amethyst-relaybench",
                |  "generatorVersion": ${CorpusSpec.VERSION},
                |  "seed": ${it.seed},
                |  "baseTime": ${it.baseTime},
                |  "spanSeconds": ${it.spanSeconds},
                """.trimMargin()
            } ?: "  \"generator\": \"external\","
        file.writeText(
            """
            |{
            |  "source": "${corpus.source}",
            |$specJson
            |  "events": ${corpus.events.size},
            |  "fingerprint": "${corpus.fingerprint}",
            |  "kinds": {
            |$kinds
            |  }
            |}
            |
            """.trimMargin(),
        )
    }
}
