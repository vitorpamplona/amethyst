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
import com.vitorpamplona.amethyst.cli.AwaitTimeout
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Json
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.delay

/**
 * NIP-17 direct-message verbs. All three reuse the Quartz gift-wrap pipeline
 * (NIP-17 chat message → NIP-59 seal → NIP-59 gift wrap via NIP-44) — this
 * command file is pure orchestration.
 */
object DmCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        if (tail.isEmpty()) return Json.error("bad_args", "dm <send|list|await> …")
        val rest = tail.drop(1).toTypedArray()
        return when (tail[0]) {
            "send" -> send(dataDir, rest)
            "list" -> list(dataDir, rest)
            "await" -> await(dataDir, rest)
            else -> Json.error("bad_args", "dm ${tail[0]}")
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Json.error("bad_args", "dm send <recipient> <text>")
        val text = rest[1]
        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val recipient = ctx.requireUserHex(rest[0])

            val template = ChatMessageEvent.build(text, listOf(PTag(recipient)))
            val result = NIP17Factory().createMessageNIP17(template, ctx.signer)

            val recipientsOut = mutableListOf<Map<String, Any?>>()
            for (wrap in result.wraps) {
                val target = wrap.recipientPubKey() ?: continue
                val relays = resolveDmRelays(ctx, target)
                if (relays.isEmpty()) {
                    // A DM needs at least one relay that either the recipient
                    // or we agree on. If we can't even fall back to bootstrap
                    // we can't say "sent" honestly — surface it as an error.
                    return Json.error("no_dm_relays", target)
                }
                val ack = ctx.publish(wrap, relays)
                recipientsOut.add(
                    mapOf(
                        "pubkey" to target,
                        "wrap_id" to wrap.id,
                        "published_to" to ack.filterValues { it }.keys.map { it.url },
                        "relays_tried" to relays.map { it.url },
                    ),
                )
            }

            Json.writeLine(
                mapOf(
                    "event_id" to result.msg.id,
                    "kind" to ChatMessageEvent.KIND,
                    "recipients" to recipientsOut,
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
        val args = Args(rest)
        val peerInput = args.flag("peer")
        val sinceFlag = args.flags["since"]?.toLongOrNull()
        val limit = args.intFlag("limit", Int.MAX_VALUE)
        val timeoutSecs = args.longFlag("timeout", 8)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peerHex = peerInput?.let { ctx.requireUserHex(it) }

            val inbox =
                ctx
                    .inboxRelays()
                    .ifEmpty { ctx.outboxRelays() }
                    .ifEmpty { ctx.bootstrapRelays() }
            if (inbox.isEmpty()) return Json.error("no_inbox_relays", "configure relays or bootstrap defaults first")

            // Stateless queries (either --since or --peer provided) don't
            // touch the cursor — the caller is asking a specific question.
            // A no-flag invocation is the "advance my cursor" path, matching
            // the Marmot syncIncoming convention.
            val advanceCursor = peerInput == null && sinceFlag == null
            val since = sinceFlag ?: ctx.state.giftWrapSince

            val filters =
                inbox
                    .flatMap { filterGiftWrapsToPubkey(it, ctx.signer.pubKey, since) }
                    .groupBy { it.relay }
                    .mapValues { (_, v) -> v.map { it.filter } }

            val raw = ctx.drain(filters, timeoutMs = timeoutSecs * 1000)

            val messages = decryptChatMessages(ctx, raw, peerHex)
            val out =
                messages
                    .sortedBy { it.createdAt }
                    .takeLast(limit)
                    .map { it.toJson() }

            if (advanceCursor) {
                val maxSeen = messages.maxOfOrNull { it.createdAt }
                if (maxSeen != null) ctx.state.giftWrapSince = maxSeen
            }

            Json.writeLine(
                mapOf(
                    "peer" to peerHex,
                    "messages" to out,
                ),
            )
            return 0
        } finally {
            ctx.close()
        }
    }

    private suspend fun await(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val peerInput = args.requireFlag("peer")
        val match = args.requireFlag("match")
        val timeoutSecs = args.longFlag("timeout", 30)

        val ctx = Context.open(dataDir)
        try {
            ctx.prepare()
            val peerHex = ctx.requireUserHex(peerInput)

            val inbox =
                ctx
                    .inboxRelays()
                    .ifEmpty { ctx.outboxRelays() }
                    .ifEmpty { ctx.bootstrapRelays() }
            if (inbox.isEmpty()) return Json.error("no_inbox_relays", "configure relays or bootstrap defaults first")

            val deadline = System.currentTimeMillis() + timeoutSecs * 1000
            // Remember how far we've already scanned on this invocation so
            // subsequent polls only pull newly-arrived wraps. The 2-day
            // lookback inside filterGiftWrapsToPubkey takes care of NIP-59's
            // randomised created_at.
            var since = ctx.state.giftWrapSince

            while (System.currentTimeMillis() < deadline) {
                val filters =
                    inbox
                        .flatMap { filterGiftWrapsToPubkey(it, ctx.signer.pubKey, since) }
                        .groupBy { it.relay }
                        .mapValues { (_, v) -> v.map { it.filter } }

                val raw = ctx.drain(filters, timeoutMs = 3_000)
                val messages = decryptChatMessages(ctx, raw, peerHex)
                val hit = messages.firstOrNull { it.content.contains(match) }
                if (hit != null) {
                    Json.writeLine(hit.toJson())
                    return 0
                }
                val maxSeen = messages.maxOfOrNull { it.createdAt }
                if (maxSeen != null && (since == null || maxSeen > since)) since = maxSeen
                delay(2_000)
            }
            throw AwaitTimeout("no DM from $peerHex matching '$match' within ${timeoutSecs}s")
        } finally {
            ctx.close()
        }
    }

    /** Where to deliver a gift-wrap addressed to [recipient]. */
    private suspend fun resolveDmRelays(
        ctx: Context,
        recipient: HexKey,
    ): Set<NormalizedRelayUrl> {
        val seed = ctx.bootstrapRelays()
        val lists = RecipientRelayFetcher.fetchRelayLists(ctx.client, recipient, seed)
        // 10050 inbox → 10002 read fallback → bootstrap as final safety net.
        return lists.dmInboxOrFallback().toSet().ifEmpty { seed }
    }

    private data class DecryptedDm(
        val id: HexKey,
        val wrapId: HexKey,
        val from: HexKey,
        val to: List<HexKey>,
        val content: String,
        val createdAt: Long,
        val relay: String,
    ) {
        fun toJson(): Map<String, Any?> =
            mapOf(
                "id" to id,
                "wrap_id" to wrapId,
                "from" to from,
                "to" to to,
                "content" to content,
                "created_at" to createdAt,
                "relay" to relay,
            )
    }

    private suspend fun decryptChatMessages(
        ctx: Context,
        raw: List<Pair<NormalizedRelayUrl, com.vitorpamplona.quartz.nip01Core.core.Event>>,
        peerHex: HexKey?,
    ): List<DecryptedDm> {
        val seen = HashSet<HexKey>()
        val out = mutableListOf<DecryptedDm>()
        for ((relay, event) in raw) {
            if (event !is GiftWrapEvent) continue
            val sealed = event.unwrapOrNull(ctx.signer) as? SealedRumorEvent ?: continue
            val inner = sealed.unsealOrNull(ctx.signer) as? ChatMessageEvent ?: continue
            if (!seen.add(inner.id)) continue
            val members = inner.groupMembers()
            if (peerHex != null && peerHex !in members) continue
            out.add(
                DecryptedDm(
                    id = inner.id,
                    wrapId = event.id,
                    from = inner.pubKey,
                    to = inner.recipientsPubKey(),
                    content = inner.content,
                    createdAt = inner.createdAt,
                    relay = relay.url,
                ),
            )
        }
        return out
    }
}
