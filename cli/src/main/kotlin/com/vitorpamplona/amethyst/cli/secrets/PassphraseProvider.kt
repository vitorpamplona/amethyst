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
package com.vitorpamplona.amethyst.cli.secrets

import java.io.File

/**
 * Resolves a passphrase for NIP-49 operations. Precedence (highest first):
 *
 *  1. `--passphrase-file PATH` — trimmed trailing newline; convenient for
 *     scripted test harnesses where a fifo/tmpfile is set up per invocation.
 *  2. `$AMY_PASSPHRASE` — quick and agent-friendly. Visible in `/proc/PID/environ`
 *     to other same-user processes, so prefer the file form on shared machines.
 *  3. TTY prompt — last resort; requires `System.console()` (so not under
 *     `runBlocking` from a bare `java -cp` invocation without a terminal).
 */
class PassphraseProvider(
    private val fileFlag: String? = null,
    private val envName: String = "AMY_PASSPHRASE",
) {
    fun read(
        prompt: String,
        confirm: Boolean = false,
    ): String {
        fileFlag?.let { path ->
            val text = File(path).readText().trimEnd('\n', '\r')
            if (text.isEmpty()) throw IllegalArgumentException("--passphrase-file $path is empty")
            return text
        }
        System.getenv(envName)?.takeIf { it.isNotEmpty() }?.let { return it }
        val console =
            System.console()
                ?: throw IllegalStateException(
                    "No TTY and no passphrase source: set $envName or pass --passphrase-file PATH.",
                )
        val first = console.readPassword("$prompt: ").concatToString()
        if (first.isEmpty()) throw IllegalArgumentException("empty passphrase")
        if (confirm) {
            val second = console.readPassword("Confirm passphrase: ").concatToString()
            if (first != second) throw IllegalArgumentException("passphrases do not match")
        }
        return first
    }
}
