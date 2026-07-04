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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `amy nip <N>` / `amy nip list` — look up a NIP (nak's `nip`). Local-ish and
 * accountless.
 *
 * Resolution order, per request: the canonical `nostr-protocol/nips` git repo
 * **first**, then a fallback search on Nostr (NIP-50 over the NipText kind:30817,
 * wiki kind:30818 and long-form kind:30023) for relays/clients that publish NIPs
 * natively.
 *
 *   nip 46     fetch NIP-46 from the repo (falls back to Nostr on a miss)
 *   nip 7D     hex-suffixed NIPs work too (case-insensitive)
 *   nip list   fetch the repo's NIP index (README)
 */
object NipCommand {
    private const val RAW_BASE = "https://raw.githubusercontent.com/nostr-protocol/nips/master"

    // Amethyst's default NIP-50-capable search relays (search isn't universal).
    private val SEARCH_RELAYS = DefaultSearchRelayList

    // Quartz's canonical "NIP published on Nostr" kind (NipTextEvent), plus the
    // wiki + long-form kinds clients also use to mirror NIP text.
    private const val NIPTEXT_KIND = NipTextEvent.KIND
    private const val WIKI_KIND = 30818
    private const val LONGFORM_KIND = 30023

    suspend fun run(rest: Array<String>): Int {
        if (rest.firstOrNull() == "list") return list()
        val args = Args(rest)
        val raw = args.positional(0, "nip-number").trim()
        val slug = normalizeSlug(raw)
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 8L) * 1000

        // 1. Canonical repo first.
        val url = "$RAW_BASE/$slug.md"
        fetchText(url)?.let { text ->
            Output.emit(mapOf("nip" to slug, "source" to "repo", "url" to url, "title" to titleOf(text), "text" to text))
            return 0
        }

        // 2. Fall back to Nostr.
        val candidates = searchNostr(slug, timeoutMs)
        if (candidates.isEmpty()) {
            return Output.error("not_found", "NIP-$slug not in the repo and no matching wiki/long-form event on Nostr")
        }
        Output.emit(mapOf("nip" to slug, "source" to "nostr", "count" to candidates.size, "events" to candidates))
        return 0
    }

    private fun list(): Int {
        val url = "$RAW_BASE/README.md"
        val text = fetchText(url) ?: return Output.error("fetch_failed", "could not fetch the NIP index from $url")
        Output.emit(mapOf("source" to "repo", "url" to url, "text" to text))
        return 0
    }

    /** `1` -> `01`, `7d` -> `7D`, `46` -> `46`. The repo names files in 2-char upper-case. */
    private fun normalizeSlug(input: String): String {
        val cleaned = input.removePrefix("NIP-").removePrefix("nip-").uppercase()
        return if (cleaned.length == 1) "0$cleaned" else cleaned
    }

    /**
     * NIP docs use setext headings — `NIP-46\n======` (H1) then a descriptive
     * `Nostr Remote Signing\n--------` (H2). Prefer the descriptive H2, then
     * the H1, then any ATX `#` heading.
     */
    private fun titleOf(markdown: String): String? {
        val lines = markdown.lines()

        fun underlinedBy(
            ch: Char,
            i: Int,
        ): Boolean {
            val text = lines.getOrNull(i)?.trim().orEmpty()
            val rule = lines.getOrNull(i + 1)?.trim().orEmpty()
            return text.isNotEmpty() && !text.startsWith("#") && rule.length >= 3 && rule.all { it == ch }
        }
        for (i in lines.indices) if (underlinedBy('-', i)) return lines[i].trim()
        for (i in lines.indices) if (underlinedBy('=', i)) return lines[i].trim()
        return lines.firstOrNull { it.startsWith("# ") || it.startsWith("## ") }?.trimStart('#', ' ')?.trim()
    }

    private fun fetchText(url: String): String? =
        try {
            OkHttpClient()
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
                .use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
        } catch (e: Exception) {
            null
        }

    /** NIP-50 search across wiki + long-form for events that document the NIP. */
    private suspend fun searchNostr(
        slug: String,
        timeoutMs: Long,
    ): List<Map<String, Any?>> {
        if (SEARCH_RELAYS.isEmpty()) return emptyList()
        val okhttp = OkHttpClient.Builder().socketFactory(TcpNoDelaySocketFactory).build()
        val client = NostrClient(websocketBuilder = BasicOkHttpWebSocket.Builder { okhttp })
        val filter = Filter(kinds = listOf(NIPTEXT_KIND, WIKI_KIND, LONGFORM_KIND), search = "NIP-$slug", limit = 10)
        val subId = newSubId()
        val events = Channel<Event>(UNLIMITED)
        val done = Channel<NormalizedRelayUrl>(UNLIMITED)
        val remaining = SEARCH_RELAYS.toMutableSet()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    events.trySend(event)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    done.trySend(relay)
                }
            }
        val collected = mutableListOf<Event>()
        try {
            client.connect()
            client.subscribe(subId, SEARCH_RELAYS.associateWith { listOf(filter) }, listener)
            withTimeoutOrNull(timeoutMs) {
                while (remaining.isNotEmpty()) {
                    select<Unit> {
                        events.onReceive { collected.add(it) }
                        done.onReceive { remaining.remove(it) }
                    }
                }
            }
        } finally {
            client.unsubscribe(subId)
            client.close()
            events.close()
            done.close()
        }
        return collected
            .distinctBy { it.id }
            .take(10)
            .map { ev ->
                mapOf(
                    "id" to ev.id,
                    "kind" to ev.kind,
                    "pubkey" to ev.pubKey,
                    "title" to (ev.tags.firstOrNull { it.size > 1 && (it[0] == "title" || it[0] == "d") }?.get(1)),
                )
            }
    }
}
