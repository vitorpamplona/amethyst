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
import com.vitorpamplona.amethyst.cli.Output

object InitCommands {
    suspend fun init(
        dataDir: DataDir,
        args: Args,
    ): Int {
        // On re-run we return metadata only. Unlocking the stored secret here
        // would trigger a keychain prompt / passphrase dialog even though the
        // caller clearly already has the identity set up.
        dataDir.loadIdentityFileOrNull()?.let { existing ->
            Output.emit(
                mapOf(
                    "npub" to existing.npub,
                    "hex" to existing.pubKeyHex,
                    "nsec" to null,
                    "existing" to true,
                    "data_dir" to dataDir.root.absolutePath,
                ),
            )
            return 0
        }
        val nsec = args.flag("nsec")
        val created = if (nsec != null) Identity.fromNsec(nsec) else Identity.create()
        dataDir.saveIdentity(created)
        Output.emit(
            mapOf(
                "npub" to created.npub,
                "hex" to created.pubKeyHex,
                "nsec" to created.nsec,
                "existing" to false,
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }

    suspend fun whoami(dataDir: DataDir): Int {
        // Intentionally metadata-only so `whoami` doesn't pop a keychain prompt
        // or ask for a NIP-49 passphrase just to echo the npub.
        val file = dataDir.loadIdentityFileOrNull()
        if (file == null) {
            return Output.error("no_identity", "No identity at ${dataDir.identityFile}. Run `init` first.")
        }
        Output.emit(
            mapOf(
                "npub" to file.npub,
                "hex" to file.pubKeyHex,
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }
}
