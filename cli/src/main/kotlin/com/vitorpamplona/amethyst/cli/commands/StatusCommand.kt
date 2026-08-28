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

import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.StoreFactory

/**
 * `amy status` — who is signed in, and what each of them has saved.
 *
 * Two questions, in that order. **Who**: every account under `~/.amy/`,
 * which one commands run as, the name/NIP-05 on its profile, and how (or
 * whether) it can sign. **What's saved**: the local footprint that account
 * has accumulated — follows and relay lists, its own events in the shared
 * store, address-book aliases, Marmot groups and their messages, a Marmot
 * KeyPackage, Concord communities, a Cashu wallet, and how far the DM
 * catch-up cursor has run. Anything an account does *not* have is left out
 * rather than printed as a zero, so a fresh account reads as one short
 * block instead of a wall of `no`.
 *
 * It also reports the one thing no other verb can: that **no account is
 * selected**. Everything but `use` and `status` resolves an account before
 * it runs and dies on a stale `current` pin or an ambiguous `~/.amy/`, so
 * the command you reach for to find out why has to name the cause. The
 * machine-level GrapeRank operator key gets a line for the same reason —
 * `listAccounts` skips it as a reserved name, so nothing else reports it.
 *
 * Deliberately out of scope: event-store internals (backend, disk bytes,
 * kind histogram) — that is `amy store stat`, and duplicating a partial,
 * FS-only view of it here is what made the old output so noisy.
 *
 * Cross-account by design, so it dispatches *before* account resolution
 * (like `use`) and never fails on "zero accounts" or "ambiguous account".
 * Strictly read-only: [StatusReport] parses the on-disk JSON and reads —
 * never creates — the shared event store. It never unlocks a private key
 * (no keychain prompt, no NIP-49 passphrase) and never touches the network.
 *
 * [StatusReport] holds the data model and the `--json` contract;
 * [StatusText] holds the terminal rendering.
 */
object StatusCommand {
    val USAGE: String =
        """
        |amy status — who is signed in and what each account has saved
        |locally. Read-only: no keychain prompt, no network. Takes no
        |arguments. For event-store size and backend, see `amy store stat`.
        """.trimMargin()

    suspend fun run(tail: Array<String>): Int {
        if (tail.firstOrNull() == "--help" || tail.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        // `status` takes no positional args; tolerate an accidental one
        // rather than erroring — it's a read-only inspection command.
        val rootBase = DataDir.DEFAULT_ROOT

        // One store handle for every account — it's shared. Null when the
        // machine has no store yet; a store we can't open (locked, corrupt)
        // degrades to the same thing, so the on-disk half still prints.
        val store = runCatching { StoreFactory.openExistingShared(rootBase) }.getOrNull()
        val overview =
            try {
                StatusReport.overview(rootBase, store)
            } finally {
                runCatching { store?.close() }
            }

        Output.emit(overview.toJson()) { color -> StatusText.render(overview, color) }
        return 0
    }
}
