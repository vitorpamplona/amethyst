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
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.model.EventThreadTree
import com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

/**
 * `amy notes thread EVENT [--limit N] [--refresh] [--timeout SECS]` — the whole
 * conversation EVENT belongs to: its root, then every NIP-10 reply and NIP-22
 * comment, laid out depth-first by the shared [EventThreadTree] and rendered by
 * the shared renderer. Replies are gathered from the local store plus the root
 * author's outbox and inbox (where the outbox model routes replies), the relays the
 * focus/root were seen on and the bootstrap set.
 */
object ThreadCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val refInput = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes thread EVENT [--limit N]")
        val refresh = args.bool("refresh")
        val limit = args.intFlag("limit", 500)
        if (limit <= 0) return Output.error("bad_args", "--limit must be > 0")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        val ref = NoteSupport.parseRef(refInput)

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val focus =
                NoteSupport.locate(ctx, ref, refresh, timeoutMs)
                    ?: return Output.error("not_found", "event not found: $refInput")

            val rootId = EventThreadTree.rootOf(focus.event) ?: focus.event.id
            val root =
                if (rootId == focus.event.id) {
                    focus
                } else {
                    val rootRef = EventRendererRegistry.render(focus.event).root
                    val hints = setOfNotNull(rootRef?.relay?.let { RelayUrlNormalizer.normalizeOrNull(it) }) + focus.seenOn
                    NoteSupport.locate(
                        ctx,
                        NoteSupport.Ref(rootId, Filter(ids = listOf(rootId), limit = 1), hints, rootRef?.author),
                        refresh,
                        timeoutMs,
                    )
                }

            val filters = replyFilters(rootId, root?.event, limit)
            val relays =
                buildSet<NormalizedRelayUrl> {
                    addAll(focus.seenOn)
                    root?.let {
                        addAll(it.seenOn)
                        addAll(NoteSupport.authorOutboxRelays(ctx, it.event.pubKey))
                        addAll(NoteSupport.authorInboxRelays(ctx, it.event.pubKey))
                    }
                    addAll(ctx.bootstrapRelays())
                }

            val fromRelays = ctx.drain(relays.associateWith { filters }, timeoutMs).map { it.second }
            val fromCache = filters.flatMap { ctx.store.query<Event>(it) }
            val events =
                (listOfNotNull(root?.event, focus.event) + fromCache + fromRelays)
                    .distinctBy { it.id }
                    .filter { it.id == rootId || it.id == focus.event.id || filters.any { f -> f.match(it) } }

            val entries = EventThreadTree.layout(rootId, events).take(limit)
            val renderCtx = NoteSupport.renderContext(ctx, entries.map { it.event.pubKey }, fetchMissing = true)

            Output.emit(
                mapOf(
                    "root_id" to rootId,
                    "root_found" to (root != null),
                    "focus_id" to focus.event.id,
                    "queried_relays" to relays.map { it.url },
                    "count" to entries.size,
                    "notes" to
                        entries.map { entry ->
                            mapOf(
                                "depth" to entry.depth,
                                "parent_id" to entry.parentId,
                                "parent_missing" to entry.parentMissing,
                                "is_focus" to (entry.event.id == focus.event.id),
                            ) + NoteSupport.render(entry.event, renderCtx, includeBody = false)
                        },
                ),
            )
            return 0
        }
    }

    /**
     * Everything that answers [rootId]: NIP-10 replies e-tag the root (marked or
     * positional), NIP-22 comments carry it as the uppercase `E` scope. An addressable
     * root is also reached through its `a` / `A` coordinate.
     */
    private fun replyFilters(
        rootId: String,
        root: Event?,
        limit: Int,
    ): List<Filter> {
        val kinds = listOf(TextNoteEvent.KIND, CommentEvent.KIND)
        val filters =
            mutableListOf(
                Filter(kinds = kinds, tags = mapOf("e" to listOf(rootId)), limit = limit),
                Filter(kinds = listOf(CommentEvent.KIND), tags = mapOf("E" to listOf(rootId)), limit = limit),
            )
        if (root is AddressableEvent) {
            val address = root.addressTag()
            filters += Filter(kinds = kinds, tags = mapOf("a" to listOf(address)), limit = limit)
            filters += Filter(kinds = listOf(CommentEvent.KIND), tags = mapOf("A" to listOf(address)), limit = limit)
        }
        return filters
    }
}
