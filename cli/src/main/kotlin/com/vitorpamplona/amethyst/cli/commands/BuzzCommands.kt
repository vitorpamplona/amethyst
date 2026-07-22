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
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetAggregator
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.AttestationConditions
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * `amy buzz …` — first-class access to the `block/buzz` workspace protocol, driving the
 * same `quartz` models + `commons` aggregator the app uses. Buzz workspaces are NIP-29
 * groups, so join/leave/create still go through `amy relaygroup`; this verb group covers
 * the Buzz-native pieces: stream messages (40002), the owner-attestation primitive (OA),
 * and the agent console (turn-metric aggregation + personas).
 */
object BuzzCommands {
    private val USAGE =
        """
        |amy buzz post RELAY GID <text>              post a kind-40002 stream message
        |amy buzz read RELAY GID [--limit N]         read recent workspace messages (9/40002/40099)
        |    [--timeout SECS]
        |amy buzz attest AGENT [--kind K]            issue a NIP-OA attestation (offline; prints the auth tag)
        |    [--after UNIX] [--before UNIX]
        |amy buzz console [--relays R,R]             decrypt + aggregate my kind-44200 turn metrics
        |    [--timeout SECS]
        |amy buzz personas [--relays R,R]            list my kind-30175 personas
        |    [--timeout SECS]
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "buzz",
            tail,
            USAGE,
            mapOf(
                "post" to { rest -> post(dataDir, rest) },
                "read" to { rest -> read(dataDir, rest) },
                "attest" to { rest -> attest(dataDir, rest) },
                "console" to { rest -> console(dataDir, rest) },
                "personas" to { rest -> personas(dataDir, rest) },
            ),
        )

    /** `buzz post RELAY GID <text>` → publishes a kind-40002 stream message with an `h` tag. */
    private suspend fun post(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val usage = "buzz post RELAY GID <text>"
        val text = Args(rest).positionalOrNull(2) ?: return Output.error("bad_args", usage)
        if (text.isBlank()) return Output.error("bad_args", "message text must not be blank")
        return publishScoped(dataDir, rest, usage) { _, groupId, _ ->
            StreamMessageV2Event.build(groupId, text)
        }
    }

    /** `buzz read RELAY GID [--limit N] [--timeout SECS]` → drains the recent human-visible timeline. */
    private suspend fun read(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz read RELAY GID [--limit N] [--timeout SECS]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val groupId = args.positionalOrNull(1) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val limit = args.flag("limit")?.toIntOrNull() ?: 50
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("limit", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val filter =
                Filter(
                    kinds = listOf(ChatEvent.KIND, StreamMessageV2Event.KIND, SystemMessageEvent.KIND),
                    tags = mapOf("h" to listOf(groupId)),
                    limit = limit,
                )
            val messages =
                ctx
                    .drain(mapOf(relay to listOf(filter)), timeoutSecs * 1000)
                    .map { it.second }
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map {
                        mapOf(
                            "id" to it.id,
                            "kind" to it.kind,
                            "author" to it.pubKey,
                            "created_at" to it.createdAt,
                            "content" to it.content,
                        )
                    }
            Output.emit(mapOf("group_id" to groupId, "relay" to relay.url, "count" to messages.size, "messages" to messages))
            return 0
        }
    }

    /**
     * `buzz attest AGENT [--kind K] [--after T] [--before T]` → signs a NIP-OA
     * [OwnerAttestation] authorizing AGENT and prints the `auth` tag. Offline (the
     * signature covers a hashed commitment, not an event) and needs a local private key.
     */
    private suspend fun attest(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz attest AGENT [--kind K] [--after UNIX] [--before UNIX]"
        val agentInput = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val agentHex =
            decodePublicKeyAsHexOrNull(agentInput.trim())?.takeIf { it.isValid() }
                ?: return Output.error("bad_args", "invalid agent public key (npub or 64-char hex): $agentInput")
        val kind = args.flag("kind")?.let { it.toIntOrNull() ?: return Output.error("bad_args", "kind must be an integer") }
        val after = args.flag("after")?.let { it.toLongOrNull() ?: return Output.error("bad_args", "after must be a unix time") }
        val before = args.flag("before")?.let { it.toLongOrNull() ?: return Output.error("bad_args", "before must be a unix time") }
        args.rejectUnknown("kind", "after", "before")

        Context.open(dataDir).use { ctx ->
            val keyPair = ctx.identity.keyPair()
            if (keyPair.privKey == null) return Output.error("no_private_key", "attestation signing needs a local private key")
            val conditions = AttestationConditions(kind = kind, createdAtBefore = before, createdAtAfter = after)
            val attestation =
                try {
                    OwnerAttestation.sign(agentHex, conditions, keyPair)
                } catch (e: IllegalArgumentException) {
                    return Output.error("bad_args", e.message ?: "could not sign the attestation")
                }
            Output.emit(
                mapOf(
                    "agent" to agentHex,
                    "owner" to attestation.ownerPubKey,
                    "conditions" to attestation.conditions.ifEmpty { null },
                    "sig" to attestation.sig,
                    "auth_tag" to attestation.toTag().toList(),
                ),
            )
            return 0
        }
    }

    /**
     * `buzz console [--relays R,R] [--timeout SECS]` → fetches my kind-44200 turn metrics
     * (`#p` = me), NIP-44-decrypts them, and aggregates fleet + per-agent cost via the same
     * [AgentFleetAggregator] the app's Agent Console uses.
     */
    private suspend fun console(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relaysFlag = args.flag("relays")
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 15
        args.rejectUnknown("relays", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val relays = relaysFor(ctx, relaysFlag)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays: pass --relays ws://…")

            val filter = Filter(kinds = listOf(AgentTurnMetricEvent.KIND), tags = mapOf("p" to listOf(me)))
            val decrypted =
                ctx
                    .drainAllPages(relays.associateWith { listOf(filter) }, timeoutSecs * 1000)
                    .map { it.second }
                    .filterIsInstance<AgentTurnMetricEvent>()
                    .distinctBy { it.id }
                    .mapNotNull { e -> e.decryptOrNull(ctx.signer)?.let { (e.agentPubKey() ?: e.pubKey) to it } }
            val metrics = AgentFleetAggregator.aggregate(decrypted)

            Output.emit(
                mapOf(
                    "total_cost_usd" to metrics.totals.costUsd,
                    "total_tokens" to metrics.totals.totalTokens,
                    "input_tokens" to metrics.totals.inputTokens,
                    "output_tokens" to metrics.totals.outputTokens,
                    "turns" to metrics.totalTurns,
                    "sessions" to metrics.totalSessions,
                    "agents" to metrics.agents.size,
                    "estimated" to metrics.hasUnreliableEstimates,
                    "breakdown" to
                        metrics.agents.map {
                            mapOf(
                                "agent" to it.agentPubKey,
                                "cost_usd" to it.totals.costUsd,
                                "tokens" to it.totals.totalTokens,
                                "turns" to it.turns,
                                "sessions" to it.sessions,
                                "models" to it.models.sorted(),
                                "last_activity" to it.lastActivity,
                            )
                        },
                ),
            )
            return 0
        }
    }

    /** `buzz personas [--relays R,R] [--timeout SECS]` → lists my kind-30175 persona definitions. */
    private suspend fun personas(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relaysFlag = args.flag("relays")
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 15
        args.rejectUnknown("relays", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val relays = relaysFor(ctx, relaysFlag)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays: pass --relays ws://…")

            val filter = Filter(kinds = listOf(PersonaEvent.KIND), authors = listOf(me))
            val personas =
                ctx
                    .drainAllPages(relays.associateWith { listOf(filter) }, timeoutSecs * 1000)
                    .map { it.second }
                    .filterIsInstance<PersonaEvent>()
                    // Newest per addressable slug (replaceable): keep the latest for each d tag.
                    .groupBy { it.slug() }
                    .values
                    .mapNotNull { versions -> versions.maxByOrNull { it.createdAt } }
                    .sortedBy { it.personaOrNull()?.displayName ?: it.slug() ?: "" }
                    .map {
                        val content = it.personaOrNull()
                        mapOf(
                            "slug" to it.slug(),
                            "display_name" to content?.displayName,
                            "model" to content?.model,
                            "runtime" to content?.runtime,
                            "provider" to content?.provider,
                        )
                    }
            Output.emit(mapOf("count" to personas.size, "personas" to personas))
            return 0
        }
    }

    /** The `--relays` set if given, else the account's outbox relays. */
    private suspend fun relaysFor(
        ctx: Context,
        relaysFlag: String?,
    ) = relaysFlag
        ?.split(",")
        ?.mapNotNull { normalizeGroupRelay(it) }
        ?.toSet()
        ?: ctx.outboxRelays()
}
