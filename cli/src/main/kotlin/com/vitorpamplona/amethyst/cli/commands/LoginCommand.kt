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
import com.vitorpamplona.amethyst.cli.Identity
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
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
    val USAGE: String =
        """
        |amy login — import an identifier and persist the identity
        |
        |  login KEY [--password X]     KEY: nsec | ncryptsec (needs --password, alias --pw) |
        |                                BIP-39 mnemonic | 64-hex privkey (with --private) |
        |                                npub/nprofile/64-hex pubkey (read-only) |
        |                                NIP-05 name@domain (read-only) | bunker://…
        |  login bunker://PUBKEY?relay=…&secret=…      sign through a remote NIP-46 bunker
        |  login --nostrconnect [--relay URL[,URL…]]   client-initiated: print a nostrconnect://
        |    [--name N] [--perms P] [--timeout SECS]    offer, wait for a signer, persist it
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        // NIP-46 NostrConnect (client-initiated) login: no key positional —
        // amy mints a transport key, prints an offer, and waits for a signer.
        val preArgs = Args(rest)
        if (preArgs.bool("nostrconnect")) {
            return NostrConnect.login(dataDir, preArgs)
        }
        if (rest.isEmpty()) {
            return Output.error("bad_args", "login <nsec|ncryptsec|mnemonic|npub|nprofile|hex|nip05|bunker://|--nostrconnect> [--password X]")
        }
        if (dataDir.identityExists()) {
            return Output.error("exists", "identity already exists at ${dataDir.identityFile}; use a fresh --data-dir or delete it first")
        }

        val key = rest[0].trim()
        val args = Args(rest.drop(1).toTypedArray())
        // --password/--pw/--private are read branch-dependently inside
        // resolveIdentity, so whitelist them up front.
        args.rejectUnknown("password", "pw", "private")

        val identity =
            resolveIdentity(key, args)
                ?: return Output.error(
                    "bad_key",
                    "could not parse '$key' as any supported identifier",
                )

        dataDir.saveIdentity(identity)

        // For a bunker, the pubkey in the URI is the REMOTE SIGNER's key, which for many signer apps
        // (Amber, nsec.app) is a per-connection key distinct from the user's identity key. Resolve the
        // real identity via the NIP-46 get_public_key RPC and persist THAT (the bunker's remote key is
        // kept in Identity.bunker for transport addressing). Best-effort: if the bunker can't answer,
        // fall back to the URI pubkey so login still succeeds.
        val account = if (identity.bunker != null) resolveBunkerIdentity(dataDir, identity) else identity

        Output.emit(
            mapOf(
                "npub" to account.npub,
                "hex" to account.pubKeyHex,
                "read_only" to !account.canSign,
                "signer" to if (account.bunker != null) "bunker" else "local",
                "bunker_relays" to account.bunker?.relays,
                "data_dir" to dataDir.root.absolutePath,
            ),
        )
        return 0
    }

    /**
     * Connect the freshly-saved bunker and ask it (NIP-46 `get_public_key`) for the user's real
     * identity pubkey, re-persisting the [Identity] when it differs from the bunker's transport key.
     * Returns the corrected identity, or [provisional] unchanged if the RPC fails.
     */
    private suspend fun resolveBunkerIdentity(
        dataDir: DataDir,
        provisional: Identity,
    ): Identity =
        try {
            Context.open(dataDir).use { ctx ->
                ctx.prepare()
                val real = (ctx.signer as NostrSignerRemote).getPublicKey().lowercase()
                if (real == provisional.pubKeyHex.lowercase()) {
                    provisional
                } else {
                    val corrected = provisional.copy(pubKeyHex = real, npub = real.hexToByteArray().toNpub())
                    dataDir.saveIdentity(corrected)
                    corrected
                }
            }
        } catch (e: Exception) {
            System.err.println("[nip46] could not resolve identity via get_public_key (${e.message}); using the bunker URI pubkey")
            provisional
        }

    private suspend fun resolveIdentity(
        key: String,
        args: Args,
    ): Identity? {
        // 0. NIP-46 bunker connection string.
        if (key.startsWith("bunker://")) return Identity.fromBunkerUri(key)
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
