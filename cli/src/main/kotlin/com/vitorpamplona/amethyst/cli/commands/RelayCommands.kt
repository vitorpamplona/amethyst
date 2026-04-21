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
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

object RelayCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "relay <add|list|publish-lists> …")
        val sub = tail[0]
        val rest = tail.drop(1).toTypedArray()
        return when (sub) {
            "add" -> add(dataDir, Args(rest))
            "list" -> list(dataDir)
            "publish-lists" -> publishLists(dataDir)
            else -> Json.error("bad_args", "relay $sub")
        }
    }

    private fun add(
        dataDir: DataDir,
        args: Args,
    ): Int {
        val url = args.positional(0, "url")
        val type = args.flag("type", "all") ?: "all"
        val cfg = dataDir.loadRelays()
        val addedTo = mutableListOf<String>()
        val targets = if (type == "all") listOf("nip65", "inbox", "key_package") else listOf(type)
        for (t in targets) {
            if (cfg.add(t, url)) addedTo.add(t)
        }
        dataDir.saveRelays(cfg)
        Json.writeLine(
            mapOf(
                "url" to url,
                "added_to" to addedTo,
                "already_present" to (targets - addedTo.toSet()),
            ),
        )
        return 0
    }

    private fun list(dataDir: DataDir): Int {
        val cfg = dataDir.loadRelays()
        Json.writeLine(
            mapOf(
                "nip65" to cfg.nip65,
                "inbox" to cfg.inbox,
                "key_package" to cfg.keyPackage,
            ),
        )
        return 0
    }

    private suspend fun publishLists(dataDir: DataDir): Int {
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val nip65Relays = ctx.relays.normalized("nip65").toList()
            val inboxRelays = ctx.relays.normalized("inbox").toList()

            val nip65Infos = nip65Relays.map { AdvertisedRelayInfo(it, AdvertisedRelayType.BOTH) }
            val nip65Event = AdvertisedRelayListEvent.create(nip65Infos, ctx.signer)
            val inboxEvent = ChatMessageRelayListEvent.create(inboxRelays, ctx.signer)

            val targets = ctx.anyRelays()
            val nip65Result = ctx.publish(nip65Event, targets)
            val inboxResult = ctx.publish(inboxEvent, targets)

            Json.writeLine(
                mapOf(
                    "nip65_event_id" to nip65Event.id,
                    "inbox_event_id" to inboxEvent.id,
                    "accepted_by" to
                        mapOf(
                            "nip65" to nip65Result.filterValues { it }.keys.map { it.url },
                            "inbox" to inboxResult.filterValues { it }.keys.map { it.url },
                        ),
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }
}
