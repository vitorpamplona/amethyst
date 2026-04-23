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
import com.vitorpamplona.quartz.nip01Core.core.Event

object MessageCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "message <send|list|react|delete> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "send" -> send(dataDir, rest)
            "list" -> list(dataDir, rest)
            "react" -> react(dataDir, rest)
            "delete" -> delete(dataDir, rest)
            else -> Json.error("bad_args", "message ${tail[0]}")
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "message send <gid> <text>")
        val text = rest[1]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
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
        val args = Args(rest.drop(1).toTypedArray())
        val limit = args.intFlag("limit", Int.MAX_VALUE)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
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

    private suspend fun react(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 3) return Json.error("bad_args", "message react <gid> <target_event_id> <emoji>")
        val targetId = rest[1]
        val emoji = rest[2]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val target = findStoredInnerEvent(ctx, gid, targetId) ?: return Json.error("not_found", targetId)
            val bundle = ctx.marmot.buildReactionMessage(gid, target, emoji)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "target_event_id" to target.id,
                    "reaction" to emoji,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun delete(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "message delete <gid> <target_event_id> [target_event_id ...]")
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Json.error("not_member", gid)

            val targetIds = rest.drop(1)
            val targets =
                targetIds.map { id ->
                    findStoredInnerEvent(ctx, gid, id) ?: return Json.error("not_found", id)
                }

            val bundle = ctx.marmot.buildDeletionMessage(gid, targets)
            val relays = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, relays)

            Json.writeLine(
                mapOf(
                    "group_id" to gid,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "kind" to bundle.innerEvent.kind,
                    "target_event_ids" to targets.map { it.id },
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    /**
     * Scan the group's persisted inner-message log for an event matching
     * [targetId] and reconstruct the full `Event`. Required by `react` and
     * `delete` because the NIP-25 / NIP-09 templates need the target's
     * `pubKey` + `kind` for p-tag / k-tag, not just the id.
     */
    private suspend fun findStoredInnerEvent(
        ctx: Context,
        nostrGroupId: String,
        targetId: String,
    ): Event? {
        for (line in ctx.marmot.loadStoredMessages(nostrGroupId)) {
            val parsed = Event.fromJsonOrNull(line) ?: continue
            if (parsed.id == targetId) return parsed
        }
        return null
    }
}
