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
import com.vitorpamplona.amethyst.cli.commands.trust.TrustCheck
import com.vitorpamplona.amethyst.cli.commands.trust.TrustCopy
import com.vitorpamplona.amethyst.cli.commands.trust.TrustSetup
import com.vitorpamplona.amethyst.cli.commands.trust.TrustStatus
import com.vitorpamplona.amethyst.cli.commands.trust.TrustSync

/**
 * `amy trust …` — the consumer side of NIP-85: the user's Web of Trust network, as the apps
 * keep it. Downloads the account's rank provider's kind 30382 cards into a local index and
 * answers who is in the network. (The provider side, computing and publishing cards, is
 * `amy graperank`.)
 *
 *  - `sync [--update|--full|--redownload]` — bring the local index up to date.
 *  - `status` — what is on disk, without touching the network.
 *  - `check USER…` — known or stranger, and why: the rule behind the DM tabs, Curated
 *    notifications and collapsed replies.
 *  - `setup brainstorm [--private]` — Brainstorm's guided sign-up, then the kind 10040.
 *  - `copy USER [--private]` — use USER's kind 10040 rows: the network as USER sees it.
 */
object TrustCommand {
    const val USAGE = "trust <sync|status|check|setup|copy>"

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "trust",
            tail,
            USAGE,
            mapOf(
                "sync" to { rest -> TrustSync.run(dataDir, rest) },
                "status" to { rest -> TrustStatus.run(dataDir, rest) },
                "check" to { rest -> TrustCheck.run(dataDir, rest) },
                "setup" to { rest -> TrustSetup.run(dataDir, rest) },
                "copy" to { rest -> TrustCopy.run(dataDir, rest) },
            ),
        )
}
