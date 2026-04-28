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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/**
 * `amy relay <add|list|publish-lists>` — manage this account's relay sets.
 *
 * Source of truth is the local event store (`<data-dir>/events-store/`)
 * via Context.relaysOf / dmInboxOf / keyPackageRelaysOf. There is no
 * `relays.json` any more — the kind:10002 / 10050 / 10051 events ARE
 * the relay configuration.
 *
 *   - `relay add URL --type T`   builds + signs + ingests a new relay-
 *                                list event for the given bucket. No
 *                                broadcast yet — call `publish-lists`.
 *   - `relay list`               dumps the URLs from the local store.
 *   - `relay publish-lists`      broadcasts the three events to every
 *                                configured relay (union).
 */
object RelayCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Output.error("bad_args", "relay <add|list|publish-lists> …")
        val sub = tail[0]
        val rest = tail.drop(1).toTypedArray()
        return when (sub) {
            "add" -> add(dataDir, Args(rest))
            "list" -> list(dataDir)
            "publish-lists" -> publishLists(dataDir)
            else -> Output.error("bad_args", "relay $sub")
        }
    }

    private suspend fun add(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val rawUrl = args.positional(0, "url")
        val type = args.flag("type", "all") ?: "all"
        val normalized =
            rawUrl.normalizeRelayUrlOrNull()
                ?: return Output.error("bad_args", "invalid relay url: $rawUrl")

        val targets = if (type == "all") listOf("nip65", "inbox", "key_package") else listOf(type)
        val ctx = Context.open(dataDir)
        try {
            val addedTo = mutableListOf<String>()
            val alreadyPresent = mutableListOf<String>()
            for (t in targets) {
                if (addToBucket(ctx, t, normalized)) addedTo.add(t) else alreadyPresent.add(t)
            }
            Output.emit(
                mapOf(
                    "url" to rawUrl,
                    "added_to" to addedTo,
                    "already_present" to alreadyPresent,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Append [url] to the relay-list event for [type] (creating one if
     * absent). Returns `true` when a new event was inserted, `false`
     * when [url] was already present in the existing event.
     */
    private suspend fun addToBucket(
        ctx: Context,
        type: String,
        url: NormalizedRelayUrl,
    ): Boolean {
        val self = ctx.identity.pubKeyHex
        return when (type) {
            "nip65" -> {
                val existing = ctx.relaysOf(self)?.relays().orEmpty()
                if (existing.any { it.relayUrl.url == url.url }) return false
                val combined = existing + AdvertisedRelayInfo(url, AdvertisedRelayType.BOTH)
                val event = AdvertisedRelayListEvent.create(combined, ctx.signer)
                ctx.verifyAndStore(event)
                true
            }

            "inbox" -> {
                val existing = ctx.dmInboxOf(self)?.relays().orEmpty()
                if (url in existing) return false
                val event = ChatMessageRelayListEvent.create(existing + url, ctx.signer)
                ctx.verifyAndStore(event)
                true
            }

            "key_package", "keyPackage" -> {
                val existing = ctx.keyPackageRelaysOf(self)?.relays().orEmpty()
                if (url in existing) return false
                val event = KeyPackageRelayListEvent.create(existing + url, ctx.signer)
                ctx.verifyAndStore(event)
                true
            }

            else -> {
                throw IllegalArgumentException("unknown relay type: $type")
            }
        }
    }

    private suspend fun list(dataDir: DataDir): Int {
        val ctx = Context.open(dataDir)
        try {
            val self = ctx.identity.pubKeyHex
            Output.emit(
                mapOf(
                    "nip65" to (ctx.relaysOf(self)?.relaysNorm()?.map { it.url } ?: emptyList()),
                    "inbox" to (ctx.dmInboxOf(self)?.relays()?.map { it.url } ?: emptyList()),
                    "key_package" to (ctx.keyPackageRelaysOf(self)?.relays()?.map { it.url } ?: emptyList()),
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun publishLists(dataDir: DataDir): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val self = ctx.identity.pubKeyHex
            val nip65Event = ctx.relaysOf(self)
            val inboxEvent = ctx.dmInboxOf(self)
            val keyPackageEvent = ctx.keyPackageRelaysOf(self)

            if (nip65Event == null && inboxEvent == null && keyPackageEvent == null) {
                return Output.error(
                    "no_relays",
                    "no relay lists in the local store; run `amy relay add` first or `amy create` to bootstrap defaults",
                )
            }

            val targets = ctx.anyRelays()
            val nip65Result = nip65Event?.let { ctx.publish(it, targets) }.orEmpty()
            val inboxResult = inboxEvent?.let { ctx.publish(it, targets) }.orEmpty()
            val keyPackageResult = keyPackageEvent?.let { ctx.publish(it, targets) }.orEmpty()

            Output.emit(
                mapOf(
                    "nip65_event_id" to nip65Event?.id,
                    "inbox_event_id" to inboxEvent?.id,
                    "key_package_list_event_id" to keyPackageEvent?.id,
                    "accepted_by" to
                        mapOf(
                            "nip65" to nip65Result.filterValues { it }.keys.map { it.url },
                            "inbox" to inboxResult.filterValues { it }.keys.map { it.url },
                            "key_package_list" to keyPackageResult.filterValues { it }.keys.map { it.url },
                        ),
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
