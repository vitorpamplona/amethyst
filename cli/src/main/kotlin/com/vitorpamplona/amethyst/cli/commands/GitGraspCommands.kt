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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent

/**
 * `amy git grasp <list|set>` — a user's NIP-34 GRASP (Git-over-Nostr hosting)
 * server list (kind 10317). Functions like NIP-65's relay list: it declares,
 * in preference order, where PR tip branches (`refs/nostr/<pr-id>`) get pushed
 * so maintainers know where to fetch them. `ngit` and `nak git` read this to
 * pick a push host; amy publishes and reads the list (the git push itself is
 * out of scope — see cli/ROADMAP.md).
 */
object GitGraspCommands {
    val USAGE: String =
        """
        |amy git grasp — NIP-34 GRASP server list (kind 10317)
        |
        |  git grasp list [USER]                        list a user's grasp servers (default self)
        |  git grasp set URL[,URL] [--relay URL[,URL]]  publish your grasp server list (preference order)
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "git grasp",
            tail,
            "git grasp <list|set>",
            mapOf(
                "list" to { rest -> list(dataDir, rest) },
                "set" to { rest -> set(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val author = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            args.rejectUnknown("relay")
            val filter = Filter(kinds = listOf(UserGraspListEvent.KIND), authors = listOf(author), limit = 1)
            var event = ctx.store.query<Event>(filter).firstOrNull() as? UserGraspListEvent
            if (event == null) {
                val relays = RawEventSupport.queryTargets(ctx, args)
                ctx.drain(relays.associateWith { listOf(filter) })
                event = ctx.store.query<Event>(filter).firstOrNull() as? UserGraspListEvent
            }
            val grasps = event?.grasps().orEmpty()
            Output.emit(mapOf("pubkey" to author, "count" to grasps.size, "grasps" to grasps))
            return 0
        }
    }

    private suspend fun set(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val grasps =
            args
                .positional(0, "grasp-server-urls")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (grasps.isEmpty()) return Output.error("bad_args", "git grasp set requires URL[,URL] (a comma-separated grasp server list)")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template = UserGraspListEvent.build(grasps)
            val signed = ctx.signer.sign(template)
            val targets = RawEventSupport.publishTargets(ctx, args)
            args.rejectUnknown()
            val ack = ctx.publish(signed, targets)
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "grasps" to grasps,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
