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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.relaybench.NostrSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.util.concurrent.TimeUnit

/**
 * Assembles a fresh real-world corpus by paginating the *latest* events out
 * of public relays — newest first, no kind filter, `until`-paged all the way
 * down until the target is met. Complements the checked-in dump: use this
 * when the corpus should reflect today's live event mix.
 *
 * Built to survive million-event pulls over flaky links:
 *  - every unique event is appended to an on-disk NDJSON spill as it
 *    arrives, so nothing is held in memory during the download;
 *  - pagination progress (`until` cursor per relay) is checkpointed next to
 *    the spill, so a crashed or interrupted run resumes where it left off;
 *  - the socket is reconnected with backoff on timeouts/drops.
 *
 * The finished spill goes through the same [CorpusSource.prepare] pipeline
 * (dedup, verify, filter, sort) as every other source, then is cached as
 * NDJSON + manifest.
 */
object CorpusDownloader {
    val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

    /** Stay inside the strictest common relay page cap (damus: max_limit 500). */
    private const val PAGE_LIMIT = 500
    private const val PAGE_TIMEOUT_MS = 45_000L
    private const val MAX_CONSECUTIVE_FAILURES = 10

    private val mapper = jacksonObjectMapper()

    fun download(
        relayUrls: List<String>,
        target: Int,
        cacheDir: File,
        http: OkHttpClient,
        log: (String) -> Unit,
        maxEventBytes: Int = CorpusSource.DEFAULT_MAX_EVENT_BYTES,
    ): Corpus {
        val tag = relayUrls.joinToString("+") { hostOf(it) }
        val key = "corpus-download-$tag-n$target"
        val cached = File(cacheDir, "$key.ndjson")
        if (cached.exists()) {
            log("  reusing downloaded corpus ${cached.name}")
            return CorpusIO.read(cached, source = "download:${relayUrls.joinToString(",")}")
        }

        cacheDir.mkdirs()
        val spill = File(cacheDir, "$key.raw.ndjson")
        val checkpoint = File(cacheDir, "$key.progress.json")

        // Deterministically droppable events (kind-5 deletions — ~40% of a
        // live firehose! — ephemerals, oversize) are filtered at page time
        // and never count toward the goal, so only a small headroom is left
        // for what preparation alone can catch (invalid sigs, canonical-size
        // edge cases, cross-relay duplicates).
        val needed = if (relayUrls.size == 1) target + target / 25 else target * 2

        val ids = HashSet<String>(needed * 2)
        val cursors = HashMap<String, Long>()
        if (spill.exists()) {
            spill.useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    runCatching { mapper.readTree(line) }
                        .getOrNull()
                        ?.get("id")
                        ?.asText()
                        ?.let { ids.add(it) }
                }
            }
            if (checkpoint.exists()) {
                runCatching { mapper.readTree(checkpoint.readText()) }.getOrNull()?.properties()?.forEach { (url, until) ->
                    cursors[url] = until.asLong()
                }
            }
            log("  resuming download: ${ids.size} events already spilled to ${spill.name}")
        }

        val ws =
            http
                .newBuilder()
                .pingInterval(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()

        spill.appendText("") // ensure the file exists even if the relay yields nothing
        FileWriter(spill, true).buffered(1 shl 16).use { out ->
            runBlocking {
                // Even split across relays (the last one may top up the rest)
                // so a multi-relay corpus actually mixes sources.
                val perRelay = (needed + relayUrls.size - 1) / relayUrls.size
                relayUrls.forEachIndexed { i, url ->
                    val goal = if (i == relayUrls.lastIndex) needed else minOf(needed, ids.size + perRelay)
                    if (ids.size < goal) {
                        downloadFrom(url, goal, ids, out, cursors, checkpoint, ws, maxEventBytes, log)
                    }
                }
            }
        }

        log("  downloaded ${ids.size} unique events from ${relayUrls.size} relay(s)")
        if (ids.size < needed) {
            log("  ! relays exhausted below the $needed-event goal — preparing what we have")
        }

        log("  preparing (dedup, filter, verify signatures)…")
        val raw = CorpusIO.read(spill).events
        val corpus = CorpusSource.prepare(raw, target, "download:${relayUrls.joinToString(",")}", log)
        CorpusIO.write(cached, corpus)
        if (!checkpoint.delete() && checkpoint.exists()) {
            log("  ! could not delete stale checkpoint ${checkpoint.name}")
        }
        log("  cached prepared corpus to ${cached.path}")
        return corpus
    }

    private fun hostOf(url: String) =
        url
            .removePrefix("wss://")
            .removePrefix("ws://")
            .trimEnd('/')
            .replace(Regex("[^A-Za-z0-9.-]"), "_")

    /**
     * Pages `{"limit":500,"until":T}` down the relay's timeline, appending
     * unique events to [out]. The `until` cursor stays *inclusive* of the
     * oldest seen second (id-dedup absorbs the overlap) so events sharing a
     * created_at are not skipped; only a full page of nothing-new at the same
     * cursor — >500 events in one second — forces the cursor past it.
     */
    private suspend fun downloadFrom(
        url: String,
        goal: Int,
        ids: MutableSet<String>,
        out: Writer,
        cursors: MutableMap<String, Long>,
        checkpoint: File,
        http: OkHttpClient,
        maxEventBytes: Int,
        log: (String) -> Unit,
    ) {
        var until: Long? = cursors[url]
        var socket: NostrSocket? = null
        var failures = 0
        var pages = 0
        var added = 0
        val startedAt = System.nanoTime()

        fun saveCheckpoint() {
            until?.let { cursors[url] = it }
            runCatching { checkpoint.writeText(mapper.writeValueAsString(cursors)) }
        }

        try {
            while (ids.size < goal) {
                val sock =
                    socket ?: runCatching { NostrSocket.connect(http, url) }.getOrElse {
                        failures++
                        if (failures > MAX_CONSECUTIVE_FAILURES) {
                            log("  ! $url: giving up after $failures consecutive failures (${it.message})")
                            return
                        }
                        delay(minOf(1000L shl (failures - 1), 30_000L))
                        null
                    } ?: continue
                socket = sock

                val filter = Filter(limit = PAGE_LIMIT, until = until)
                val page = requestPage(sock, "dl-$pages", filter)
                if (page == null) {
                    // Timeout or dead socket: reconnect and retry the same cursor.
                    runCatching { sock.disconnect() }
                    socket = null
                    failures++
                    if (failures > MAX_CONSECUTIVE_FAILURES) {
                        log("  ! $url: giving up after $failures consecutive page failures")
                        return
                    }
                    delay(minOf(1000L shl (failures - 1), 30_000L))
                    continue
                }
                failures = 0
                pages++

                if (page.isEmpty()) break // end of the relay's timeline

                var fresh = 0
                for (event in page) {
                    // Skip what preparation would deterministically drop, so
                    // they never count toward the goal: deletions, ephemerals
                    // (never queryable), events over the size cap.
                    if (event.kind == 5 || event.kind in 20000..29999) continue
                    if (event.json.length > maxEventBytes) continue
                    if (ids.add(event.id)) {
                        out.write(event.json)
                        out.write("\n")
                        fresh++
                    }
                }
                added += fresh
                val oldest = page.minOf { it.createdAt }
                until =
                    if (fresh == 0 && until != null && oldest >= until) {
                        // >PAGE_LIMIT events in this second and we have them all.
                        until - 1
                    } else {
                        oldest
                    }

                out.flush()
                saveCheckpoint()

                if (page.size < PAGE_LIMIT && fresh == 0) break // exhausted

                if (pages % 50 == 0) {
                    val elapsed = (System.nanoTime() - startedAt) / 1e9
                    val rate = added / elapsed
                    val remaining = (goal - ids.size).coerceAtLeast(0)
                    val eta = if (rate > 0) (remaining / rate).toLong() else -1
                    log(
                        "  $url: ${ids.size} events (${"%.0f".format(rate)}/s, page $pages, " +
                            "cursor $until, eta ${if (eta >= 0) "${eta / 60}m${eta % 60}s" else "?"})",
                    )
                }
            }
        } finally {
            runCatching { socket?.disconnect() }
        }
        log("  $url: $added events in $pages pages")
    }

    private class PagedEvent(
        val id: String,
        val kind: Int,
        val createdAt: Long,
        val json: String,
    )

    /** One REQ page; null on timeout or socket close (caller reconnects). */
    private suspend fun requestPage(
        socket: NostrSocket,
        subId: String,
        filter: Filter,
    ): List<PagedEvent>? =
        withTimeoutOrNull(PAGE_TIMEOUT_MS) {
            if (!socket.req(subId, filter.toJson())) return@withTimeoutOrNull null
            val page = ArrayList<PagedEvent>(filter.limit ?: PAGE_LIMIT)
            for (raw in socket.incoming) {
                val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                when (node[0]?.asText()) {
                    "EVENT" ->
                        if (node[1]?.asText() == subId) {
                            val event = node[2] ?: continue
                            val id = event["id"]?.asText() ?: continue
                            val kind = event["kind"]?.asInt() ?: continue
                            val createdAt = event["created_at"]?.asLong() ?: continue
                            page.add(PagedEvent(id, kind, createdAt, event.toString()))
                        }
                    "EOSE" ->
                        if (node[1]?.asText() == subId) {
                            // End of stored events: this page is the complete
                            // window for the cursor.
                            socket.close(subId)
                            return@withTimeoutOrNull page
                        }
                    "CLOSED" ->
                        if (node[1]?.asText() == subId) {
                            // The relay terminated the sub itself (rate-limit,
                            // policy, error) — possibly *before* sending every
                            // matching event. Treat it as a soft failure, NOT an
                            // EOSE: returning the partial page as complete would
                            // advance the cursor past the unsent tail and silently
                            // drop those events from the corpus. null makes the
                            // caller reconnect and retry the SAME cursor; the id
                            // dedup set absorbs the events this attempt did see.
                            return@withTimeoutOrNull null
                        }
                }
            }
            null // channel closed without EOSE — socket died mid-page
        }
}
