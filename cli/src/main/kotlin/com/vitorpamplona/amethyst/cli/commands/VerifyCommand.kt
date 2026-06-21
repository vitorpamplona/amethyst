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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip01Core.crypto.verifySignature

/**
 * `amy verify [EVENT-JSON]` — check a Nostr event's id hash and signature
 * (nak's `verify`). Local, no network, no account. The event JSON is read
 * from the positional argument, or from stdin when the argument is omitted
 * or `-`.
 *
 * Reports `id_ok` (computed id matches the `id` field) and `signature_ok`
 * separately so a caller can tell a tampered id apart from a bad sig.
 * `valid` is the conjunction. Exit code stays 0 — the result is data, not
 * a runtime failure (parse errors are runtime/bad_args).
 */
object VerifyCommand {
    fun run(rest: Array<String>): Int {
        val args = Args(rest)
        val arg = args.positionalOrNull(0)
        val json =
            if (arg == null || arg == "-") {
                System.`in`
                    .readBytes()
                    .decodeToString()
                    .trim()
            } else {
                arg.trim()
            }
        if (json.isEmpty()) return Output.error("bad_args", "no event JSON on the argument or stdin")

        val event =
            try {
                Event.fromJson(json)
            } catch (e: Exception) {
                return Output.error("bad_args", "could not parse event JSON: ${e.message}")
            }

        val idOk = event.verifyId()
        val sigOk = event.verifySignature()

        Output.emit(
            mapOf(
                "valid" to (idOk && sigOk),
                "id" to event.id,
                "id_ok" to idOk,
                "signature_ok" to sigOk,
            ),
        )
        return 0
    }
}
