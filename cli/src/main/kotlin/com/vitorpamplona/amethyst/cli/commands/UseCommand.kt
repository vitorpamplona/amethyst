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
import com.vitorpamplona.amethyst.cli.SecureFileIO
import java.io.File

/**
 * `amy use <name>` — pin the active account in `~/.amy/current`.
 *
 * Sits outside the `DataDir`-needing dispatch path: its whole purpose
 * is to disambiguate the auto-pick when more than one account exists,
 * so it must work even when `DataDir.resolve` would otherwise fail.
 *
 * `amy use` with no argument prints the current pin (or `(none)` when
 * unset). `amy use --clear` removes the marker entirely.
 */
object UseCommand {
    fun run(tail: Array<String>): Int {
        val rootBase = DataDir.DEFAULT_ROOT
        val markerFile = File(rootBase, DataDir.CURRENT_MARKER_NAME)

        if (tail.isEmpty()) {
            val pinned = if (markerFile.isFile) markerFile.readText().trim().ifEmpty { null } else null
            Output.emit(
                mapOf(
                    "current" to pinned,
                    "available" to DataDir.listAccounts(rootBase),
                    "root" to rootBase.absolutePath,
                ),
            )
            return 0
        }

        if (tail[0] == "--clear") {
            val existed = markerFile.isFile && markerFile.delete()
            Output.emit(
                mapOf(
                    "current" to null,
                    "cleared" to existed,
                    "root" to rootBase.absolutePath,
                ),
            )
            return 0
        }

        val name =
            try {
                DataDir.validateName(tail[0])
            } catch (e: IllegalArgumentException) {
                return Output.error("bad_args", e.message)
            }
        val accountDir = File(rootBase, name)
        if (!accountDir.isDirectory) {
            return Output.error(
                "no_account",
                "${accountDir.absolutePath} doesn't exist; create it with `amy --account $name init`",
            )
        }
        SecureFileIO.secureMkdirs(rootBase)
        SecureFileIO.writeTextAtomic(markerFile, name)
        Output.emit(
            mapOf(
                "current" to name,
                "root" to rootBase.absolutePath,
            ),
        )
        return 0
    }
}
