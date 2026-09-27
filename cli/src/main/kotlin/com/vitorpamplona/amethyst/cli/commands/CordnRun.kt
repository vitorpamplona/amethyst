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
import com.vitorpamplona.amethyst.cli.CordnArgException
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.cordn.CoordinatorConfig
import com.vitorpamplona.amethyst.commons.cordn.CordnSession

/**
 * The plumbing every `amy cordn` verb that touches a coordinator shares.
 *
 * Two things live here rather than in each command. The first is opening and
 * closing a session around a block, because a CLI run that forgets to close
 * leaves a relay subscription behind. The second is turning the three ways a
 * cordn call fails into the CLI's three error codes, which matters more than
 * it looks: `bad_args` means the caller can fix it, `coordinator` means the
 * other end did something, and anything else is ours. A script that cannot
 * tell those apart has to treat every failure as fatal.
 */
internal object CordnRun {
    /**
     * Opens a session on the coordinator [args] names, runs [block], closes.
     *
     * The session is restored from disk first, so a `gid` from a previous run
     * is already known and a cursor already positioned. That is the whole
     * continuity story for a process-per-command client: `spec/02.md` §7 calls
     * a cursor a delivery primitive, and here it is also the only thing
     * carried between invocations.
     */
    suspend fun withSession(
        dataDir: DataDir,
        args: Args,
        block: suspend (Context, CordnSessionScope) -> Int,
    ): Int =
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            try {
                val config = ctx.cordn.resolve(args)
                val session = ctx.cordn.session(config)
                try {
                    block(ctx, CordnSessionScope(config, session))
                } finally {
                    ctx.cordn.close()
                }
            } catch (e: CordnArgException) {
                Output.error("bad_args", e.message ?: "bad coordinator selection")
            }
        }

    /** Config plus session, so a command does not have to carry both. */
    class CordnSessionScope(
        val config: CoordinatorConfig,
        val session: CordnSession,
    ) {
        val manager get() = session.manager
        val keyPackages get() = session.keyPackages
    }

    /**
     * The `gid` a command was given, or the only one we are in.
     *
     * Same rule as `--coordinator`: convenient when there is one, refuses to
     * guess when there are several. A `gid` is unique only within a
     * coordinator (`spec/00.md` §4), so a wrong guess is not a near miss.
     */
    fun gid(
        args: Args,
        scope: CordnSessionScope,
    ): String {
        val requested = args.flag("gid")
        if (requested != null) return requested
        val known = scope.manager.gids.value
        return when (known.size) {
            0 -> throw CordnArgException("not in any group on this coordinator yet")
            1 -> known.single()
            else -> throw CordnArgException("several groups here, name one with --gid: ${known.sorted().joinToString(", ")}")
        }
    }
}
