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
import com.vitorpamplona.amethyst.cli.Identity
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray

/**
 * `amy key …` — standalone key utilities (nak's `key`). Local, no network,
 * no account; these never touch `~/.amy/` — they operate purely on the keys
 * passed in. Use `amy init` / `amy login` to persist an identity.
 *
 *   key generate                 mint a fresh keypair (prints nsec + npub + hex)
 *   key public <nsec|hex-priv>   derive the public key from a secret key
 *
 * Thin assembly only: key generation + bech32 derivation live in quartz
 * (reused here via [Identity], which wraps Quartz's `KeyPair`).
 */
object KeyCommands {
    fun dispatch(rest: Array<String>): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "key <generate|public>")
        val tail = rest.drop(1).toTypedArray()
        return when (rest[0]) {
            "generate" -> generate()
            "public" -> public(tail)
            else -> Output.error("bad_args", "key ${rest[0]} (expected generate|public)")
        }
    }

    private fun generate(): Int {
        val id = Identity.create()
        Output.emit(
            mapOf(
                "nsec" to id.nsec,
                "npub" to id.npub,
                "private_key" to id.privKeyHex,
                "pubkey" to id.pubKeyHex,
            ),
        )
        return 0
    }

    private fun public(rest: Array<String>): Int {
        val args = Args(rest)
        val input = args.positional(0, "secret-key").trim()
        val id =
            try {
                when {
                    input.startsWith("nsec") -> Identity.fromNsec(input)
                    input.length == 64 && input.lowercase().all { it in "0123456789abcdef" } ->
                        Identity.fromPrivateKey(input.hexToByteArray())
                    else -> return Output.error("bad_args", "expected an nsec or a 64-char hex private key")
                }
            } catch (e: Exception) {
                return Output.error("bad_args", "could not parse secret key: ${e.message}")
            }
        Output.emit(mapOf("npub" to id.npub, "pubkey" to id.pubKeyHex))
        return 0
    }
}
