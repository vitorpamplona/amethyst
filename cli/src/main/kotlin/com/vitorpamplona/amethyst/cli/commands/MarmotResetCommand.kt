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
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output

/**
 * `amy marmot reset [--yes]` — wipe all local Marmot state.
 *
 * Mirrors the Android "Reset Marmot State" danger-zone action. Deletes
 * every MLS group, every retained epoch secret, every persisted
 * KeyPackage bundle, every active relay subscription, and the run-state
 * since-cursors that track catch-up progress.
 *
 * Does NOT publish any SelfRemove/leave commits — the reset path is
 * specifically for recovering from corrupted or unrecoverable local
 * state where graceful teardown may be impossible.
 *
 * Requires `--yes` to execute, because the command is destructive and
 * cannot be undone. Without `--yes` the command prints the set of
 * groups that would be wiped and exits with code 2.
 */
object MarmotResetCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val confirmed = rest.any { it == "--yes" || it == "-y" }
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val groupIds =
                ctx.marmot
                    .activeGroupIds()
                    .toList()
                    .sorted()

            if (!confirmed) {
                Output.emit(
                    mapOf(
                        "dry_run" to true,
                        "would_wipe_groups" to groupIds,
                        "detail" to "pass --yes to actually reset",
                    ),
                )
                return 2
            }

            ctx.marmot.resetAllState()
            ctx.state.giftWrapSince = null
            ctx.state.groupSince.clear()

            Output.emit(
                mapOf(
                    "reset" to true,
                    "wiped_groups" to groupIds,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
