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
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Identity
import com.vitorpamplona.amethyst.cli.Json

object InitCommands {
    suspend fun init(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val existing = dataDir.loadIdentityOrNull()
        val id =
            existing ?: run {
                val nsec = args.flag("nsec")
                val created = if (nsec != null) Identity.fromNsec(nsec) else Identity.create()
                dataDir.saveIdentity(created)
                created
            }
        Json.writeLine(
            mapOf(
                "npub" to id.npub,
                "hex" to id.pubKeyHex,
                "nsec" to id.nsec,
                "existing" to (existing != null),
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }

    suspend fun whoami(dataDir: DataDir): Int {
        val id = dataDir.loadIdentityOrNull()
        if (id == null) {
            return Json.error("no_identity", "No identity at ${dataDir.identityFile}. Run `init` first.")
        }
        Json.writeLine(
            mapOf(
                "npub" to id.npub,
                "hex" to id.pubKeyHex,
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }
}
