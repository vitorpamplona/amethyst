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

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json

object MessageCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "message <send|list> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "send" -> send(dataDir, rest)
            "list" -> list(dataDir, rest)
            else -> Json.error("bad_args", "message ${tail[0]}")
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "message send <gid> <text>")
        val gid = rest[0]
        val text = rest[1]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val bundle = ctx.marmot.buildTextMessage(gid, text)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "message list <gid>")
        val gid = rest[0]
        val args = Args(rest.drop(1).toTypedArray())
        val limit = args.intFlag("limit", Int.MAX_VALUE)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val raw = ctx.marmot.loadStoredMessages(gid)
            val items =
                raw
                    .map { line ->
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val obj = Json.mapper.readValue<Map<String, Any?>>(line)
                            mapOf(
                                "id" to obj["id"],
                                "author" to obj["pubkey"],
                                "kind" to obj["kind"],
                                "content" to obj["content"],
                                "created_at" to obj["created_at"],
                            )
                        } catch (_: Exception) {
                            mapOf("raw" to line)
                        }
                    }.takeLast(limit)

            Json.writeLine(mapOf("group_id" to gid, "messages" to items))
            return 0
        } finally {
            ctx.close()
        }
    }
}
