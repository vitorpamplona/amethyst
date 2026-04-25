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
import com.vitorpamplona.amethyst.cli.AwaitTimeout
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.amethyst.cli.commands.AwaitCommands.awaitAdmin
import com.vitorpamplona.amethyst.cli.commands.AwaitCommands.awaitMember
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import kotlinx.coroutines.delay

/**
 * `await` subcommands. Each polls until a condition is met or the timeout elapses;
 * on timeout we throw [AwaitTimeout] which maps to exit code 124.
 */
object AwaitCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "await <key-package|group|member|admin|message|rename|epoch>")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "key-package" -> awaitKeyPackage(dataDir, rest)
            "group" -> awaitGroup(dataDir, rest)
            "member" -> awaitMember(dataDir, rest)
            "admin" -> awaitAdmin(dataDir, rest)
            "message" -> awaitMessage(dataDir, rest)
            "rename" -> awaitRename(dataDir, rest)
            "epoch" -> awaitEpoch(dataDir, rest)
            else -> Json.error("bad_args", "await ${tail[0]}")
        }
    }

    private suspend fun awaitKeyPackage(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "await key-package <npub>")
        val args = Args(rest.drop(1).toTypedArray())
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val target = ctx.requireUserHex(rest[0])
            val filter = ctx.marmot.subscriptionManager.keyPackageFilter(target)
            // MIP-00: target's KeyPackages live on the relays advertised in
            // their kind:10051 (fallback: kind:10002 write). Resolve the
            // right relay set once up front against bootstrap seeds; the
            // polling loop then hits those relays on every tick. If the
            // target hasn't published either list yet, fall back to the
            // bootstrap pool so the loop still has something to query.
            val seed = ctx.bootstrapRelays()
            // Cache-first: relay lists are kind:10050 / 10051 / 10002 —
            // all replaceable, served from the local store via the slot
            // shortcut. Falls back to a network drain only if Amy has
            // never observed any of them for `target`.
            val lists =
                ctx.cachedRelayListsOf(target)
                    ?: RecipientRelayFetcher.fetchRelayLists(ctx.client, target, seed)
            val relays =
                KeyPackageFetcher.fetchRelaysFor(
                    targetKeyPackageRelays = lists.keyPackage,
                    targetOutbox = lists.nip65Write(),
                    myOutbox = seed,
                )
            if (relays.isEmpty()) {
                throw AwaitTimeout("no relays to query for $target (configure relays or bootstrap defaults first)")
            }
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                val event =
                    ctx.client.fetchFirst(
                        filters = relays.associateWith { listOf(filter) },
                        timeoutMs = 3_000,
                    )
                if (event is KeyPackageEvent) {
                    Json.writeLine(
                        mapOf(
                            "event_id" to event.id,
                            "author" to event.pubKey,
                            "found_on" to relays.map { it.url },
                        ),
                    )
                    return 0
                }
                delay(2_000)
            }
            throw AwaitTimeout("no KeyPackage for $target within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    private suspend fun awaitGroup(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val wantedName = args.flag("name")
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                ctx.syncIncoming(timeoutMs = 3_000)
                val match =
                    ctx.marmot.activeGroupIds().firstOrNull { gid ->
                        wantedName == null || ctx.marmot.groupMetadata(gid)?.name == wantedName
                    }
                if (match != null) {
                    Json.writeLine(
                        mapOf(
                            "group_id" to match,
                            "mls_group_id" to ctx.marmot.mlsGroupIdHex(match),
                            "name" to (ctx.marmot.groupMetadata(match)?.name ?: ""),
                            "epoch" to ctx.marmot.groupEpoch(match),
                        ),
                    )
                    return 0
                }
                delay(1_500)
            }
            throw AwaitTimeout("no group with name=$wantedName within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    private suspend fun awaitMember(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        pollCondition(dataDir, rest, "await member <gid> <npub>", targetIdx = 1) { ctx, rawArgs ->
            val gid = ctx.resolveGroupId(rawArgs[0])
            val target = ctx.requireUserHex(rawArgs[1])
            if (!ctx.marmot.isMember(gid)) {
                null
            } else if (ctx.marmot.memberPubkeys(gid).any { it.pubkey == target }) {
                mapOf("group_id" to gid, "pubkey" to target, "epoch" to ctx.marmot.groupEpoch(gid))
            } else {
                null
            }
        }

    private suspend fun awaitAdmin(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        pollCondition(dataDir, rest, "await admin <gid> <npub>", targetIdx = 1) { ctx, rawArgs ->
            val gid = ctx.resolveGroupId(rawArgs[0])
            val target = ctx.requireUserHex(rawArgs[1])
            if (!ctx.marmot.isMember(gid)) {
                null
            } else if (ctx.marmot
                    .groupMetadata(gid)
                    ?.adminPubkeys
                    ?.contains(target) == true
            ) {
                mapOf("group_id" to gid, "pubkey" to target, "epoch" to ctx.marmot.groupEpoch(gid))
            } else {
                null
            }
        }

    private suspend fun awaitRename(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "await rename <gid> --name <name>")
        val args = Args(rest.drop(1).toTypedArray())
        val wantedName = args.requireFlag("name")
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                ctx.syncIncoming(timeoutMs = 3_000)
                val name = ctx.marmot.groupMetadata(gid)?.name
                if (name == wantedName) {
                    Json.writeLine(mapOf("group_id" to gid, "name" to name, "epoch" to ctx.marmot.groupEpoch(gid)))
                    return 0
                }
                delay(1_500)
            }
            throw AwaitTimeout("group $gid never renamed to $wantedName within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    private suspend fun awaitEpoch(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "await epoch <gid> --min N")
        val args = Args(rest.drop(1).toTypedArray())
        val min = args.longFlag("min", 1)
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                ctx.syncIncoming(timeoutMs = 3_000)
                val epoch = ctx.marmot.groupEpoch(gid)
                if (epoch != null && epoch >= min) {
                    Json.writeLine(mapOf("group_id" to gid, "epoch" to epoch))
                    return 0
                }
                delay(1_500)
            }
            throw AwaitTimeout("group $gid epoch never reached $min within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    private suspend fun awaitMessage(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Json.error("bad_args", "await message <gid> --match STRING")
        val args = Args(rest.drop(1).toTypedArray())
        val needle = args.requireFlag("match")
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                ctx.syncIncoming(timeoutMs = 3_000)
                val msgs = ctx.marmot.loadStoredMessages(gid)
                for (line in msgs.asReversed()) {
                    val obj =
                        try {
                            Json.mapper.readValue<Map<String, Any?>>(line)
                        } catch (_: Exception) {
                            null
                        } ?: continue
                    val content = obj["content"]?.toString() ?: continue
                    if (content.contains(needle)) {
                        Json.writeLine(
                            mapOf(
                                "group_id" to gid,
                                "id" to obj["id"],
                                "author" to obj["pubkey"],
                                "content" to content,
                                "kind" to obj["kind"],
                            ),
                        )
                        return 0
                    }
                }
                delay(1_500)
            }
            throw AwaitTimeout("no message matching '$needle' in $gid within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    /**
     * Generic poll loop used by [awaitMember] / [awaitAdmin] — both take the
     * same `<gid> <npub>` positional shape and differ only in the predicate.
     */
    private suspend fun pollCondition(
        dataDir: DataDir,
        rest: Array<String>,
        usage: String,
        targetIdx: Int,
        check: suspend (Context, Array<String>) -> Map<String, Any?>?,
    ): Int {
        if (rest.size <= targetIdx) return Json.error("bad_args", usage)
        val args = Args(rest.drop(targetIdx + 1).toTypedArray())
        val timeoutSecs = args.longFlag("timeout", 30)
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            while (System.currentTimeMillis() < deadline) {
                ctx.syncIncoming(timeoutMs = 3_000)
                val hit = check(ctx, rest)
                if (hit != null) {
                    Json.writeLine(hit)
                    return 0
                }
                delay(1_500)
            }
            throw AwaitTimeout("condition never satisfied within ${timeoutSecs}s ($usage)")
        } finally {
            ctx.close()
        }
    }
}
