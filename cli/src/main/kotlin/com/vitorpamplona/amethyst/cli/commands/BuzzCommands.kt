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
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.AttestationConditions
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
        |amy buzz join <invite-url> [--accept-policy]  redeem a Buzz invite link (HTTP claim, NIP-98)
        |amy buzz post RELAY GID <text>              post a kind-40002 stream message
        |amy buzz read RELAY GID [--limit N]         read recent workspace messages (9/40002/40099)
        |    [--timeout SECS]
        |amy buzz attest AGENT [--kind K]            issue a NIP-OA attestation (offline; prints the auth tag)
        |    [--after UNIX] [--before UNIX]
        |amy buzz console [--relays R,R]             decrypt + aggregate my kind-44200 turn metrics
        |    [--timeout SECS]
        |amy buzz personas [--relays R,R]            list my kind-30175 personas
        |    [--timeout SECS]
        |amy buzz dm list [--relays R,R]             list my DMs (44100 member-added + 40099 dm_created)
        |    [--limit N] [--timeout SECS]
        |amy buzz dm open RELAY PUBKEY [PUBKEY…]     open a DM with 1-8 people (kind-41010)
        |amy buzz dm hide RELAY CHANNEL              hide a DM from my sidebar (kind-41012)
        |amy buzz dm add-member RELAY CHANNEL PUBKEY add a member to a group DM (kind-41011)
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
                "join" to { rest -> join(dataDir, rest) },
                "post" to { rest -> post(dataDir, rest) },
                "read" to { rest -> read(dataDir, rest) },
                "attest" to { rest -> attest(dataDir, rest) },
                "console" to { rest -> console(dataDir, rest) },
                "personas" to { rest -> personas(dataDir, rest) },
                "dm" to { rest -> dm(dataDir, rest) },
            ),
        )

    /** `buzz dm …` — the Buzz direct-message sub-verbs (list / open / hide / add-member). */
    private suspend fun dm(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int {
        val usage =
            """
            |amy buzz dm list [--relays R,R] [--limit N] [--timeout SECS]
            |amy buzz dm open RELAY PUBKEY [PUBKEY…]
            |amy buzz dm hide RELAY CHANNEL
            |amy buzz dm add-member RELAY CHANNEL PUBKEY
            """.trimMargin()
        return route(
            "buzz dm",
            tail,
            usage,
            mapOf(
                "list" to { rest -> dmList(dataDir, rest) },
                "open" to { rest -> dmOpen(dataDir, rest) },
                "hide" to { rest -> dmHide(dataDir, rest) },
                "add-member" to { rest -> dmAddMember(dataDir, rest) },
            ),
        )
    }

    /** `buzz dm list` → discovers my DM channels via kind-44100 member-added notifications, filtered to the kind-40099 `dm_created` ones. */
    private suspend fun dmList(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relaysFlag = args.flag("relays")
        val limit = args.flag("limit")?.toIntOrNull() ?: 50
        val timeoutSecs = args.flag("timeout")?.toLongOrNull() ?: 8
        args.rejectUnknown("relays", "limit", "timeout")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val me = ctx.identity.pubKeyHex
            val relays = relaysFor(ctx, relaysFlag)
            if (relays.isEmpty()) return Output.error("no_relays", "no relays: pass --relays ws://…")

            // The deployed Buzz relay does NOT emit kind-41001; instead it (a) confirms a DM's
            // channel id synchronously in the open OK, and (b) addresses each member a kind-44100
            // member-added notification (`#p` = me, `h` = channel). So discover the channels I'm in
            // via 44100, then keep those whose kind-40099 system message marks them a DM.
            val memberFilter = Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(me)))
            val channelIds =
                ctx
                    .drain(relays.associateWith { listOf(memberFilter) }, timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
                    .filterIsInstance<MemberAddedNotificationEvent>()
                    .mapNotNull { it.channel() }
                    .distinct()
            if (channelIds.isEmpty()) {
                Output.emit(mapOf("count" to 0, "dms" to emptyList<Any>()))
                return 0
            }

            val sysFilter = Filter(kinds = listOf(SystemMessageEvent.KIND), tags = mapOf("h" to channelIds))
            val dms =
                ctx
                    .drain(relays.associateWith { listOf(sysFilter) }, timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
                    .filterIsInstance<SystemMessageEvent>()
                    .filter { it.payload()?.type == "dm_created" }
                    .distinctBy { it.channel() }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
                    .map { sys ->
                        // The relay's dm_created content carries a `participants` array our
                        // SystemMessagePayload model drops; read it from the raw content.
                        val participants =
                            runCatching {
                                jsonParser
                                    .parseToJsonElement(sys.content)
                                    .jsonObject["participants"]
                                    ?.let { arr ->
                                        arr
                                            .toString()
                                            .trim('[', ']')
                                            .split(",")
                                            .map { it.trim().trim('"') }
                                            .filter { it.isNotBlank() }
                                    }
                            }.getOrNull().orEmpty()
                        mapOf(
                            "dm_id" to sys.channel(),
                            "participants" to participants,
                            "created_at" to sys.createdAt,
                        )
                    }
            Output.emit(mapOf("count" to dms.size, "dms" to dms))
            return 0
        }
    }

    /** `buzz dm open RELAY PUBKEY [PUBKEY…]` → publishes a kind-41010 with 1-8 `p` participants. */
    private suspend fun dmOpen(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz dm open RELAY PUBKEY [PUBKEY…]"
        val relayUrl = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val relay = normalizeGroupRelay(relayUrl) ?: return Output.error("bad_args", "invalid relay url: $relayUrl")

        val participants = mutableListOf<HexKey>()
        var i = 1
        while (true) {
            val raw = args.positionalOrNull(i) ?: break
            val hex =
                decodePublicKeyAsHexOrNull(raw.trim())?.takeIf { it.isValid() }
                    ?: return Output.error("bad_args", "invalid public key (npub or 64-char hex): $raw")
            if (hex !in participants) participants.add(hex)
            i++
        }
        if (participants.size !in DmOpenEvent.MIN_PARTICIPANTS..DmOpenEvent.MAX_PARTICIPANTS) {
            return Output.error("bad_args", "a DM needs ${DmOpenEvent.MIN_PARTICIPANTS}-${DmOpenEvent.MAX_PARTICIPANTS} participants")
        }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(DmOpenEvent.build(participants))
            val ack = ctx.publish(signed, setOf(relay))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            // The relay confirms a DM open synchronously in the OK message as
            // `response:{"channel_id":"…","created":bool}` — the authoritative, relay-assigned
            // channel UUID. Surface it so callers open the chat straight from the ack.
            val okMessage = ack.values.firstOrNull { it.accepted }?.message
            val channelId =
                okMessage
                    ?.substringAfter("response:", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        runCatching {
                            jsonParser
                                .parseToJsonElement(it)
                                .jsonObject["channel_id"]
                                ?.jsonPrimitive
                                ?.content
                        }.getOrNull()
                    }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "relay" to relay.url,
                    "participants" to participants,
                    "published" to ack.values.any { it.accepted },
                    "channel_id" to channelId,
                    "relay_message" to okMessage,
                ),
            )
            return 0
        }
    }

    /** `buzz dm hide RELAY CHANNEL` → publishes a kind-41012 scoped by the DM's `h` tag. */
    private suspend fun dmHide(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        publishScoped(dataDir, rest, "buzz dm hide RELAY CHANNEL") { _, channelId, _ ->
            DmHideEvent.build(channelId)
        }

    /** `buzz dm add-member RELAY CHANNEL PUBKEY` → publishes a kind-41011 (`h` + new `p`). */
    private suspend fun dmAddMember(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val usage = "buzz dm add-member RELAY CHANNEL PUBKEY"
        val memberInput = Args(rest).positionalOrNull(2) ?: return Output.error("bad_args", usage)
        val member =
            decodePublicKeyAsHexOrNull(memberInput.trim())?.takeIf { it.isValid() }
                ?: return Output.error("bad_args", "invalid public key (npub or 64-char hex): $memberInput")
        return publishScoped(dataDir, rest, usage) { _, channelId, _ ->
            DmAddMemberEvent.build(channelId, member)
        }
    }

    private val jsonMedia = "application/json".toMediaType()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * `buzz join <invite-url> [--accept-policy]` → redeems a Buzz workspace invite. A Buzz
     * invite is NOT a NIP-29 code; it's a relay-signed token claimed over HTTP against the
     * tenant host: optionally accept the join policy, then `POST /api/invites/claim`
     * NIP-98-signed by our key. On success we become a relay member and can REQ/publish.
     */
    private suspend fun join(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val usage = "buzz join <invite-url> [--accept-policy]"
        val urlArg = args.positionalOrNull(0) ?: return Output.error("bad_args", usage)
        val acceptPolicy = args.bool("accept-policy")
        args.rejectUnknown("accept-policy")

        val invite = BuzzInviteLink.parse(urlArg) ?: return Output.error("bad_args", "not a Buzz invite link: $urlArg")
        if (invite.isExpired(TimeUtils.now())) return Output.error("expired", "this invite has expired")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val http = ctx.okhttp

            // 1. Join policy (optional). A relay with a configured policy requires an accepted
            //    receipt before the claim; one without returns 404 and we skip straight to claim.
            var policyReceipt: String? = null
            val (policyCode, policyBody) = httpGet(http, "${invite.httpBase()}/api/join-policy")
            if (policyCode == 200) {
                val policy = jsonParser.parseToJsonElement(policyBody).jsonObject["policy"]?.jsonObject
                val version = policy?.get("version")?.jsonPrimitive?.content
                if (version != null) {
                    if (!acceptPolicy) {
                        return Output.error(
                            "policy_required",
                            "this workspace requires accepting its terms + age attestation; re-run with --accept-policy to consent",
                        )
                    }
                    // 2. Accept policy → short-lived, invite-bound receipt (no NIP-98 auth).
                    val acceptReq =
                        buildJsonObject {
                            put("code", invite.code)
                            put("policy_version", version)
                            put("age_confirmed", true)
                        }.toString()
                    val (acceptCode, acceptBody) = httpPost(http, "${invite.httpBase()}/api/invites/accept-policy", acceptReq, null)
                    if (acceptCode != 200) return Output.error("policy_failed", "accept-policy failed ($acceptCode): $acceptBody")
                    policyReceipt =
                        jsonParser
                            .parseToJsonElement(acceptBody)
                            .jsonObject["receipt"]
                            ?.jsonPrimitive
                            ?.content
                            ?: return Output.error("policy_failed", "accept-policy returned no receipt: $acceptBody")
                }
            }

            // 3. Claim — NIP-98-signed POST. The `u` tag must equal the tenant-host URL.
            val claimUrl = "${invite.httpBase()}/api/invites/claim"
            val claimReq =
                buildJsonObject {
                    put("code", invite.code)
                    policyReceipt?.let { put("policy_receipt", it) }
                }.toString()
            val authEvent = ctx.signer.sign(HTTPAuthorizationEvent.build(claimUrl, "POST", claimReq.encodeToByteArray()))
            val (claimCode, claimBody) = httpPost(http, claimUrl, claimReq, authEvent.toAuthToken())
            if (claimCode != 200) return Output.error("claim_failed", "invite claim failed ($claimCode): $claimBody")

            val result = jsonParser.parseToJsonElement(claimBody).jsonObject
            Output.emit(
                mapOf(
                    "status" to result["status"]?.jsonPrimitive?.content,
                    "community_id" to (result["community_id"]?.jsonPrimitive?.content ?: invite.communityId),
                    "role" to (result["role"]?.jsonPrimitive?.content ?: invite.role),
                    "host" to invite.host,
                    "relay" to invite.relayUrl(),
                ),
            )
            return 0
        }
    }

    private suspend fun httpGet(
        http: OkHttpClient,
        url: String,
    ): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            http
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
                .use { it.code to it.body.string() }
        }

    private suspend fun httpPost(
        http: OkHttpClient,
        url: String,
        body: String,
        auth: String?,
    ): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).post(body.toRequestBody(jsonMedia))
            if (auth != null) builder.header("Authorization", auth)
            http.newCall(builder.build()).execute().use { it.code to it.body.string() }
        }

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
                    .drain(mapOf(relay to listOf(filter)), timeoutSecs * 1000, pendingOnAuthRequired = true)
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
                    .drain(relays.associateWith { listOf(filter) }, timeoutSecs * 1000, pendingOnAuthRequired = true)
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
                    .drain(relays.associateWith { listOf(filter) }, timeoutSecs * 1000, pendingOnAuthRequired = true)
                    .map { it.second }
                    .filterIsInstance<PersonaEvent>()
                    // Newest per addressable slug (replaceable): keep the latest for each d tag.
                    .groupBy { it.slug() }
                    .values
                    .mapNotNull { versions -> versions.maxByOrNull { it.createdAt } }
                    .sortedBy { it.personaOrNull()?.displayName ?: it.slug() }
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
