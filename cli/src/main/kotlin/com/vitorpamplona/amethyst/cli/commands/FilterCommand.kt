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
import com.vitorpamplona.amethyst.cli.Output

/**
 * `amy filter [--kind …] [--author …] [--id …] [--tag …] [--since/--until TS]
 *             [--limit N] [--search TEXT]` — assemble a NIP-01 filter JSON
 * from flags and print it (nak's `filter`). Local, no network, no account —
 * handy for piping into another tool or eyeballing what `fetch`/`subscribe`
 * would send.
 *
 * Same flag grammar as `fetch`/`subscribe`; `--author`/`--id` accept
 * npub/nevent/note/naddr or hex (local decode only).
 */
object FilterCommand {
    fun run(rest: Array<String>): Int {
        val filter = RawEventSupport.buildFilter(Args(rest))
        Output.emit(Output.mapper.readTree(filter.toJson()))
        return 0
    }
}
