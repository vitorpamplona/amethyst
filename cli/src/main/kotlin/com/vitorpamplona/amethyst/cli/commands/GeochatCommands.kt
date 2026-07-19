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
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayCsvLoader
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.experimental.bitchat.identity.GeohashKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `amy geochat <listen|send|keys>` — Bitchat-interoperable public geohash chat
 * (location channels) over Nostr.
 *
 * Messages are ephemeral kind-20000 events tagged `["g", geohash]`, signed with a
 * per-geohash throwaway identity, and routed to the relays geographically nearest
 * the cell (the [GeoRelayDirectory]). This verb drives the same quartz + commons
 * code the apps use, so it doubles as an interop harness against a real Bitchat
 * client: run `listen` in one place, `send` (or a Bitchat device) in another.
 *
 * Because the events are ephemeral, relays broadcast them live but do not store
 * them — `listen` therefore holds an open subscription for `--seconds` and reports
 * everything that arrives in that window.
 */
object GeochatCommands {
    private const val DEFAULT_LISTEN_SECONDS = 30L
    private const val DEFAULT_LIMIT = 50
    private const val DEFAULT_POW_TIMEOUT_SECS = 5L

    val USAGE: String =
        """
        |amy geochat — Bitchat-interoperable public geohash chat (ephemeral kind:20000)
        |
        |  geochat listen GEOHASH [--seconds N]        hold a live subscription to the cell and report
        |          [--limit N] [--relay URL[,URL…]]     messages + present pubkeys seen in the window
        |          [--no-fetch]                         (default --seconds 30, --limit 50)
        |  geochat send GEOHASH MESSAGE [--nick NAME]  sign with the per-geohash throwaway identity and
        |          [--teleport] [--pow BITS]            publish to the cell's nearest relays
        |          [--pow-timeout SECS] [--seed HEX]
        |          [--relay URL[,URL…]] [--no-fetch]
        |  geochat keys GEOHASH [--seed HEX]           print the per-geohash derived pubkey
        |
        |  --relay accepts a comma-separated list; bare wss://… positionals are
        |  also accepted for back-compat. --no-fetch skips the geo-relay
        |  directory refresh.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "geochat",
            tail,
            "geochat <listen|send|keys> …",
            mapOf(
                "listen" to { rest -> listen(dataDir, rest) },
                "send" to { rest -> send(dataDir, rest) },
                "keys" to { rest -> keys(rest) },
            ),
            help = USAGE,
        )

    /**
     * `amy geochat listen <geohash> [--seconds N] [--limit N] [--relay wss://… ...] [--no-fetch]`
     * Holds a live subscription to kinds 20000/20001 for the cell and reports the
     * chat messages + distinct present pubkeys seen in the window.
     */
    private suspend fun listen(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val geohash =
            args.positional.firstOrNull()?.lowercase()
                ?: return Output.error("bad_args", "geochat listen <geohash> [--seconds N] [--limit N] [--relay …] [--no-fetch]")
        if (!isGeohash(geohash)) return Output.error("bad_args", "not a geohash: $geohash")

        val seconds = args.longFlag("seconds", DEFAULT_LISTEN_SECONDS)
        val limit = args.intFlag("limit", DEFAULT_LIMIT)
        val relays = resolveRelays(args, geohash)
        args.rejectUnknown()
        if (relays.isEmpty()) return Output.error("no_relays", "no relays for geohash $geohash (directory empty / bad --relay)")

        val since = TimeUtils.now() - seconds.coerceAtLeast(1)
        val filter = Filter(kinds = listOf(GeohashChatEvent.KIND, GeohashPresenceEvent.KIND), tags = mapOf("g" to listOf(geohash)), since = since, limit = limit)

        val collected = CopyOnWriteArrayList<Event>()
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val subId = newSubId()
            val listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event.verify()) collected.add(event)
                    }
                }
            try {
                ctx.client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
                delay(seconds * 1000)
            } finally {
                ctx.client.unsubscribe(subId)
            }
        }

        // Dedup by id (the same event can arrive from several relays).
        val unique = collected.associateBy { it.id }.values
        val messages =
            unique
                .filterIsInstance<GeohashChatEvent>()
                .sortedBy { it.createdAt }
                .map { messageJson(it) }
        val presentPubkeys = unique.filter { it.kind == GeohashChatEvent.KIND || it.kind == GeohashPresenceEvent.KIND }.map { it.pubKey }.toSet()

        Output.emit(
            mapOf(
                "geohash" to geohash,
                "relays" to relays.map { it.url },
                "listened_seconds" to seconds,
                "participants" to presentPubkeys.size,
                "message_count" to messages.size,
                "messages" to messages,
            ),
        )
        return 0
    }

    /**
     * `amy geochat send <geohash> <message> [--nick NAME] [--teleport] [--pow BITS]
     *   [--seed HEX] [--relay wss://… ...] [--no-fetch]`
     * Signs a kind-20000 message with the per-geohash ephemeral identity (derived
     * from [--seed], or a random one for this run) and publishes it to the cell's
     * relays.
     */
    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val geohash = args.positional.getOrNull(0)?.lowercase()
        val message = args.positional.getOrNull(1)
        if (geohash == null || message == null) {
            return Output.error("bad_args", "geochat send <geohash> <message> [--nick NAME] [--teleport] [--pow BITS] [--seed HEX] [--relay …] [--no-fetch]")
        }
        if (!isGeohash(geohash)) return Output.error("bad_args", "not a geohash: $geohash")

        val seed = resolveSeed(args) ?: return Output.error("bad_args", "--seed must be 64 hex chars (32 bytes)")
        val keyPair = GeohashKeyDerivation.deriveKeyPair(seed, geohash)
        val signer = NostrSignerSync(keyPair)

        val nick = args.flag("nick")?.takeIf { it.isNotBlank() }
        val teleported = args.bool("teleport")
        val powBits = args.intFlag("pow", 0)

        var template = GeohashChatEvent.build(message, geohash, nickname = nick, teleported = teleported)
        if (powBits > 0) {
            val deadline = System.nanoTime() + args.longFlag("pow-timeout", DEFAULT_POW_TIMEOUT_SECS) * 1_000_000_000L
            template =
                withContext(Dispatchers.Default) {
                    PoWMiner.mine(template, keyPair.pubKey.toHexKey(), powBits, defaultThreads()) { System.nanoTime() < deadline }
                }
        }
        val event = signer.sign(template)

        val relays = resolveRelays(args, geohash)
        if (relays.isEmpty()) return Output.error("no_relays", "no relays for geohash $geohash (directory empty / bad --relay)")
        // --pow-timeout is only read when --pow is set; whitelist it either way.
        args.rejectUnknown("pow-timeout")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val acks = ctx.publish(event, relays.toSet())
            RawEventSupport.publishGuard(acks, event.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to event.id,
                    "author" to event.pubKey,
                    "geohash" to geohash,
                    "nickname" to nick,
                    "teleported" to teleported,
                    "pow" to PoWRankEvaluator.calculatePowRankOf(event.id),
                    "content" to message,
                ) + RawEventSupport.ackFields(acks),
            )
        }
        return 0
    }

    /**
     * `amy geochat keys <geohash> [--seed HEX]` — the per-geohash pubkey derived
     * for the (seed, geohash) pair. Useful to check identity stability or to
     * address a geohash DM.
     */
    private fun keys(rest: Array<String>): Int {
        val args = Args(rest)
        val geohash = args.positional.firstOrNull()?.lowercase() ?: return Output.error("bad_args", "geochat keys <geohash> [--seed HEX]")
        if (!isGeohash(geohash)) return Output.error("bad_args", "not a geohash: $geohash")
        val seed = resolveSeed(args) ?: return Output.error("bad_args", "--seed must be 64 hex chars (32 bytes)")
        args.rejectUnknown()
        val keyPair = GeohashKeyDerivation.deriveKeyPair(seed, geohash)
        Output.emit(
            mapOf(
                "geohash" to geohash,
                "pubkey" to keyPair.pubKey.toHexKey(),
                "seed" to seed.toHexKey(),
            ),
        )
        return 0
    }

    // ------------------------------------------------------------------

    /**
     * Explicit `--relay URL[,URL…]` list wins (strictly validated — a bad entry
     * is a `bad_args` failure, like every other `--relay` in amy); bare
     * `wss://…` positionals are still accepted for back-compat. Otherwise the
     * closest relays from the (optionally refreshed) directory.
     */
    private suspend fun resolveRelays(
        args: Args,
        geohash: String,
    ): List<NormalizedRelayUrl> {
        // Read eagerly so `--relay X --no-fetch` doesn't trip rejectUnknown()
        // when the explicit-relay early return skips the directory branch.
        val noFetch = args.bool("no-fetch")
        val explicit = RawEventSupport.relayFlag(args).toList()
        val allExplicit = (explicit + args.positional.mapNotNull { if (it.startsWith("wss://") || it.startsWith("ws://")) RelayUrlNormalizer.normalizeOrNull(it) else null })
        if (allExplicit.isNotEmpty()) return allExplicit.distinct()

        val directory = GeoRelayDirectory()
        if (!noFetch) {
            runCatching { GeoRelayCsvLoader { OkHttpClient() }.refresh(directory) }
        }
        return directory.closestRelays(geohash)
    }

    /** `--seed HEX` (32 bytes), else a fresh random per-run seed. */
    private fun resolveSeed(args: Args): ByteArray? {
        val hex = args.flag("seed") ?: return RandomInstance.bytes(GeohashKeyDerivation.SEED_SIZE)
        if (hex.length != GeohashKeyDerivation.SEED_SIZE * 2 || !Hex.isHex(hex)) return null
        return hex.hexToByteArray()
    }

    private fun messageJson(event: GeohashChatEvent): Map<String, Any?> =
        mapOf(
            "event_id" to event.id,
            "author" to event.pubKey,
            "nickname" to event.nickname(),
            "teleported" to event.isTeleported(),
            "content" to event.content,
            "created_at" to event.createdAt,
            "pow" to PoWRankEvaluator.calculatePowRankOf(event.id),
        )

    private fun isGeohash(s: String): Boolean = s.isNotEmpty() && s.length <= 12 && s.all { it in GEOHASH_ALPHABET }

    private fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private const val GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"
}
