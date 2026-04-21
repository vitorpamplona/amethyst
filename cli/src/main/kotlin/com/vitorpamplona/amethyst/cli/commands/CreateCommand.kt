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
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.amethyst.cli.RelayConfig
import com.vitorpamplona.amethyst.commons.account.bootstrapAccountEvents
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65List
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync

/**
 * `amy create [--name NAME]` — provision a brand-new Nostr account with the
 * same defaults Amethyst uses, publish the nine bootstrap events to the
 * default NIP-65 relay set, and seed this data-dir's relay config so
 * subsequent `amy marmot …` commands immediately target the right relays.
 *
 * The heavy lifting (which events to sign, with which defaults) lives in
 * `commons/.../AccountBootstrap.kt` so this command stays assembly-thin
 * and the on-relay shape matches the in-app flow byte-for-byte.
 */
object CreateCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (dataDir.loadIdentityOrNull() != null) {
            return Json.error("exists", "identity already exists at ${dataDir.identityFile}")
        }
        val args = Args(rest)
        val name = args.flag("name")

        // 1. Mint identity + seed relay config.
        val identity = Identity.create()
        dataDir.saveIdentity(identity)
        dataDir.saveRelays(defaultRelayConfig())

        // 2. Build the nine signed bootstrap events via the shared helper.
        val signer = NostrSignerSync(identity.keyPair())
        val bootstrap = bootstrapAccountEvents(signer, name)

        // 3. Open a Context so we reuse publishAndConfirmDetailed + the
        //    NostrClient plumbing instead of reinventing a second client.
        val ctx = Context.open(dataDir)
        val accepted = mutableMapOf<String, List<String>>()
        try {
            ctx.prepare()
            for (event in bootstrap.all()) {
                val ack = ctx.publish(event, DefaultNIP65RelaySet)
                accepted[event.kind.toString()] =
                    ack.filterValues { it }.keys.map { it.url }
            }
        } finally {
            ctx.close()
        }

        Json.writeLine(
            mapOf(
                "npub" to identity.npub,
                "hex" to identity.pubKeyHex,
                "name" to (name ?: ""),
                "data_dir" to dataDir.root.absolutePath,
                "published_kinds" to bootstrap.all().map { it.kind },
                "accepted_by" to accepted,
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

    /**
     * Mirror of what Amethyst's in-app defaults would write on disk — NIP-65
     * outbox, DM inbox (kind:10050), and KeyPackage host relays (kind:10051).
     * Keeping these in sync with the signed events above is load-bearing:
     * `amy marmot key-package publish` later reads `relays.json` to decide
     * where to publish, and if those diverge from the advertised kind:10051
     * nobody will find the KPs.
     */
    private fun defaultRelayConfig(): RelayConfig {
        val cfg = RelayConfig()
        DefaultNIP65List.forEach { cfg.add("nip65", it.relayUrl.url) }
        DefaultDMRelayList.forEach { cfg.add("inbox", it.url) }
        DefaultNIP65RelaySet.forEach { cfg.add("key_package", it.url) }
        return cfg
    }
}
