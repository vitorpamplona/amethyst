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
import com.vitorpamplona.quartz.kinds.KindNames

/**
 * `amy kind <N|name>` — look up a Nostr event kind (nak's `kind`). Local,
 * accountless. A number prints that kind's label + defining NIP; a text query
 * searches labels.
 *
 *   kind 1              -> {"kind":1,"name":"Short Text Note","nip":"10",...}
 *   kind "text note"    -> {"query":…,"matches":[…]}
 *
 * Thin assembly only: the canonical kind registry lives in quartz
 * (`KindNames`) — the same data the Android relay view localizes on top of.
 */
object KindCommand {
    fun run(rest: Array<String>): Int {
        val args = Args(rest)
        val arg = args.positional(0, "kind-number-or-name").trim()
        val n = arg.toIntOrNull()
        if (n != null) {
            val info = KindNames.infoFor(n)
            Output.emit(
                mapOf(
                    "kind" to n,
                    "name" to info?.name,
                    "nip" to info?.nip,
                    "known" to (info != null),
                ),
            )
            return 0
        }
        val matches = KindNames.search(arg)
        Output.emit(
            mapOf(
                "query" to arg,
                "count" to matches.size,
                "matches" to matches.map { (kind, info) -> mapOf("kind" to kind, "name" to info.name, "nip" to info.nip) },
            ),
        )
        return 0
    }
}
