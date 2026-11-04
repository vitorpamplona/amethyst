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
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.EventLocator
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.EventRef
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.ProfileLoader
import com.vitorpamplona.amethyst.commons.relayClient.oneshot.ThreadLoader

/**
 * `amy notes thread EVENT [--limit N] [--refresh] [--timeout SECS]` — the conversation
 * EVENT belongs to, loaded and laid out by commons' [ThreadLoader], which runs the app's
 * thread screen code (its relay filters, `ThreadAssembler`, `ThreadFeedFilter` and
 * `ThreadLevelCalculator`) over the store and the relays.
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
        val ref = EventRef.parse(refInput)

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val access = NoteSupport.access(ctx)
            val focus =
                EventLocator.locate(access, ref, refresh, timeoutMs)
                    ?: return Output.error("not_found", "event not found: $refInput")

            val thread =
                ThreadLoader(access, timeoutMs).load(focus, viewer = ctx.identity.pubKeyHex)
                    ?: return Output.error("not_found", "event not found (deleted): $refInput")

            val shown = thread.ordered.filter { it.event != null }.take(limit)
            val renderCtx = ProfileLoader.renderContext(access, shown.mapNotNull { it.event?.pubKey }, fetchMissing = true, timeoutMs = timeoutMs)
            val root = thread.root

            Output.emit(
                mapOf(
                    // Known even when no relay returned the root: it is the id the replies name.
                    "root_id" to (root?.event?.id ?: root?.idHex),
                    "root_found" to (root?.event != null),
                    "focus_id" to focus.event.id,
                    "queried_relays" to thread.queriedRelays.map { it.url }.sorted(),
                    "count" to shown.size,
                    "missing" to thread.ordered.count { it.event == null },
                    "notes" to
                        shown.map { note ->
                            mapOf(
                                "depth" to thread.depth(note),
                                "parent_ids" to note.replyTo?.map { it.idHex }.orEmpty(),
                                "is_focus" to (note == thread.focus),
                            ) + NoteSupport.render(note.event!!, renderCtx, includeBody = false)
                        },
                ),
            )
            return 0
        }
    }
}
