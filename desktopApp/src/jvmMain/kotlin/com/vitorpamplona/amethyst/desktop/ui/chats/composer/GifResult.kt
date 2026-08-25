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
package com.vitorpamplona.amethyst.desktop.ui.chats.composer

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

/** A single GIF result surfaced from a NIP-94 (kind 1063) metadata event. */
data class GifResult(
    val url: String,
    val description: String,
)

/**
 * Nostr-native GIF search. Queries the given [relays] for NIP-94 kind-1063
 * file-metadata events tagged `m=image/gif` (the shape GIF Buddy publishes),
 * merges + dedups by URL across relays, and filters client-side by [query]
 * (the default relay has no NIP-50, so matching is done here). No third-party
 * GIF API, no API key — just relay REQs on the app's own (Tor-aware) client.
 */
suspend fun searchGifs(
    client: INostrClient,
    relays: List<String>,
    query: String,
    limitPerRelay: Int = 100,
): List<GifResult> {
    val trimmed = query.trim()
    val filter =
        Filter(
            kinds = listOf(FileHeaderEvent.KIND),
            tags = mapOf("m" to listOf("image/gif")),
            limit = limitPerRelay,
            search = trimmed.ifBlank { null },
        )

    val raw = mutableListOf<GifResult>()
    for (relay in relays) {
        val events =
            runCatching { client.fetchAll(relay, filter, idleTimeoutMs = 8_000L) }
                .getOrDefault(emptyList())
        for (event in events) {
            val fileHeader = event as? FileHeaderEvent ?: continue
            val url = fileHeader.url() ?: continue
            raw.add(GifResult(url = url, description = fileHeader.content))
        }
    }
    return mergeGifResults(raw, trimmed)
}

/**
 * Dedups [raw] GIF results by URL (keeping first-seen order — newest-first since
 * fetchAll sorts descending) and, when [query] is non-blank, filters to entries
 * whose description or URL contains it (case-insensitive). Pure so it can be
 * unit-tested without a relay client.
 */
internal fun mergeGifResults(
    raw: List<GifResult>,
    query: String,
): List<GifResult> {
    val byUrl = LinkedHashMap<String, GifResult>()
    for (gif in raw) {
        if (!byUrl.containsKey(gif.url)) byUrl[gif.url] = gif
    }
    val all = byUrl.values.toList()
    val trimmed = query.trim()
    return if (trimmed.isBlank()) {
        all
    } else {
        all.filter {
            it.description.contains(trimmed, ignoreCase = true) ||
                it.url.contains(trimmed, ignoreCase = true)
        }
    }
}
