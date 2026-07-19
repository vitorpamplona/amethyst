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

import com.vitorpamplona.amethyst.cli.Output

/**
 * Shared sub-verb router used by every `*Commands.dispatch`.
 *
 * Maps the first token of [tail] to a handler over the remaining args.
 * `--help` / `-h` / `help` prints the group's [help] text (falling back to
 * the one-line [usage]) to stderr and exits 0. Empty input emits
 * `bad_args: <usage>`; an unrecognised verb names the verbs that do exist.
 * Handlers receive the args *after* the verb, mirroring the old
 * hand-rolled `when (tail[0]) { … }` blocks.
 */
suspend fun route(
    name: String,
    tail: Array<String>,
    usage: String,
    routes: Map<String, suspend (Array<String>) -> Int>,
    help: String? = null,
): Int {
    when (tail.firstOrNull()) {
        null -> return Output.error("bad_args", usage)
        "--help", "-h", "help" -> {
            System.err.println(help ?: usage)
            return 0
        }
    }
    val handler =
        routes[tail[0]]
            ?: return Output.error("bad_args", "unknown verb: $name ${tail[0]} (expected ${routes.keys.joinToString("|")})")
    return handler(tail.drop(1).toTypedArray())
}
