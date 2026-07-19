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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNsec

/**
 * `amy encode <type> …` — build a NIP-19 entity from raw parts (nak's
 * `encode`). Local, no network, no account.
 *
 *   encode npub <hex>
 *   encode nsec <hex>
 *   encode note <event-id-hex>
 *   encode nevent <event-id-hex> [--author HEX] [--kind N] [--relay URL[,URL…]]
 *   encode nprofile <pubkey-hex> [--relay URL[,URL…]]
 *   encode naddr --kind N --pubkey HEX --identifier D [--relay URL[,URL…]]
 *
 * Thin assembly only: every encoder lives in quartz's NIP-19 entities; this
 * file parses flags and calls them.
 */
object EncodeCommand {
    val USAGE: String =
        """
        |amy encode — build a NIP-19 entity from raw parts (local, no account)
        |
        |  encode npub HEX              encode a public key
        |  encode nsec HEX              encode a private key
        |  encode note ID               encode an event id
        |  encode nevent ID [--author HEX] [--kind N] [--relay URL[,URL…]]
        |  encode nprofile HEX [--relay URL[,URL…]]
        |  encode naddr --kind N --pubkey HEX --identifier D [--relay URL[,URL…]]
        """.trimMargin()

    fun run(rest: Array<String>): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        if (rest.isEmpty()) return Output.error("bad_args", "encode <npub|nsec|note|nevent|nprofile|naddr> …")
        val type = rest[0]
        val args = Args(rest.drop(1).toTypedArray())
        // Flags are read branch-dependently below, so whitelist the union.
        args.rejectUnknown("author", "kind", "relay", "pubkey", "identifier")

        return when (type) {
            "npub" -> emit("npub", NPub.create(hex32(args.positional(0, "pubkey-hex"))))
            "nsec" -> emit("nsec", hex32(args.positional(0, "private-key-hex")).hexToByteArray().toNsec())
            "note" -> emit("note", NNote.create(hex32(args.positional(0, "event-id-hex"))))
            "nevent" ->
                emit(
                    "nevent",
                    NEvent.create(
                        idHex = hex32(args.positional(0, "event-id-hex")),
                        author = args.flag("author"),
                        kind = args.flag("kind")?.toIntOrNull(),
                        relays = relays(args),
                    ),
                )
            "nprofile" ->
                emit(
                    "nprofile",
                    NProfile.create(
                        authorPubKeyHex = hex32(args.positional(0, "pubkey-hex")),
                        relays = relays(args),
                    ),
                )
            "naddr" ->
                emit(
                    "naddr",
                    NAddress.create(
                        kind = args.requireFlag("kind").toIntOrNull() ?: return Output.error("bad_args", "--kind must be an integer"),
                        pubKeyHex = hex32(args.requireFlag("pubkey")),
                        dTag = args.flag("identifier", "") ?: "",
                        relays = relays(args),
                    ),
                )
            else -> Output.error("bad_args", "encode $type (expected npub|nsec|note|nevent|nprofile|naddr)")
        }
    }

    /** Validate a 64-char hex key/id; bare-hex only — bech32 inputs go through `decode` first. */
    private fun hex32(value: String): String {
        val v = value.trim().lowercase()
        require(v.length == 64 && v.all { it in "0123456789abcdef" }) {
            "expected 64-char hex, got '$value'"
        }
        return v
    }

    private fun relays(args: Args): List<NormalizedRelayUrl> =
        args
            .flag("relay")
            ?.split(',')
            ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
            .orEmpty()

    private fun emit(
        key: String,
        value: String,
    ): Int {
        Output.emit(mapOf(key to value))
        return 0
    }
}
