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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.model.ThreadLevelCalculator
import com.vitorpamplona.amethyst.commons.relayClient.thread.filterEventsInThreadForRoot
import com.vitorpamplona.amethyst.commons.relayClient.thread.filterMissingEventsForThread
import com.vitorpamplona.amethyst.commons.viewmodels.thread.ThreadFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

/**
 * `amy notes thread EVENT [--limit N] [--refresh] [--timeout SECS]` — the conversation
 * EVENT belongs to, loaded and laid out by the app's own thread code over a
 * [NoteCache]:
 *
 *  - what to fetch is the thread screen's two relay filters
 *    ([filterEventsInThreadForRoot]: everything citing the root on its author's inbox
 *    and where it was seen; [filterMissingEventsForThread]: parents and roots not loaded
 *    yet), repeated until a round brings nothing new;
 *  - which notes form the thread is [ThreadAssembler];
 *  - the order and indentation are [ThreadFeedFilter.thread] and
 *    [ThreadLevelCalculator.replyLevel], exactly what the app shows.
 *
 * Each round reads the local store before the relays, so a thread amy has already seen
 * costs no extra round-trip.
 */
object ThreadCommand {
    private const val MAX_ROUNDS = 5

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

            val notes = NoteCache(ctx)
            val focusNote = notes.add(focus.event, focus.seenOn) ?: return Output.error("not_found", "event not found: $refInput")
            val defaultRelays = ctx.bootstrapRelays()
            val queried = mutableSetOf<String>()

            for (round in 0 until MAX_ROUNDS) {
                val assembler = ThreadAssembler(notes.cache)
                val info = assembler.findThreadFor(focusNote.idHex) ?: break
                val root = assembler.findRoot(focusNote.idHex) ?: focusNote
                // Inbox relays decide where the thread's replies are looked for.
                notes.addUsers(info.allNotes.mapNotNull { it.author?.pubkeyHex }, fetchMissing = round == 0)

                val filters = filterEventsInThreadForRoot(root, null) + filterMissingEventsForThread(notes.cache, info, defaultRelays)
                if (filters.isEmpty()) break
                queried += filters.map { it.relay.url }

                val before = loaded(notes, focusNote)
                fetch(ctx, notes, filters, timeoutMs)
                if (loaded(notes, focusNote) == before) break
            }

            val me = notes.user(ctx.identity.pubKeyHex)
            val following = ctx.contactsOf(ctx.identity.pubKeyHex)?.verifiedFollowKeySet() ?: emptySet()
            val ordered = ThreadFeedFilter.thread(focusNote.idHex, notes.cache, me, following)
            val shown = ordered.filter { it.event != null }.take(limit)
            val levels = mutableMapOf<Note, Int>()
            val root = ThreadAssembler(notes.cache).findRoot(focusNote.idHex)

            val events = shown.mapNotNull { it.event }
            val renderCtx = NoteSupport.renderContext(ctx, events.map { it.pubKey }, fetchMissing = true)

            Output.emit(
                mapOf(
                    "root_id" to root?.event?.id,
                    "root_found" to (root?.event != null),
                    "focus_id" to focus.event.id,
                    "queried_relays" to queried.sorted(),
                    "count" to shown.size,
                    "missing" to ordered.count { it.event == null },
                    "notes" to
                        shown.map { note ->
                            mapOf(
                                "depth" to ThreadLevelCalculator.replyLevel(note, levels),
                                "parent_ids" to note.replyTo?.map { it.idHex }.orEmpty(),
                                "is_focus" to (note == focusNote),
                            ) + NoteSupport.render(note.event!!, renderCtx, includeBody = false)
                        },
                ),
            )
            return 0
        }
    }

    private fun loaded(
        notes: NoteCache,
        focus: Note,
    ): Int = ThreadAssembler(notes.cache).findThreadFor(focus.idHex)?.allNotes?.count { it.event != null } ?: 0

    /** One round of the thread screen's filters: the store first, then the relays, all into [notes]. */
    private suspend fun fetch(
        ctx: Context,
        notes: NoteCache,
        filters: List<RelayBasedFilter>,
        timeoutMs: Long,
    ) {
        filters.map { it.filter }.distinct().forEach { filter ->
            ctx.store.query<Event>(filter).forEach { notes.add(it) }
        }
        val byRelay = filters.groupBy({ it.relay }, { it.filter })
        ctx.drain(byRelay, timeoutMs).forEach { (relay, event) -> notes.add(event, listOf(relay)) }
    }
}
