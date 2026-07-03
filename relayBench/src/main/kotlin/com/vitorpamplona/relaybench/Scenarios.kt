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
package com.vitorpamplona.relaybench

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.relaybench.corpus.Corpus

/**
 * One REQ workload, named after the client behavior it models. The filter
 * is derived from the corpus itself (top authors, hottest thread, most
 * mentioned pubkey, …) so the same scenario keys stay meaningful whether
 * the corpus is synthetic, the checked-in real dump, or freshly downloaded.
 *
 * Everything stays inside strfry's *default* limits (≤200 filter elements,
 * limit ≤500, ≤3 tag kinds per filter) — the point is comparing relays on
 * out-of-the-box configs, not tuning around them.
 */
data class Scenario(
    val key: String,
    val description: String,
    val filter: Filter,
) {
    val filterJson: String get() = filter.toJson()
}

object Scenarios {
    fun derive(corpus: Corpus): List<Scenario> {
        val events = corpus.events

        // Frequency maps that mirror what relay indexes serve most.
        val notesByAuthor = HashMap<String, Int>()
        val eTagRefs = HashMap<String, Int>()
        val pTagRefs = HashMap<String, Int>()
        val tTagRefs = HashMap<String, Int>()
        val noteIds = ArrayList<String>()

        for (e in events) {
            if (e.kind == 1) {
                notesByAuthor.merge(e.pubKey, 1, Int::plus)
                noteIds.add(e.id)
            }
            if (e.kind == 1 || e.kind == 6 || e.kind == 7 || e.kind == 9735) {
                for (tag in e.tags) {
                    if (tag.size < 2) continue
                    when (tag[0]) {
                        "e" -> if (tag[1].length == 64) eTagRefs.merge(tag[1], 1, Int::plus)
                        "p" -> if (tag[1].length == 64) pTagRefs.merge(tag[1], 1, Int::plus)
                        "t" -> if (e.kind == 1) tTagRefs.merge(tag[1].lowercase(), 1, Int::plus)
                    }
                }
            }
        }

        val topAuthors = notesByAuthor.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
        val hottestThread =
            eTagRefs.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key
        val mostMentioned =
            pTagRefs.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key
        val topHashtag =
            tTagRefs.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()
                ?.key

        // Evenly spread sample of note ids — a "fetch these 100 events" batch.
        val idSample =
            if (noteIds.size <= 100) {
                noteIds.toList()
            } else {
                (0 until 100).map { noteIds[it * noteIds.size / 100] }
            }

        val maxTime = events.maxOf { it.createdAt }
        val minTime = events.minOf { it.createdAt }
        val recentSince = maxTime - ((maxTime - minTime) / 5).coerceAtLeast(1)

        return buildList {
            add(
                Scenario(
                    "firehose",
                    "latest 500 events of any kind (relay landing query)",
                    Filter(limit = 500),
                ),
            )
            add(
                Scenario(
                    "global-feed",
                    "latest 500 text notes (global tab)",
                    Filter(kinds = listOf(1), limit = 500),
                ),
            )
            if (topAuthors.isNotEmpty()) {
                add(
                    Scenario(
                        "profiles",
                        "metadata for ${topAuthors.take(50).size} pubkeys (profile hydration)",
                        Filter(kinds = listOf(0), authors = topAuthors.take(50)),
                    ),
                )
                add(
                    Scenario(
                        "follow-feed",
                        "notes+reposts from ${topAuthors.take(150).size} follows (home feed)",
                        Filter(kinds = listOf(1, 6), authors = topAuthors.take(150), limit = 500),
                    ),
                )
            }
            hottestThread?.let {
                add(
                    Scenario(
                        "thread",
                        "replies/reposts/reactions/zaps on the hottest note (thread view)",
                        Filter(kinds = listOf(1, 6, 7, 9735), tags = mapOf("e" to listOf(it))),
                    ),
                )
            }
            mostMentioned?.let {
                add(
                    Scenario(
                        "notifications",
                        "events tagging the most-mentioned pubkey (notifications tab)",
                        Filter(kinds = listOf(1, 6, 7, 9735), tags = mapOf("p" to listOf(it)), limit = 500),
                    ),
                )
            }
            topHashtag?.let {
                add(
                    Scenario(
                        "hashtag",
                        "latest notes under #$it (hashtag feed)",
                        Filter(kinds = listOf(1), tags = mapOf("t" to listOf(it)), limit = 500),
                    ),
                )
            }
            if (idSample.isNotEmpty()) {
                add(
                    Scenario(
                        "by-ids",
                        "batch fetch of ${idSample.size} known event ids",
                        Filter(ids = idSample),
                    ),
                )
            }
            add(
                Scenario(
                    "recent-window",
                    "notes since the corpus' last 20% time window",
                    Filter(kinds = listOf(1), since = recentSince, limit = 500),
                ),
            )
        }
    }
}
