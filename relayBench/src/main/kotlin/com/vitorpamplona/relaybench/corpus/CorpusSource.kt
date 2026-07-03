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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

/**
 * Turns any raw event source (deterministic generator, NDJSON file, a JSON
 * array dump like quartz's `nostr_vitor_startup_data.json.gz`, or a live
 * download) into a *benchmark-ready* corpus, and caches the result so
 * subsequent runs skip the expensive preparation.
 *
 * Preparation makes the corpus fair and hang-proof for every relay:
 *  - dedup by id (multi-relay dumps repeat events heavily),
 *  - drop unsigned events (NIP-17 kind-14 rumors are unsigned by design),
 *  - drop kind-5 deletions (order-dependent side effects would make query
 *    counts diverge between relays for reasons unrelated to performance),
 *  - drop ephemeral kinds (accepted but never queryable — would deadlock
 *    the visibility probe),
 *  - drop events beyond strfry's default hard limits (64 KiB serialized,
 *    2000 tags, 1 KiB tag values) so both relays are offered an identical,
 *    fully ingestable stream,
 *  - verify every Schnorr signature in parallel and drop invalid ones (a
 *    verifying relay would reject them),
 *  - sort chronologically (stable) so replaceable-event supersession is
 *    deterministic.
 */
object CorpusSource {
    const val DEFAULT_MAX_EVENT_BYTES = 65536
    const val DEFAULT_MAX_TAGS = 2000
    private const val MAX_TAG_VALUE_BYTES = 1024

    fun synthetic(
        spec: CorpusSpec,
        cacheDir: File,
        log: (String) -> Unit,
    ): Corpus {
        val cached = File(cacheDir, spec.cacheName())
        if (cached.exists()) {
            log("  reusing cached corpus ${cached.name}")
            return Corpus(CorpusIO.read(cached).events, "synthetic seed=${spec.seed} n=${spec.events}", spec)
        }
        log("  generating deterministic synthetic corpus (seed=${spec.seed}, n=${spec.events})…")
        val corpus = CorpusGenerator.generate(spec, log)
        CorpusIO.write(cached, corpus)
        log("  cached to ${cached.path}")
        return corpus
    }

    fun fromFile(
        file: File,
        limit: Int,
        cacheDir: File,
        log: (String) -> Unit,
        maxEventBytes: Int = DEFAULT_MAX_EVENT_BYTES,
        maxTags: Int = DEFAULT_MAX_TAGS,
    ): Corpus {
        val key = "corpus-file-${file.name}-${file.length()}-limit$limit-b$maxEventBytes-t$maxTags.ndjson"
        val cached = File(cacheDir, key)
        if (cached.exists()) {
            log("  reusing prepared corpus ${cached.name}")
            return CorpusIO.read(cached, source = "file:${file.name}")
        }
        log("  reading ${file.name}…")
        // For multi-million-event dumps a --limit also caps how much we
        // read (with headroom for events preparation will drop), keeping
        // memory bounded instead of materializing gigabytes.
        val readCap = if (limit > 0) (limit * 3 / 2) + 1000 else Int.MAX_VALUE
        val raw = readAnyFormat(file, readCap)
        log("  ${raw.size} raw events read; preparing (dedup, filter, verify signatures)…")
        val corpus = prepare(raw, limit, "file:${file.name}", log, maxEventBytes, maxTags)
        CorpusIO.write(cached, corpus)
        log("  cached prepared corpus to ${cached.path}")
        return corpus
    }

    /**
     * Reads NDJSON or a single JSON array, gzipped or plain (sniffed by
     * magic bytes, not filename — Drive exports lie about extensions),
     * streaming up to [readCap] events.
     */
    private fun readAnyFormat(
        file: File,
        readCap: Int,
    ): List<Event> {
        val base = PushbackInputStream(file.inputStream().buffered(1 shl 16), 2)
        val magic = ByteArray(2)
        val read = base.read(magic)
        if (read > 0) base.unread(magic, 0, read)
        val isGzip = read == 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()
        val stream = if (isGzip) GZIPInputStream(base, 1 shl 16) else base
        stream.use { input ->
            val pushback = PushbackInputStream(input, 1)
            var first: Int
            do {
                first = pushback.read()
            } while (first != -1 && Character.isWhitespace(first))
            if (first == -1) return emptyList()
            pushback.unread(first)
            return if (first.toChar() == '[') {
                // One huge JSON array — stream element by element.
                val mapper = ObjectMapper()
                val parser = JsonFactory().createParser(pushback)
                parser.codec = mapper
                check(parser.nextToken() == JsonToken.START_ARRAY)
                val events = ArrayList<Event>(1 shl 18)
                while (events.size < readCap && parser.nextToken() == JsonToken.START_OBJECT) {
                    val node = parser.readValueAsTree<JsonNode>()
                    toEvent(node)?.let { events.add(it) }
                }
                events
            } else {
                // NDJSON.
                pushback.bufferedReader().useLines { lines ->
                    lines
                        .filter { it.isNotBlank() }
                        .mapNotNull { runCatching { OptimizedJsonMapper.fromJson(it) }.getOrNull() }
                        .take(readCap)
                        .toList()
                }
            }
        }
    }

    private fun toEvent(node: JsonNode): Event? =
        runCatching {
            Event(
                id = node["id"].asText(),
                pubKey = node["pubkey"].asText(),
                createdAt = node["created_at"].asLong(),
                kind = node["kind"].asInt(),
                tags = node["tags"].map { tag -> tag.map { it.asText() }.toTypedArray() }.toTypedArray(),
                content = node["content"].asText(),
                sig = node["sig"]?.asText() ?: "",
            )
        }.getOrNull()

    fun prepare(
        raw: List<Event>,
        limit: Int,
        source: String,
        log: (String) -> Unit,
        maxEventBytes: Int = DEFAULT_MAX_EVENT_BYTES,
        maxTags: Int = DEFAULT_MAX_TAGS,
    ): Corpus {
        val drops = LinkedHashMap<String, Int>()

        fun drop(reason: String) = drops.merge(reason, 1, Int::plus)

        val seen = HashSet<String>(raw.size * 2)
        val unique =
            raw.filter { e ->
                when {
                    !seen.add(e.id) -> {
                        drop("duplicate id")
                        false
                    }
                    e.sig.length != 128 -> {
                        drop("missing/short sig (e.g. NIP-17 rumors)")
                        false
                    }
                    e.kind == 5 -> {
                        drop("kind-5 deletion (order-dependent)")
                        false
                    }
                    e.kind in 20000..29999 -> {
                        drop("ephemeral kind")
                        false
                    }
                    e.tags.size > maxTags -> {
                        drop("more than $maxTags tags")
                        false
                    }
                    e.tags.any { t -> t.any { it.toByteArray().size > MAX_TAG_VALUE_BYTES } } -> {
                        drop("tag value over ${MAX_TAG_VALUE_BYTES}B")
                        false
                    }
                    OptimizedJsonMapper.toJson(e).toByteArray().size > maxEventBytes -> {
                        drop("serialized size over ${maxEventBytes}B")
                        false
                    }
                    else -> true
                }
            }

        val verified =
            runBlocking {
                coroutineScope {
                    unique
                        .chunked(2048)
                        .map { chunk -> async(Dispatchers.Default) { chunk.filter { it.verify() } } }
                        .awaitAll()
                        .flatten()
                }
            }
        val invalidSigs = unique.size - verified.size
        if (invalidSigs > 0) drops["invalid signature"] = invalidSigs

        val sorted = verified.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val capped = if (limit in 1 until sorted.size) sorted.takeLast(limit) else sorted
        if (capped.size < sorted.size) drops["over --limit (kept newest)"] = sorted.size - capped.size

        drops.forEach { (reason, count) -> log("    dropped $count: $reason") }
        log("  prepared corpus: ${capped.size} events")
        return Corpus(capped, source, spec = null)
    }
}
