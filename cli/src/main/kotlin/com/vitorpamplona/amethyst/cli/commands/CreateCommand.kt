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
import com.vitorpamplona.amethyst.commons.account.bootstrapAccountEvents
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65List
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync

/**
 * `amy create [--name NAME]` — provision a brand-new Nostr account with the
 * same defaults Amethyst uses and publish the nine bootstrap events to the
 * default NIP-65 relay set.
 *
 * The bootstrap events include kind:10002 / 10050 / 10051, which are
 * persisted into [Context.store] as a side effect of [Context.publish].
 * From that point on, [Context.outboxRelays], [Context.inboxRelays], and
 * [Context.keyPackageRelays] read straight from the local store — no
 * separate `relays.json` is needed.
 *
 * The heavy lifting (which events to sign, with which defaults) lives in
 * `commons/.../AccountBootstrap.kt` so this command stays assembly-thin
 * and the on-relay shape matches the in-app flow byte-for-byte.
 */
object CreateCommand {
    val USAGE: String =
        """
        |Account provisioning:
        |  create [--name NAME]    provision a full Amethyst-style account + publish the bootstrap
        |                          events (kind:0/3/10002/10050/10051/…) to the default NIP-65
        |                          relay set. Use `init` instead for a bare identity that
        |                          publishes nothing.
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        if (dataDir.identityExists()) {
            return Output.error("exists", "identity already exists at ${dataDir.identityFile}")
        }
        val args = Args(rest)
        val name = args.flag("name")
        args.rejectUnknown()

        // 1. Mint identity.
        val identity = Identity.create()
        dataDir.saveIdentity(identity)

        // 2. Build the nine signed bootstrap events via the shared helper.
        val signer = NostrSignerSync(identity.keyPair())
        val bootstrap = bootstrapAccountEvents(signer, name)

        // 3. Open a Context so we reuse publishAndConfirmDetailed + the
        //    NostrClient plumbing instead of reinventing a second client.
        //    Each ctx.publish call also persists the event into the local
        //    store via verifyAndStore — so kind:10002 / 10050 / 10051 are
        //    immediately readable by outboxRelays() / inboxRelays() /
        //    keyPackageRelays() without a separate config file.
        val accepted = mutableMapOf<String, List<String>>()
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            for (event in bootstrap.all()) {
                val ack = ctx.publish(event, DefaultNIP65RelaySet)
                accepted[event.kind.toString()] =
                    ack.filterValues { it.accepted }.keys.map { it.url }
            }
        }

        Output.emit(
            mapOf(
                "npub" to identity.npub,
                "hex" to identity.pubKeyHex,
                "name" to (name ?: ""),
                "data_dir" to dataDir.root.absolutePath,
                "published_kinds" to bootstrap.all().map { it.kind },
                "published_to" to accepted,
                "relays" to
                    mapOf(
                        "nip65" to DefaultNIP65List.map { it.relayUrl.url },
                        "inbox" to DefaultDMRelayList.map { it.url },
                        "key_package" to DefaultNIP65RelaySet.map { it.url },
                    ),
            ),
        )
        return 0
    }
}
