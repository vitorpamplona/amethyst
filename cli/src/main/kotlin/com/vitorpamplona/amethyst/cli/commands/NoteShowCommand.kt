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

/**
 * `amy notes show EVENT [--refresh] [--raw] [--timeout SECS]` — one event, rendered
 * by the shared commons [com.vitorpamplona.amethyst.commons.rendering.EventRendererRegistry]:
 * author profile, rich-text body spans, reply/root refs, mentions, quotes,
 * hashtags, media and the kind-specific details. Works on any kind (unknown kinds
 * fall back to the default renderer). Cache-first; read-only, so it also runs
 * without an account.
 */
object NoteShowCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val refInput = args.positionalOrNull(0) ?: return Output.error("bad_args", "notes show EVENT [--refresh] [--raw]")
        val refresh = args.bool("refresh")
        val raw = args.bool("raw")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()
        val ref = EventRef.parse(refInput)

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val access = NoteSupport.access(ctx)
            val located =
                EventLocator.locate(access, ref, refresh, timeoutMs)
                    ?: return Output.error("not_found", "event not found: $refInput")
            val renderCtx = ProfileLoader.renderContext(access, ProfileLoader.peopleIn(listOf(located.event)), fetchMissing = true, timeoutMs = timeoutMs)

            Output.emit(
                mapOf(
                    "source" to located.source,
                    "seen_on" to located.seenOn.map { it.url },
                    "note" to NoteSupport.render(located.event, renderCtx),
                    "event" to if (raw) Output.mapper.readTree(located.event.toJson()) else null,
                ),
            )
            return 0
        }
    }
}
