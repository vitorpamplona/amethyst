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

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry
import com.vitorpamplona.amethyst.commons.rendering.RenderContext
import com.vitorpamplona.amethyst.commons.rendering.json.JsonEventFormatter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Shared plumbing for the verbs that act on an existing event (`notes show /
 * reply / quote / react / repost / thread`, `amy delete`): turn a user-typed
 * reference into the event and render events through the shared commons
 * renderer. Assembly only — routing, tagging, thread layout and the event
 * builders are the app's own code, reached through [NoteCache].
 */
object NoteSupport {
    /** A parsed `hex` / `note1` / `nevent1` / `naddr1` (optionally `nostr:`-prefixed) reference. */
    class Ref(
        val input: String,
        val filter: Filter,
        val hints: Set<NormalizedRelayUrl>,
        val author: HexKey?,
    )

    /** An event located through the cache or relays, with the relays that served it. */
    class Located(
        val event: Event,
        val seenOn: Set<NormalizedRelayUrl>,
        val source: String,
    )

    fun parseRef(input: String): Ref {
        val code = input.trim().removePrefix("nostr:")
        if (code.length == 64 && code.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return Ref(input, Filter(ids = listOf(code.lowercase()), limit = 1), emptySet(), null)
        }
        return when (val entity = Nip19Parser.uriToRoute(code)?.entity) {
            is NNote -> Ref(input, Filter(ids = listOf(entity.hex), limit = 1), emptySet(), null)
            is NEvent -> Ref(input, Filter(ids = listOf(entity.hex), limit = 1), entity.relay.toSet(), entity.author)
            is NAddress ->
                Ref(
                    input,
                    Filter(kinds = listOf(entity.kind), authors = listOf(entity.author), tags = mapOf("d" to listOf(entity.dTag)), limit = 1),
                    entity.relay.toSet(),
                    entity.author,
                )
            else -> throw IllegalArgumentException("not an event reference: '$input' (expects a 64-hex id, note1…, nevent1… or naddr1…)")
        }
    }

    /**
     * Cache-first lookup of [ref]: the local store answers unless [refresh] is set;
     * otherwise the hint relays, the author's NIP-65 outbox and the bootstrap set are
     * drained (the same outbox-model resolution `amy fetch CODE` uses). Returns the
     * newest match — an `naddr` names a replaceable slot — or null when nobody has it.
     */
    suspend fun locate(
        ctx: Context,
        ref: Ref,
        refresh: Boolean,
        timeoutMs: Long,
    ): Located? {
        if (!refresh) {
            ctx.store.query<Event>(ref.filter).maxByOrNull { it.createdAt }?.let {
                return Located(it, ref.hints, "cache")
            }
        }

        val relays = ref.hints + (ref.author?.let { authorOutboxRelays(ctx, it, timeoutMs) } ?: emptySet()) + ctx.bootstrapRelays()
        val received = ctx.drain(relays.associateWith { listOf(ref.filter) }, timeoutMs)
        val matches = received.filter { (_, event) -> ref.filter.match(event) }
        val newest = matches.maxByOrNull { it.second.createdAt }?.second ?: return null
        val seenOn = matches.filter { it.second.id == newest.id }.mapTo(mutableSetOf()) { it.first }
        return Located(newest, seenOn + ref.hints, "relays")
    }

    /** [author]'s NIP-65 write relays, draining their kind:10002 from the bootstrap set on a cache miss. */
    suspend fun authorOutboxRelays(
        ctx: Context,
        author: HexKey,
        timeoutMs: Long,
    ): Set<NormalizedRelayUrl> {
        ensureRelayList(ctx, author, timeoutMs)
        return ctx.relaysOf(author)?.writeRelaysNorm()?.toSet() ?: emptySet()
    }

    private suspend fun ensureRelayList(
        ctx: Context,
        author: HexKey,
        timeoutMs: Long,
    ) {
        if (ctx.relaysOf(author) != null) return
        val filter = Filter(authors = listOf(author), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1)
        ctx.drain(ctx.bootstrapRelays().associateWith { listOf(filter) }, timeoutMs)
    }

    /** Most profiles one command fetches from relays; the rest render from the store or by npub. */
    private const val MAX_PROFILE_FETCH = 100

    /**
     * Profiles for [pubKeys] as a [RenderContext]: from the local store, and — when
     * [fetchMissing] — one drain of the first [MAX_PROFILE_FETCH] missing kind:0s (in
     * [pubKeys] order, so authors listed first win) from the index + bootstrap relays.
     * A kind:3 tags thousands of people; their names are not worth thousands of fetches.
     */
    suspend fun renderContext(
        ctx: Context,
        pubKeys: Collection<HexKey>,
        fetchMissing: Boolean,
        timeoutMs: Long,
    ): RenderContext {
        val wanted = pubKeys.filterTo(LinkedHashSet()) { it.isValid() }
        if (wanted.isEmpty()) return RenderContext.EMPTY

        val profiles = HashMap<HexKey, UserMetadata>()

        fun collect(events: Collection<Event>) {
            events
                .filterIsInstance<MetadataEvent>()
                .groupBy { it.pubKey }
                .forEach { (pubKey, list) -> list.maxBy { it.createdAt }.contactMetaData()?.let { profiles[pubKey] = it } }
        }

        collect(ctx.store.query<Event>(Filter(authors = wanted.toList(), kinds = listOf(MetadataEvent.KIND))))

        val missing = (wanted - profiles.keys).take(MAX_PROFILE_FETCH)
        if (fetchMissing && missing.isNotEmpty()) {
            val relays = ctx.indexRelays() + ctx.bootstrapRelays()
            val filter = Filter(authors = missing.toList(), kinds = listOf(MetadataEvent.KIND), limit = missing.size)
            collect(ctx.drain(relays.associateWith { listOf(filter) }, timeoutMs).map { it.second })
        }
        return RenderContext(profiles)
    }

    /** Every pubkey a rendered view of [events] can name: authors, tagged users and zap senders. */
    fun peopleIn(events: Collection<Event>): Set<HexKey> =
        buildSet {
            events.forEach {
                add(it.pubKey)
                addAll(it.taggedUserIds())
            }
        }

    fun render(
        event: Event,
        renderCtx: RenderContext,
        includeBody: Boolean = true,
    ): Map<String, Any?> = JsonEventFormatter.toMap(EventRendererRegistry.render(event, renderCtx), includeBody)

    /** The `target` block every interaction verb echoes back. */
    fun targetFields(target: Located): Map<String, Any?> =
        mapOf(
            "event_id" to target.event.id,
            "kind" to target.event.kind,
            "author" to target.event.pubKey,
            "source" to target.source,
        )
}
