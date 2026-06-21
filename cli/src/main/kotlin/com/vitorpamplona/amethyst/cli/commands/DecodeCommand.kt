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
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec

/**
 * `amy decode <bech32>` — turn a NIP-19 / NIP-21 entity into its structured
 * parts (nak's `decode`). Local, no network, no account. Accepts an optional
 * `nostr:` prefix.
 *
 * Thin assembly only: all parsing lives in quartz's [Nip19Parser]; this file
 * just maps the parsed entity onto amy's result-map output contract.
 */
object DecodeCommand {
    fun run(rest: Array<String>): Int {
        val args = Args(rest)
        val input = args.positional(0, "entity").trim()

        val entity =
            Nip19Parser.uriToRoute(input)?.entity
                ?: return Output.error("bad_args", "not a recognized NIP-19 entity (npub, nsec, note, nevent, nprofile, naddr, nrelay, nembed)")

        val result: Map<String, Any?> =
            when (entity) {
                is NPub -> mapOf("type" to "npub", "pubkey" to entity.hex)
                is NSec -> mapOf("type" to "nsec", "private_key" to entity.hex, "pubkey" to entity.toPubKeyHex())
                is NNote -> mapOf("type" to "note", "id" to entity.hex)
                is NProfile ->
                    mapOf(
                        "type" to "nprofile",
                        "pubkey" to entity.hex,
                        "relays" to entity.relay.map { it.url },
                    )
                is NEvent ->
                    mapOf(
                        "type" to "nevent",
                        "id" to entity.hex,
                        "author" to entity.author,
                        "kind" to entity.kind,
                        "relays" to entity.relay.map { it.url },
                    )
                is NAddress ->
                    mapOf(
                        "type" to "naddr",
                        "kind" to entity.kind,
                        "pubkey" to entity.author,
                        "identifier" to entity.dTag,
                        "relays" to entity.relay.map { it.url },
                    )
                is NRelay -> mapOf("type" to "nrelay", "relays" to entity.relay)
                is NEmbed -> mapOf("type" to "nembed", "event" to Output.mapper.readTree(entity.event.toJson()))
                else -> return Output.error("bad_args", "unsupported entity type")
            }

        Output.emit(result)
        return 0
    }
}
