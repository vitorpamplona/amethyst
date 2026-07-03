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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.relaybench.NostrSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.io.File

/**
 * Assembles a fresh real-world corpus by paginating recent events out of
 * public relays. Complements the checked-in dump: use this when the corpus
 * should reflect *today's* event mix. The result goes through the same
 * [CorpusSource.prepare] pipeline (dedup, verify, filter) as every other
 * source, then is cached as NDJSON.
 */
object CorpusDownloader {
    val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

    private val KIND_BUCKETS = listOf(listOf(0, 3), listOf(1), listOf(6, 7), listOf(9735), listOf(30023, 1111))
    private val mapper = jacksonObjectMapper()

    fun download(
        relayUrls: List<String>,
        target: Int,
        cacheDir: File,
        http: OkHttpClient,
        log: (String) -> Unit,
    ): Corpus {
        val key = "corpus-download-${relayUrls.hashCode()}-n$target.ndjson"
        val cached = File(cacheDir, key)
        if (cached.exists()) {
            log("  reusing downloaded corpus ${cached.name}")
            return CorpusIO.read(cached, source = "download:${relayUrls.joinToString(",")}")
        }

        val perRelay = (target * 2 / relayUrls.size).coerceAtLeast(500)
        val collected = LinkedHashMap<String, Event>(target * 2)
        runBlocking {
            relayUrls
                .map { url ->
                    async {
                        runCatching { downloadFrom(url, perRelay, log) }
                            .onFailure { log("  ! $url failed: ${it.message}") }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
        }.flatten().forEach { collected.putIfAbsent(it.id, it) }

        log("  downloaded ${collected.size} unique events from ${relayUrls.size} relays")
        val corpus = CorpusSource.prepare(collected.values.toList(), target, "download:${relayUrls.joinToString(",")}", log)
        CorpusIO.write(cached, corpus)
        return corpus
    }

    private suspend fun downloadFrom(
        url: String,
        target: Int,
        log: (String) -> Unit,
    ): List<Event> {
        val http = OkHttpClient.Builder().build()
        val socket = NostrSocket.connect(http, url)
        val events = ArrayList<Event>(target)
        try {
            for (kinds in KIND_BUCKETS) {
                var until: Long? = null
                var pages = 0
                val quota = target / KIND_BUCKETS.size
                var got = 0
                while (got < quota && pages < 40) {
                    val filter = Filter(kinds = kinds, limit = 500, until = until)
                    val page = requestPage(socket, "dl-${kinds.first()}-$pages", filter) ?: break
                    if (page.isEmpty()) break
                    events += page
                    got += page.size
                    until = page.minOf { it.createdAt } - 1
                    pages++
                }
            }
        } finally {
            socket.disconnect()
            http.dispatcher.executorService.shutdown()
        }
        log("  $url: ${events.size} events")
        return events
    }

    /** One REQ page; null on timeout or socket close. */
    private suspend fun requestPage(
        socket: NostrSocket,
        subId: String,
        filter: Filter,
    ): List<Event>? =
        withTimeoutOrNull(30_000) {
            socket.req(subId, filter.toJson())
            val page = ArrayList<Event>(filter.limit ?: 500)
            for (raw in socket.incoming) {
                val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                when (node[0]?.asText()) {
                    "EVENT" ->
                        if (node[1]?.asText() == subId) {
                            runCatching { OptimizedJsonMapper.fromJson(node[2].toString()) }
                                .getOrNull()
                                ?.let { page.add(it) }
                        }
                    "EOSE", "CLOSED" ->
                        if (node[1]?.asText() == subId) {
                            socket.close(subId)
                            return@withTimeoutOrNull page
                        }
                }
            }
            page
        }
}
