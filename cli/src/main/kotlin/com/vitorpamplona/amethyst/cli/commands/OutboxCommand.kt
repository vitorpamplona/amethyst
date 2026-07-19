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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * `amy outbox USER [--refresh] [--timeout SECS]` — show a user's NIP-65
 * (kind:10002) read/write relays — the outbox-model relay hints (nak's
 * `outbox`). USER accepts npub/nprofile/hex/NIP-05.
 *
 * Cache-first: reads the local store, falling back to a relay drain on a
 * miss (or always with `--refresh`).
 */
object OutboxCommand {
    val USAGE: String =
        """
        |amy outbox — show a user's NIP-65 (kind:10002) outbox-model relays
        |
        |  outbox USER [--refresh]       show USER's NIP-65 read/write relays (outbox model)
        |        [--timeout SECS]         (USER: npub|nprofile|hex|name@domain; cache-first,
        |                                  --refresh forces a relay drain; default timeout 8s)
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        val args = Args(rest)
        val user = args.positional(0, "user")
        val refresh = args.bool("refresh")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val pubkey = ctx.requireUserHex(user)

            var event = if (refresh) null else ctx.relaysOf(pubkey)
            if (event == null) {
                ctx.drain(
                    ctx.bootstrapRelays().associateWith {
                        listOf(
                            Filter(authors = listOf(pubkey), kinds = listOf(AdvertisedRelayListEvent.KIND), limit = 1),
                        )
                    },
                    timeoutMs,
                )
                event = ctx.relaysOf(pubkey)
            }

            if (event == null) {
                Output.emit(mapOf("pubkey" to pubkey, "npub" to NPub.create(pubkey), "found" to false))
                return 0
            }

            Output.emit(
                mapOf(
                    "pubkey" to pubkey,
                    "npub" to NPub.create(pubkey),
                    "found" to true,
                    "created_at" to event.createdAt,
                    "read" to (event.readRelaysNorm()?.map { it.url } ?: emptyList()),
                    "write" to (event.writeRelaysNorm()?.map { it.url } ?: emptyList()),
                    "all" to event.relaysNorm().map { it.url },
                ),
            )
            return 0
        }
    }
}
