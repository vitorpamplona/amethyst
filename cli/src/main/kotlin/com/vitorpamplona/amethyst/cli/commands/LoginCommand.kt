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
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import okhttp3.OkHttpClient

/**
 * `amy login KEY [--password X]` — import any of the identifier forms
 * Amethyst's login screen accepts and persist the identity to the
 * data-dir. Mirrors [AccountSessionManager.loginSync] but on the JVM.
 *
 * Accepted forms (tried in this order):
 *  - nsec1…                                → full account
 *  - ncryptsec… + --password X             → NIP-49 decrypt → full
 *  - BIP-39 mnemonic (space-separated)     → NIP-06 derive → full
 *  - 64-hex private key (with --private)   → full
 *  - npub1… / nprofile1… / 64-hex pubkey   → read-only
 *  - NIP-05 identifier (name@domain.tld)   → read-only (HTTP lookup)
 */
object LoginCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) {
            return Output.error("bad_args", "login <nsec|ncryptsec|mnemonic|npub|nprofile|hex|nip05> [--password X]")
        }
        if (dataDir.identityExists()) {
            return Output.error("exists", "identity already exists at ${dataDir.identityFile}; use a fresh --data-dir or delete it first")
        }

        val key = rest[0].trim()
        val args = Args(rest.drop(1).toTypedArray())

        val identity =
            resolveIdentity(key, args)
                ?: return Output.error(
                    "bad_key",
                    "could not parse '$key' as any supported identifier",
                )

        dataDir.saveIdentity(identity)
        Output.emit(
            mapOf(
                "npub" to identity.npub,
                "hex" to identity.pubKeyHex,
                "read_only" to !identity.hasPrivateKey,
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }

    private suspend fun resolveIdentity(
        key: String,
        args: Args,
    ): Identity? {
        // 1. ncryptsec — password mandatory.
        if (key.startsWith("ncryptsec")) {
            val pw =
                args.flag("password") ?: args.flag("pw")
                    ?: throw IllegalArgumentException("ncryptsec input requires --password")
            val privHex = Nip49().decrypt(key, pw)
            return Identity.fromPrivateKey(
                com.vitorpamplona.quartz.utils.Hex
                    .decode(privHex),
            )
        }
        // 2. nsec
        if (key.startsWith("nsec1")) return Identity.fromNsec(key)
        // 3. mnemonic (space-separated, 12/24 words)
        if (key.contains(' ') && Nip06().isValidMnemonic(key)) {
            val priv = Nip06().privateKeyFromMnemonic(key)
            return Identity.fromPrivateKey(priv)
        }
        // 4. 64-hex privkey — only when explicitly asked; otherwise a bare
        //    hex string is ambiguous with a pubkey and we default to public.
        if (args.bool("private") && isHex64(key)) {
            return Identity.fromPrivateKey(
                com.vitorpamplona.quartz.utils.Hex
                    .decode(key),
            )
        }
        // 5. everything else — defer to the shared resolver (npub / nprofile /
        //    hex pubkey / NIP-05). Read-only.
        val pubHex = resolveUserHexOrNull(key, nip05Client()) ?: return null
        return Identity.fromPublicKeyHex(pubHex)
    }

    private fun isHex64(s: String): Boolean = s.length == 64 && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    private fun nip05Client(): Nip05Client {
        // Build a throwaway OkHttp client — we don't hold a Context here and
        // login is a one-shot CLI invocation anyway.
        val http = OkHttpClient.Builder().build()
        return Nip05Client(fetcher = OkHttpNip05Fetcher { _ -> http })
    }
}
