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

import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher

object KeyPackageCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "key-package <publish|check> …")
        return when (tail[0]) {
            "publish" -> publish(dataDir)
            "check" -> check(dataDir, tail.drop(1).toTypedArray())
            else -> Json.error("bad_args", "key-package ${tail[0]}")
        }
    }

    private suspend fun publish(dataDir: DataDir): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val relays = ctx.keyPackageRelays().ifEmpty { ctx.outboxRelays() }.ifEmpty { ctx.anyRelays() }
            if (relays.isEmpty()) return Json.error("no_relays", "configure relays first")

            val event = ctx.marmot.generateKeyPackageEvent(relays.toList())
            val ack = ctx.publish(event, relays)
            Json.writeLine(
                mapOf(
                    "event_id" to event.id,
                    "kind" to event.kind,
                    "accepted_by" to ack.filterValues { it }.keys.map { it.url },
                    "rejected_by" to ack.filterValues { !it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun check(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "key-package check <npub>")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val targetHex = ctx.requireUserHex(rest[0])
            // Per MIP-00: a user's KeyPackages live on the relays advertised
            // in their kind:10051 event (fallback: kind:10002 write marker).
            // Look those up first from bootstrap seeds so `check` works even
            // when the target and inviter share no relays.
            val seed = ctx.bootstrapRelays()
            if (seed.isEmpty()) return Json.error("no_relays", "configure relays first")
            val recipient = RecipientRelayFetcher.fetchRelayLists(ctx.client, targetHex, seed)
            val relays =
                KeyPackageFetcher.fetchRelaysFor(
                    targetKeyPackageRelays = recipient.keyPackage,
                    targetOutbox = recipient.nip65Write(),
                    myOutbox = seed,
                )
            val event =
                KeyPackageFetcher.fetchKeyPackage(
                    client = ctx.client,
                    targetPubKey = targetHex,
                    relays = relays,
                    timeoutMs = 10_000,
                )
            if (event == null) {
                return Json.error("not_found", "no KeyPackage for $targetHex on ${relays.size} relay(s)")
            }
            Json.writeLine(
                mapOf(
                    "event_id" to event.id,
                    "author" to event.pubKey,
                    "kind" to event.kind,
                    "created_at" to event.createdAt,
                    "has_content" to event.content.isNotBlank(),
                    "found_on" to relays.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
