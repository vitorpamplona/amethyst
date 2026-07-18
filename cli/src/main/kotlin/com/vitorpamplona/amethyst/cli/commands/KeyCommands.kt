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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49

/**
 * `amy key …` — standalone key utilities (nak's `key`). Local, no network,
 * no account; these never touch `~/.amy/` — they operate purely on the keys
 * passed in. Use `amy init` / `amy login` to persist an identity.
 *
 *   key generate                 mint a fresh keypair (prints nsec + npub + hex)
 *   key public <nsec|hex-priv>   derive the public key from a secret key
 *   key encrypt <nsec|hex> --password X   NIP-49 encrypt to an ncryptsec1…
 *   key decrypt <ncryptsec> --password X  NIP-49 decrypt back to a secret key
 *
 * Thin assembly only: key generation + bech32 derivation live in quartz
 * (reused here via [Identity] / [Nip49]).
 */
object KeyCommands {
    val USAGE: String =
        """
        |amy key — standalone key utilities (local, no network, no account)
        |
        |  key generate                 mint a fresh keypair (nsec + npub + hex)
        |  key public NSEC|HEX          derive the public key from a secret key
        |  key encrypt NSEC|HEX --password X    NIP-49 encrypt to ncryptsec1…
        |  key decrypt NCRYPTSEC --password X   NIP-49 decrypt back to a secret key
        |  key validate PUBKEY          check an npub/64-hex pubkey is structurally valid
        |                                (reports {"valid": false} instead of erroring)
        |
        |  --pw is accepted as an alias of --password.
        """.trimMargin()

    suspend fun dispatch(rest: Array<String>): Int =
        route(
            "key",
            rest,
            "key <generate|public|encrypt|decrypt|validate>",
            mapOf(
                "generate" to { _ -> generate() },
                "public" to { tail -> public(tail) },
                "encrypt" to { tail -> encrypt(tail) },
                "decrypt" to { tail -> decrypt(tail) },
                "validate" to { tail -> validate(tail) },
            ),
            help = USAGE,
        )

    /**
     * `key validate PUBKEY` — nak's `key validate`. Parses an npub or 64-hex
     * public key and reports whether it is a structurally valid x-only pubkey.
     * Never errors on a bad key — it reports `{"valid": false}` so scripts can
     * branch on the field rather than the exit code.
     */
    private fun validate(rest: Array<String>): Int {
        val args = Args(rest)
        args.rejectUnknown()
        val input = args.positional(0, "pubkey").trim()
        val hex =
            when {
                input.startsWith("npub") -> runCatching { input.bechToBytes().toHexKey() }.getOrNull()
                input.length == 64 && input.lowercase().all { it in "0123456789abcdef" } -> input.lowercase()
                else -> null
            }
        // A Nostr pubkey is a 32-byte x-only key. Confirm length after decode.
        val valid = hex != null && hex.length == 64
        if (!valid) {
            Output.emit(mapOf("valid" to false))
            return 0
        }
        val npub = hex.hexToByteArray().toNpub()
        Output.emit(mapOf("valid" to true, "pubkey" to hex, "npub" to npub))
        return 0
    }

    /** Read the private key (nsec or 64-hex) into hex, or null if unparseable. */
    private fun privHexOrNull(input: String): String? =
        when {
            input.startsWith("nsec") -> runCatching { input.bechToBytes().toHexKey() }.getOrNull()
            input.length == 64 && input.lowercase().all { it in "0123456789abcdef" } -> input.lowercase()
            else -> null
        }

    private fun encrypt(rest: Array<String>): Int {
        val args = Args(rest)
        val priv = privHexOrNull(args.positional(0, "secret-key").trim()) ?: return Output.error("bad_args", "expected an nsec or 64-char hex secret key")
        val password = args.flag("password") ?: args.flag("pw") ?: return Output.error("bad_args", "key encrypt requires --password")
        args.rejectUnknown()
        val ncryptsec = Nip49().encrypt(priv, password)
        Output.emit(mapOf("ncryptsec" to ncryptsec))
        return 0
    }

    private fun decrypt(rest: Array<String>): Int {
        val args = Args(rest)
        val ncryptsec = args.positional(0, "ncryptsec").trim()
        if (!ncryptsec.startsWith("ncryptsec")) return Output.error("bad_args", "expected an ncryptsec1… string")
        val password = args.flag("password") ?: args.flag("pw") ?: return Output.error("bad_args", "key decrypt requires --password")
        args.rejectUnknown()
        val privHex =
            try {
                Nip49().decrypt(ncryptsec, password)
            } catch (e: Exception) {
                return Output.error("decrypt_failed", e.message ?: "could not decrypt (wrong password?)")
            }
        val id = Identity.fromPrivateKey(privHex.hexToByteArray())
        Output.emit(mapOf("nsec" to id.nsec, "npub" to id.npub, "private_key" to id.privKeyHex, "pubkey" to id.pubKeyHex))
        return 0
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
        args.rejectUnknown()
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
