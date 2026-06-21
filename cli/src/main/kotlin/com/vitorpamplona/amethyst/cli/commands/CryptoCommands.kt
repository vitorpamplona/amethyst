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
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output

/**
 * `amy encrypt --to USER [TEXT] [--nip04]` and
 * `amy decrypt --from USER [CIPHERTEXT] [--nip04]` — raw NIP-44 (default) or
 * NIP-04 message encryption with the active account's key (nak's
 * `encrypt`/`decrypt`).
 *
 * The plaintext/ciphertext is taken from the positional argument or stdin
 * (when omitted or `-`). USER accepts npub/nprofile/hex/NIP-05.
 *
 * Thin assembly only: the crypto lives in quartz (`NostrSigner.nip44*` /
 * `nip04*`); this file resolves the peer and shuttles strings.
 */
object EncryptCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val to = args.flag("to") ?: return Output.error("bad_args", "encrypt requires --to USER")
        val nip04 = args.bool("nip04")
        val text = RawEventSupport.readArgOrStdin(args)
        if (text.isEmpty()) return Output.error("bad_args", "no plaintext on the argument or stdin")

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peer = ctx.requireUserHex(to)
            val ciphertext =
                if (nip04) ctx.signer.nip04Encrypt(text, peer) else ctx.signer.nip44Encrypt(text, peer)
            Output.emit(mapOf("algorithm" to if (nip04) "nip04" else "nip44", "ciphertext" to ciphertext))
            return 0
        } finally {
            ctx.close()
        }
    }
}

object DecryptCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val from = args.flag("from") ?: return Output.error("bad_args", "decrypt requires --from USER")
        val nip04 = args.bool("nip04")
        val ciphertext = RawEventSupport.readArgOrStdin(args)
        if (ciphertext.isEmpty()) return Output.error("bad_args", "no ciphertext on the argument or stdin")

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peer = ctx.requireUserHex(from)
            val plaintext =
                if (nip04) ctx.signer.nip04Decrypt(ciphertext, peer) else ctx.signer.nip44Decrypt(ciphertext, peer)
            Output.emit(mapOf("algorithm" to if (nip04) "nip04" else "nip44", "plaintext" to plaintext))
            return 0
        } finally {
            ctx.close()
        }
    }
}
