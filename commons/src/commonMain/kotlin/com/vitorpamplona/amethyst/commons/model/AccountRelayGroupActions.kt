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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHuddleLivenessState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunPayload
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupDeletions
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.buzz.dm.BuzzDmRules
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.dm.dmHistory
import com.vitorpamplona.quartz.buzz.dm.tags.DmHistoryTag
import com.vitorpamplona.quartz.buzz.huddles.HuddleLivenessEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.ChannelPinsEvent
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.PinMessageEvent
import com.vitorpamplona.quartz.buzz.pmPinnedMessages.UnpinMessageEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.workflowChannel
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_FORUM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_CHANNEL_TYPE_STREAM
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_VISIBILITY_OPEN
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_VISIBILITY_PRIVATE
import com.vitorpamplona.quartz.buzz.workspace.buzzRoleFor
import com.vitorpamplona.quartz.buzz.workspace.canonicalBuzzChannelName
import com.vitorpamplona.quartz.buzz.workspace.newBuzzChannelId
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAwaitingOkThroughAuth
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.warmUpAuth
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-29 relay-group and Buzz-workspace orchestration for an [Account]:
 * join/leave/create/delete/archive groups, threads, invites, pins, member and
 * role management, metadata edits, plus the Buzz dialect's DMs, jobs,
 * workflows, and typing signals. Event building lives in quartz builders;
 * this class wires them to the account's signer and the group's host relay.
 */
class AccountRelayGroupActions(
    private val account: Account,
) {
    // All group commands are published ONLY to the group's host relay, where
    // relay29 authorizes them. The relay is the source of truth; the kind-10009
    // list is our own cross-device bookkeeping of what we joined.

    /** Send a kind 9021 join request to the group's host relay and remember it. */
    suspend fun joinRelayGroup(
        channel: RelayGroupChannel,
        code: String? = null,
    ) {
        val template = GroupJoinRequestEvent.build(channel.groupId.id, inviteCode = code)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.follow(channel)
    }

    // "relay|profile content" pairs already pushed this session, so each profile version goes to each
    // workspace once. Keyed on content, not event id: the copy we sign comes back from the relay as a
    // newer kind-0 with the same content, and must not trigger another signature.
    private val profileSharedWith = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Push my current kind-0 profile to the Buzz workspace at [relay]. Buzz reads member names and
     * pictures only from its own relay, and its people search (mentions, Add people, new DM) is by
     * name — without this, Buzz showed me as a bare npub and nobody could find or add me.
     *
     * Buzz refuses an event whose timestamp is far from its clock ("event timestamp too far from
     * server time"), so the already-signed profile — usually days old — can't be relayed as-is. Like
     * Buzz's own client, this signs a fresh copy with the same content and tags, and sends it ONLY to
     * that workspace, once per profile version.
     */
    suspend fun shareProfileWithBuzzWorkspace(relay: NormalizedRelayUrl) {
        if (!account.isWriteable()) return
        val profile = account.userMetadata.getUserMetadataEvent() ?: return
        val key = "${relay.url}|${profile.content.hashCode()}|${profile.tags.contentDeepHashCode()}"
        while (true) {
            val current = profileSharedWith.value
            if (key in current) return
            if (profileSharedWith.compareAndSet(current, current + key)) break
        }
        try {
            val fresh =
                if (TimeUtils.now() - profile.createdAt < BUZZ_FRESH_PROFILE_SECS) {
                    profile
                } else {
                    account.signer.sign<MetadataEvent>(TimeUtils.now(), MetadataEvent.KIND, profile.tags, profile.content)
                }
            account.client.publish(fresh, setOf(relay))
        } catch (e: Throwable) {
            // Not shared (signer refused, timed out, …): forget the attempt so a later visit retries.
            profileSharedWith.update { it - key }
            throw e
        }
    }

    /**
     * Fire a Buzz kind-20002 typing heartbeat for [channel] to its host relay. Ephemeral
     * (never stored) and fire-and-forget — no delivery tracking, no local echo (we filter
     * our own typing in the UI). Throttled by the composer to [BuzzTypingState.TYPING_HEARTBEAT_SECS].
     * [threadRootId]/[replyToId] scope it to a thread, as Buzz's thread composer does.
     */
    suspend fun sendBuzzTyping(
        channel: RelayGroupChannel,
        threadRootId: HexKey? = null,
        replyToId: HexKey? = null,
    ) {
        if (!account.isWriteable()) return
        // In a thread, the `e` markers scope the signal to it: Buzz shows it in that thread's pane.
        val signed = account.signer.sign(TypingIndicatorEvent.build(channel.groupId.id, threadRootId, replyToId))
        account.client.publish(signed, setOf(channel.groupId.relayUrl))
    }

    /**
     * Open (or re-surface) a Buzz DM with [participants] on [relay] via a kind-41010
     * command. [participants] are the OTHER 1-8 people — the relay adds me, derives the
     * canonical channel UUID, and confirms with a relay-signed [DmCreatedEvent]
     * (kind-41001) that lands in [com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry].
     * We never assign the channel id ourselves, so callers discover the materialized DM
     * by watching that registry rather than from this call's return.
     */
    suspend fun openBuzzDm(
        relay: NormalizedRelayUrl,
        participants: List<HexKey>,
    ): String? {
        // Not DmOpenEvent.build: it stops at 8 others, but agents don't count toward Buzz's cap.
        val signed = account.signer.sign(BuzzDmRules.openTemplate(participants))
        // The relay confirms the DM synchronously in the OK as `response:{"channel_id":"…"}` —
        // the authoritative, relay-assigned channel UUID (the deployed relay does not emit a
        // queryable kind-41001). Read it straight from the ack so the caller can open the chat.
        return buzzDmChannelIdFromAck(publishAwaitingOk(signed, relay))
    }

    /**
     * Publishes [signed] to [relay] alone and waits up to [timeoutSecs] for its OK, getting past a
     * cold socket's NIP-42 `auth-required:` ([publishAwaitingOkThroughAuth]). The warm-up is a
     * pendingOnAuthRequired read of [warmUpKind], so the auth coordinator completes the handshake.
     */
    private suspend fun publishAwaitingOk(
        signed: Event,
        relay: NormalizedRelayUrl,
        timeoutSecs: Long = OK_TIMEOUT_SECS,
        warmUpKind: Int = DmOpenEvent.KIND,
    ): Map<NormalizedRelayUrl, PublishResult> {
        // Same delivery tracking as every other group command, so the chat shows it as sent.
        account.chatDeliveryTracker.trackPublic(signed.id, setOf(relay))
        return account.client.publishAwaitingOkThroughAuth(signed, setOf(relay), timeoutSecs) { needAuth ->
            account.client.taggedAs(SubPurpose.RELAY_GROUPS).warmUpAuth(needAuth, warmUpKind, AUTH_WARM_UP_MS)
        }
    }

    /** The relay-assigned DM channel id from a DM command's OK message (`response:{"channel_id":"…"}`). */
    private fun buzzDmChannelIdFromAck(results: Map<NormalizedRelayUrl, PublishResult>): String? = BuzzDmRules.channelIdFromOk(results.values.firstOrNull { it.accepted }?.message)

    /**
     * Asks [channel]'s Buzz relay which huddle sessions in it are live right now and records them
     * in [BuzzHuddleLivenessState]. The relay synthesizes kind-48104 answers only for a REQ whose
     * every filter is exactly `kinds:[48104]` with an authorized `#h`, so this is its own REQ, never
     * part of the channel's timeline subscription. Buzz's desktop repeats it every 10 seconds.
     */
    suspend fun refreshBuzzHuddleLiveness(channel: RelayGroupChannel) {
        val filter = HuddleLivenessEvent.filter(listOf(channel.groupId.id))
        val now = TimeUtils.now()
        account.client.taggedAs(SubPurpose.RELAY_GROUPS, "Huddle liveness").fetchAll(channel.groupId.relayUrl, filter, idleTimeoutMs = 8_000).forEach { event ->
            if (event !is HuddleLivenessEvent || event.channelId() != channel.groupId.id) return@forEach
            event.sessionId()?.let { BuzzHuddleLivenessState.record(channel.groupId.id, it, now) }
        }
    }

    /** Hide a Buzz DM from my sidebar with a kind-41012 command (re-opening it un-hides). */
    suspend fun hideBuzzDm(channel: RelayGroupChannel) {
        val template = DmHideEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [member] to the Buzz DM [channel] with a kind-41011 command.
     *
     * [history] says what to do with the DM's earlier messages (see [BuzzDmRules] and
     * [DmHistoryTag]). [BuzzDmHistory.ASK], the default, lets the relay decide when there is nothing
     * to decide (a DM of one person forks a new DM without history; people who already share a DM
     * land there) and answer [BuzzDmAddResult.NeedsHistoryChoice] when the newcomer would join this
     * conversation in place, so the caller asks the user and sends again with [BuzzDmHistory.SHARE]
     * or [BuzzDmHistory.DONT_SHARE]. An agent account may not send the tag: the relay's refusal of
     * the default [BuzzDmHistory.ASK] is answered by sending the add again untagged, while an explicit
     * [BuzzDmHistory.SHARE] keeps the refusal instead of quietly not sharing (as `amy` does).
     */
    suspend fun addBuzzDmMember(
        channel: RelayGroupChannel,
        member: HexKey,
        history: BuzzDmHistory = BuzzDmHistory.ASK,
    ): BuzzDmAddResult {
        val relay = channel.groupId.relayUrl
        val signed =
            account.signer.sign(
                DmAddMemberEvent.build(channel.groupId.id, member) {
                    when (history) {
                        BuzzDmHistory.ASK -> dmHistory(DmHistoryTag.ASK)
                        BuzzDmHistory.SHARE -> dmHistory(DmHistoryTag.INCLUDE)
                        BuzzDmHistory.DONT_SHARE -> {}
                    }
                },
            )
        account.cache.justConsumeMyOwnEvent(signed)
        val result = buzzDmAddResult(publishAwaitingOk(signed, relay), relay, fromChannelId = channel.groupId.id)
        if (result is BuzzDmAddResult.Refused && result.message == ONLY_PEOPLE_SHARE_HISTORY && history == BuzzDmHistory.ASK) {
            return addBuzzDmMember(channel, member, BuzzDmHistory.DONT_SHARE)
        }
        return result
    }

    /**
     * File a Buzz agent job (kind-43001) into channel [channelId] on [relay] — a shared
     * feature-request the workspace bot can pick up. Untargeted: any agent watching the
     * channel may accept it. Returns the new job id (the request event id), or null when the
     * account can't write. See [com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator].
     */
    suspend fun fileBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        request: String,
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(JobRequestEvent.build(request, channelId, null))
        // Reflect it locally so the board updates immediately (publish only sends to relays).
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /** Cancel a Buzz job [jobId] with a kind-43005 scoped to [channelId] on [relay]. */
    suspend fun cancelBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        jobId: HexKey,
    ) {
        if (!account.isWriteable()) return
        val signed = account.signer.sign(JobCancelEvent.build(jobId, "", channelId))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
    }

    /**
     * Trigger a Buzz **workflow** run (kind-46020) for [workflowId] into channel [channelId] on
     * [relay], carrying [task] as the run's request. The trigger's event id IS the run id (and the
     * approval token), returned here. A run pauses on a human-approval gate before anything ships —
     * see [com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator].
     */
    suspend fun triggerBuzzWorkflow(
        relay: NormalizedRelayUrl,
        channelId: String,
        workflowId: String,
        task: String,
    ): HexKey? {
        if (!account.isWriteable()) return null
        val content = Json.encodeToString(WorkflowRunPayload(task = task, workflow = workflowId))
        val signed = account.signer.sign(WorkflowTriggerEvent.build(workflowId, content) { workflowChannel(channelId) })
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /**
     * Publish a Buzz **workflow definition** (kind-30620) into channel [channelId] on [relay]: an
     * addressable event whose `d` tag is a freshly-minted workflow UUID (returned here), carrying a
     * human-readable [name] and the workflow's [yaml] recipe. On a real Buzz relay the relay parses
     * the YAML and runs it; self-hosted on geode the definition is a named catalog entry the picker
     * offers and `amy` triggers by id. Returns the new workflow id, or null when the account can't write.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun publishBuzzWorkflowDef(
        relay: NormalizedRelayUrl,
        channelId: String,
        name: String,
        yaml: String,
    ): String? {
        if (!account.isWriteable()) return null
        // The relay parses the `d` tag as a UUID and refuses anything else.
        val workflowId = Uuid.random().toString()
        val signed = account.signer.sign(WorkflowDefEvent.build(workflowId, channelId, yaml, name.ifBlank { null }))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return workflowId
    }

    /**
     * Grant a paused Buzz workflow run's approval gate (kind-46030). [runId] is the run id, which
     * doubles as the approval token (the grant's `d` tag). Resuming lets the runner ship the work.
     * Publishing to the single group [relay]; the runner discovers the decision by author.
     */
    suspend fun approveBuzzWorkflowRun(
        relay: NormalizedRelayUrl,
        runId: HexKey,
        note: String = "",
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(ApprovalGrantEvent.build(runId, note))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /** Deny a paused Buzz workflow run's approval gate (kind-46031); the run is terminal (DENIED). */
    suspend fun denyBuzzWorkflowRun(
        relay: NormalizedRelayUrl,
        runId: HexKey,
        note: String = "",
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(ApprovalDenyEvent.build(runId, note))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /**
     * Upvote a Buzz job [jobId] (authored by [jobAuthor]) — a NIP-25 like (kind-7 `+`) `e`-tagging
     * the request, `p`-tagging its author and `k`-tagging the reacted kind per NIP-25, and
     * `h`-scoped to [channelId] so the scheduler (and the board) count it toward priority.
     */
    suspend fun upvoteBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        jobId: HexKey,
        jobAuthor: HexKey?,
    ) {
        if (!account.isWriteable()) return
        val template =
            eventTemplate<ReactionEvent>(ReactionEvent.KIND, ReactionEvent.LIKE) {
                addUnique(ETag.assemble(jobId, null, null))
                jobAuthor?.let { addUnique(PTag.assemble(it, null)) }
                addUnique(arrayOf("k", JobRequestEvent.KIND.toString()))
                addUnique(GroupIdTag.assemble(channelId))
            }
        val signed = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
    }

    /** Send a kind 9022 leave request to the host relay and drop it from our list. */
    suspend fun leaveRelayGroup(channel: RelayGroupChannel) {
        val template = GroupLeaveRequestEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.unfollow(channel)
    }

    /**
     * Delete the whole group with a kind 9008 delete-group event (owner/admin only — the relay
     * enforces this). Unlike [leaveRelayGroup], this destroys the channel for everyone rather than
     * just removing me; the relay drops the group and its messages. Also drops it from our own list
     * so it disappears from Messages immediately instead of lingering as a now-dead id.
     */
    suspend fun deleteRelayGroup(channel: RelayGroupChannel) {
        val template = DeleteGroupEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.unfollow(channel)
        // Remember the deletion so the channel leaves the community's browse list immediately and
        // stays gone across a restart — the relay drops the group but our cached 39000 metadata (and a
        // stale re-announced 44100 on a Buzz relay) would otherwise keep it visible.
        RelayGroupDeletions.markDeleted(channel.groupId)
    }

    /**
     * Create a new group on [relay]: kind 9007 (create-group) then kind 9002
     * (edit-metadata) with the chosen name/visibility, then remember it.
     *
     * Waits for the relay's OK on the 9007 first, for at most [CREATE_OK_TIMEOUT_SECS] (the AUTH
     * warm-up included). A relay that refuses it (Buzz refuses a listed channel whose name another
     * listed channel has, ignoring case — buzz `5f8d0aab9`; any relay may refuse for policy) has
     * created nothing, so nothing else is sent or followed and the refusal comes back as
     * [RelayGroupCreateResult.Refused]. A relay that does not answer in time, or answers "later"
     * (`auth-required:` while a slow signer is still on the AUTH, a `rate-limited:` back-off), is
     * [RelayGroupCreateResult.Created] with `confirmed = false`: the pool sends the 9007 again, so the
     * channel is assumed created and followed, and a second press can't strand it under an id the
     * relay now holds.
     *
     * Once the 9007 is out, the 9002 and the follow run to completion even if the caller is
     * cancelled: stopping between them would leave a group with no name that is in nobody's list.
     * Callers still run this from a scope that outlives a screen (the account's).
     *
     * [hashtags] are NIP-29 topics. On a Buzz relay `t` tags are channel labels instead, so they
     * are not sent.
     */
    suspend fun createRelayGroup(
        relay: NormalizedRelayUrl,
        groupId: String,
        name: String,
        about: String? = null,
        picture: String? = null,
        isPrivate: Boolean = false,
        isClosed: Boolean = false,
        isHidden: Boolean = false,
        isRestricted: Boolean = false,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = null,
        channelType: String? = null,
        banner: String? = null,
    ): RelayGroupCreateResult {
        val templates =
            relayGroupCreateTemplates(
                isBuzz = BuzzRelayDialect.isBuzz(relay),
                groupId = groupId,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                isPrivate = isPrivate,
                isClosed = isClosed,
                isHidden = isHidden,
                isRestricted = isRestricted,
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
                channelType = channelType,
            )
        val signedCreate = account.signer.sign(templates.create)
        // Sign the 9002 up front too, so a signer that asks the user does it before anything is sent.
        val edit = account.signer.sign(templates.edit)
        account.cache.justConsumeMyOwnEvent(signedCreate)
        val results = publishAwaitingOk(signedCreate, relay, CREATE_OK_TIMEOUT_SECS, warmUpKind = GroupMetadataEvent.KIND)
        relayGroupCommandRefusal(results)?.let { return RelayGroupCreateResult.Refused(it) }
        val confirmed = results.values.any { it.accepted }

        return withContext(NonCancellable) {
            account.cache.justConsumeMyOwnEvent(edit)
            account.chatDeliveryTracker.trackPublic(edit.id, setOf(relay))
            account.client.publish(edit, setOf(relay))

            val id = GroupId(groupId, relay)
            // The group exists (or may still land) whatever the signer says about the list: a refusal
            // here must not read as a failed create, or the form stays open over a group that a
            // second try with the same id can't make.
            val followed =
                try {
                    account.follow(LocalCache.getOrCreateRelayGroupChannel(id))
                    true
                } catch (e: SignerExceptions) {
                    Log.w("AccountRelayGroupActions", "Created $groupId but could not add it to the group list", e)
                    false
                }
            RelayGroupCreateResult.Created(id, confirmed = confirmed, followed = followed)
        }
    }

    /**
     * On Buzz, the name of a listed channel (open, not unlisted, stream or forum) that a loaded
     * listed channel on [relay] already holds, ignoring case, or null. The relay refuses such a
     * create or rename (`invalid: an open channel named "…" already exists`, buzz `5f8d0aab9`); this
     * lets the form say so before sending. Archived channels keep their names. Channels we haven't
     * loaded can still collide, so the relay's answer stays authoritative.
     */
    fun buzzListedNameTaken(
        relay: NormalizedRelayUrl,
        name: String,
        isPrivate: Boolean,
        channelType: String?,
        except: GroupId? = null,
    ): String? {
        if (!BuzzRelayDialect.isBuzz(relay) || isPrivate) return null
        if (channelType != null && channelType != BUZZ_CHANNEL_TYPE_STREAM && channelType != BUZZ_CHANNEL_TYPE_FORUM) return null
        val wanted = canonicalBuzzChannelName(name)
        if (wanted.isEmpty()) return null
        return LocalCache
            .getRelayGroupChannelsOnRelay(relay)
            .firstOrNull { channel ->
                val event = channel.event ?: return@firstOrNull false
                val type = event.buzzChannelType()
                channel.groupId != except &&
                    !channel.isPrivate() &&
                    (type == null || type == BUZZ_CHANNEL_TYPE_STREAM || type == BUZZ_CHANNEL_TYPE_FORUM) &&
                    !event.tags.fastAny { it.isNotEmpty() && it[0] == "unlisted" } &&
                    !RelayGroupDeletions.isDeleted(channel.groupId) &&
                    event.name()?.let { canonicalBuzzChannelName(it).equals(wanted, ignoreCase = true) } == true
            }?.event
            ?.name()
    }

    /** Post a kind 11 thread (forum-style) to the group, scoped by its `h` tag. */
    suspend fun postRelayGroupThread(
        channel: RelayGroupChannel,
        title: String,
        body: String,
    ) {
        val template =
            ThreadEvent.build(body, title) {
                hTag(channel.groupId.id)
                previous(channel.previousEventRefs(account.pubKey))
            }
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Mint a kind 9009 invite code for the group (admin/moderator only). */
    suspend fun createRelayGroupInvite(
        channel: RelayGroupChannel,
        code: String,
    ) {
        val template = GroupCreateInviteEvent.build(channel.groupId.id, code)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Replace the group's pin list with a kind 9010 update-pin-list event (admin/moderator
     * only). NIP-29 carries the FULL ordered list — `e` pins and `a` pins — so the relay applies
     * it and republishes the kind-39005 [com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent].
     */
    suspend fun updateRelayGroupPins(
        channel: RelayGroupChannel,
        pins: List<GroupPin>,
    ) {
        val template = GroupUpdatePinListEvent.build(channel.groupId.id, pins)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Pin [note] in [channel]. Returns the relay's refusal, or null when it went out (or there was
     * nothing to do).
     *
     * NIP-29: append it to the current list (no-op if already pinned) and send the whole list as a
     * 9010. An addressable note is pinned by its address (`a`), so the pin follows its latest
     * version; anything else by event id (`e`). Every existing pin, other clients' `a` pins too, is kept.
     *
     * Buzz (NIP-PM): sign one kind-40010 command naming the message; the relay applies it and
     * republishes the channel's 30623 list, which [RelayGroupChannel.updateBuzzPins] folds. Buzz
     * refuses 9010 as an unknown kind.
     */
    suspend fun pinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ): String? {
        if (BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
            return sendBuzzPinCommand(channel, PinMessageEvent.build(channel.groupId.id, note.event?.id ?: note.idHex))
        }
        val pin = pinFor(note) ?: return null
        if (channel.isPinned(pin.ref)) return null
        updateRelayGroupPins(channel, channel.pinsWith(pin))
        return null
    }

    /** Unpin [note] (by address or id), keeping every other pin intact. Returns the relay's refusal, as [pinRelayGroupMessage]. */
    suspend fun unpinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ): String? {
        if (BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
            return sendBuzzPinCommand(channel, UnpinMessageEvent.build(channel.groupId.id, note.event?.id ?: note.idHex))
        }
        val refs = pinRefsFor(note)
        if (refs.none { channel.isPinned(it) }) return null
        updateRelayGroupPins(channel, channel.pins.filter { it.ref !in refs })
        return null
    }

    /**
     * Signs one NIP-PM command and sends it to [channel]'s relay, awaiting its OK through AUTH.
     * NIP-PM: a client with no answer re-sends the same signed event and never signs a new one, which
     * is what the pool does with the event it holds. Pinning a pinned message is accepted and
     * changes nothing, so there is no local no-op check.
     */
    private suspend fun <T : Event> sendBuzzPinCommand(
        channel: RelayGroupChannel,
        template: EventTemplate<T>,
    ): String? {
        val signed = account.signer.sign(template)
        return relayGroupCommandRefusal(publishAwaitingOk(signed, channel.groupId.relayUrl, CREATE_OK_TIMEOUT_SECS, warmUpKind = ChannelPinsEvent.KIND))
    }

    private fun addressOf(note: Note): Address? = note.address() ?: (note.event as? AddressableEvent)?.address()

    private fun pinFor(note: Note): GroupPin? {
        addressOf(note)?.let { return AddressPin(it) }
        return EventPin(note.event?.id ?: note.idHex)
    }

    /** Both references a note can be pinned under: its address (if addressable) and its event id. */
    private fun pinRefsFor(note: Note): Set<String> =
        buildSet {
            addressOf(note)?.let { add(it.toValue()) }
            note.event?.id?.let { add(it) }
            add(note.idHex)
        }

    /**
     * NIP-29 group migration: the same group id now lives on [newRelay] (moved or forked). Swap this
     * group's entry in our kind-10009 for the one on [newRelay] and return the new channel, which the
     * caller opens so its events load from the new relay.
     */
    suspend fun moveRelayGroup(
        channel: RelayGroupChannel,
        newRelay: NormalizedRelayUrl,
    ): RelayGroupChannel {
        val target = LocalCache.getOrCreateRelayGroupChannel(GroupId(channel.groupId.id, newRelay))
        account.sendMyPublicAndPrivateOutbox(account.relayGroupList.move(channel, target))
        return target
    }

    /** Kick [pubkey] out of the group with a kind 9001 remove-user event (moderator only). */
    suspend fun removeRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) {
        val template = GroupRemoveUserEvent.build(channel.groupId.id, listOf(pubkey))
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to the group as a plain member with a kind 9000 put-user event, without
     * touching the role of someone who is already in it.
     *
     * On Buzz this sends **no** `role` tag: the relay reads a missing role as "no role change",
     * keeping an existing member's role and defaulting only a newcomer to `member`. Sending
     * `role=member` instead would demote an admin who happened to be re-added, or be refused
     * outright (`only owners/admins may change an active member's role`) when the actor is not
     * elevated. Ground truth: `handle_put_user` in `buzz-relay/src/handlers/side_effects.rs` and
     * `decide_put_user` in `channel_authz.rs`.
     */
    suspend fun addRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) {
        val template = GroupPutUserEvent.build(channel.groupId.id, listOf(pubkey to emptyList()))
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Set [pubkey]'s roles in the group with a kind 9000 put-user event (moderator only). An
     * empty [roles] list makes them a plain member - on Buzz that is an explicit demotion to
     * `member`. To add someone without changing a role, use [addRelayGroupUser].
     */
    suspend fun putRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        roles: List<String>,
    ) {
        // Buzz has one role per member, in its own vocabulary, and reads it from `p[2]` before the
        // `role` tag: our NIP-29 roles there (`moderator`, or the old role first in an edit) were
        // refused or won over the new one. Map ours onto its set and send the role tag alone (see
        // GroupPutUserEvent.buildRoleChange). An unmapped role becomes `member` rather than travel.
        val buzzRole =
            if (BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
                buzzRoleFor(roles)
            } else {
                null
            }
        val template = GroupPutUserEvent.buildRoleChange(channel.groupId.id, pubkey, roles, buzzRole)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to a Buzz **community** (the whole relay/tenant, not one channel) via the
     * relay-admin add-member command (kind 9030). Owner/admin only — the relay validates the
     * sender's role and, on a new insert, updates its NIP-43 membership list (13534). Published to
     * [relay] with no channel scope.
     */
    suspend fun addCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
        role: String? = null,
    ) {
        account.broadcaster.signAndSendPrivatelyOrBroadcast(RelayAdminAddMemberEvent.build(pubkey, role)) { listOf(relay) }
    }

    /** Remove [pubkey] from a Buzz community via the relay-admin remove-member command (kind 9031). */
    suspend fun removeCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
    ) {
        account.broadcaster.signAndSendPrivatelyOrBroadcast(RelayAdminRemoveMemberEvent.build(pubkey)) { listOf(relay) }
    }

    /**
     * Edit the group's relay-signed metadata with a kind 9002 event (admin only).
     *
     * NIP-29 §Subgroups makes the metadata edit a full replacement of the hierarchy
     * links: a 9002 with no `parent` tag re-roots the group, and one that drops any
     * existing `child` is rejected by the relay. So unless the caller is explicitly
     * re-parenting, we re-carry the group's current [parent] and full [children] list
     * from its latest known metadata to keep the tree intact across a plain name/flag
     * edit. Pass an explicit value to change them.
     *
     * A 9002 also carries "all the fields of group-metadata", so the current 39000's [banner]
     * (unless replaced) and every tag this form doesn't manage (`livekit`, `supported_kinds`, a
     * newer spec field…) ride along verbatim — otherwise a rename would erase them on a relay that
     * rebuilds the metadata from the edit.
     *
     * On a Buzz relay [hashtags] are ignored: there the 39000's `t` tags are the channel type and its
     * labels, and a 9002 `t` tag replaces the label set — echoing them back (the type included) made
     * the relay refuse every rename (buzz `2f79374f4`). The labels are left as they are.
     *
     * A Buzz relay refuses a rename for policy (a listed name another listed channel holds), so there
     * this waits for the OK and returns the relay's reason, or null when it took the edit or did not
     * answer in time. A NIP-29 relay's edit goes out without waiting, and this returns null.
     */
    suspend fun editRelayGroupMetadata(
        channel: RelayGroupChannel,
        name: String?,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = channel.parentGroupId(),
        children: List<String> = channel.childGroupIds(),
        banner: String? = channel.bannerPicture(),
    ): String? {
        val isBuzz = BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)
        val template =
            GroupEditMetadataEvent.buildEdit(
                channel.groupId.id,
                currentMetadata = channel.event,
                isBuzz = isBuzz,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
                children = children,
                buzzLabels = null,
            )
        if (!isBuzz) {
            account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
            return null
        }
        val signed = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(signed)
        return relayGroupCommandRefusal(publishAwaitingOk(signed, channel.groupId.relayUrl, CREATE_OK_TIMEOUT_SECS, warmUpKind = GroupMetadataEvent.KIND))
    }

    /**
     * Archive or unarchive a Buzz channel (a minimal kind-9002 carrying only the `archived` tag). The
     * relay hides an archived channel from the sidebar and stamps the 39000, but keeps it and its
     * history — the reversible counterpart to [deleteRelayGroup]. Admin/owner only; the relay enforces.
     */
    suspend fun archiveRelayGroup(
        channel: RelayGroupChannel,
        archived: Boolean,
    ) {
        val template = GroupEditMetadataEvent.build(channel.groupId.id, archived = archived)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }
}

/** The two events that make a group: the 9007 create and the 9002 that names it. */
class RelayGroupCreateTemplates(
    val create: EventTemplate<CreateGroupEvent>,
    val edit: EventTemplate<GroupEditMetadataEvent>,
)

/**
 * A new group id for [isBuzz]'s dialect: a Buzz relay keys channels by UUID and ignores an id it
 * cannot parse as one; a NIP-29 relay gets 8 random bytes, hex-encoded (as Armada does).
 */
fun newRelayGroupId(isBuzz: Boolean): String = if (isBuzz) newBuzzChannelId() else RandomInstance.bytes(8).toHexKey()

/**
 * The create and edit events of a new group, shared by the app's form and `amy relaygroup create`.
 *
 * The metadata rides the create event as well as the 9002. A plain NIP-29 relay takes its metadata
 * from the 9002 and ignores these tags; Buzz rejects a bare 9007 outright (`invalid: channel name is
 * required`), which is why a create used to publish two events and produce nothing on Buzz.
 * [hashtags] are NIP-29 topics: on Buzz `t` tags are the channel's labels, so they are not sent.
 */
fun relayGroupCreateTemplates(
    isBuzz: Boolean,
    groupId: String,
    name: String,
    about: String? = null,
    picture: String? = null,
    banner: String? = null,
    isPrivate: Boolean = false,
    isClosed: Boolean = false,
    isHidden: Boolean = false,
    isRestricted: Boolean = false,
    hashtags: List<String> = emptyList(),
    geohashes: List<String> = emptyList(),
    parent: String? = null,
    channelType: String? = null,
): RelayGroupCreateTemplates =
    RelayGroupCreateTemplates(
        create =
            CreateGroupEvent.build(
                groupId = groupId,
                name = name,
                about = about,
                visibility = if (isPrivate) BUZZ_VISIBILITY_PRIVATE else BUZZ_VISIBILITY_OPEN,
                channelType = channelType,
            ),
        edit =
            GroupEditMetadataEvent.build(
                groupId,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = if (isBuzz) emptyList() else hashtags,
                geohashes = geohashes,
                parent = parent,
            ),
    )

/**
 * The set of NIP-29 status flags to emit on a kind-9002 metadata event. Flags are
 * presence-only — public/open/visible/unrestricted are simply the ABSENCE of their
 * restrictive counterpart — so only the enabled restrictive flags are added.
 */
fun relayGroupStatus(
    isPrivate: Boolean,
    isClosed: Boolean,
    isHidden: Boolean,
    isRestricted: Boolean,
): Set<GroupMetadataEvent.GroupStatus> =
    buildSet {
        if (isPrivate) add(GroupMetadataEvent.GroupStatus.PRIVATE)
        if (isClosed) add(GroupMetadataEvent.GroupStatus.CLOSED)
        if (isHidden) add(GroupMetadataEvent.GroupStatus.HIDDEN)
        if (isRestricted) add(GroupMetadataEvent.GroupStatus.RESTRICTED)
    }

/** What became of a [AccountRelayGroupActions.createRelayGroup]. */
sealed interface RelayGroupCreateResult {
    /**
     * The relay accepted the create ([confirmed]), or has not answered yet and it may still land: it
     * went silent, or answered `auth-required:` / a `rate-limited:` back-off and the pool sends it
     * again. Either way it is followed, so a group that lands later is in the list. [followed] is
     * false when the signer would not sign the kind-10009 that lists it.
     */
    data class Created(
        val groupId: GroupId,
        val confirmed: Boolean = true,
        val followed: Boolean = true,
    ) : RelayGroupCreateResult

    /** The relay refused the create, so no group exists; [message] is its reason. */
    data class Refused(
        val message: String,
    ) : RelayGroupCreateResult
}

/**
 * The relay's reason when it explicitly refused a group command (a create, an edit, a DM add), or
 * null when it accepted it or never answered (a transport failure says nothing about the group).
 * Relay prefixes (`invalid:`, `restricted:`, …) are dropped so the reason reads as a sentence.
 */
internal fun relayGroupCommandRefusal(results: Map<NormalizedRelayUrl, PublishResult>): String? {
    if (results.values.any { it.accepted }) return null
    val refusal = results.values.firstOrNull { !it.isTransportFailure && !it.isNotYetAnswered() } ?: return null
    val message = refusal.message
    val prefix = message.substringBefore(':', "")
    return if (prefix.isNotEmpty() && prefix.none { it.isWhitespace() }) message.substringAfter(':').trim() else message
}

/** What a Buzz DM add does with the conversation's earlier messages ([AccountRelayGroupActions.addBuzzDmMember]). */
enum class BuzzDmHistory {
    /** Let the relay decide where it can, and ask ([BuzzDmAddResult.NeedsHistoryChoice]) where it can't. */
    ASK,

    /** Grow this DM in place: the newcomer sees its history. */
    SHARE,

    /** Move to the DM of these people and the newcomer, found or made, without this history. */
    DONT_SHARE,
}

/** What became of a [AccountRelayGroupActions.addBuzzDmMember]. */
sealed interface BuzzDmAddResult {
    /** The newcomer is in [groupId], which is not the DM the add was sent from when [moved]. */
    data class Added(
        val groupId: GroupId,
        val moved: Boolean,
    ) : BuzzDmAddResult

    /** The add would let them see this DM's history: ask the user and send again with a choice. */
    data object NeedsHistoryChoice : BuzzDmAddResult

    /** The relay refused the add; [message] is its reason. */
    data class Refused(
        val message: String,
    ) : BuzzDmAddResult

    /** The relay did not answer in time (or answered without naming a DM). */
    data object NoAnswer : BuzzDmAddResult
}

/** Reads the relay's answer to a DM add sent from the DM on [relay] whose id the event's `h` names. */
fun buzzDmAddResult(
    results: Map<NormalizedRelayUrl, PublishResult>,
    relay: NormalizedRelayUrl,
    fromChannelId: String? = null,
): BuzzDmAddResult {
    val accepted = results.values.firstOrNull { it.accepted }
    if (accepted != null) {
        val landed = BuzzDmRules.channelIdFromOk(accepted.message) ?: return BuzzDmAddResult.NoAnswer
        return BuzzDmAddResult.Added(GroupId(landed, relay), moved = fromChannelId != null && landed != fromChannelId)
    }
    if (results.values.any { it.message.startsWith(BuzzDmRules.NEEDS_HISTORY_CHOICE) }) return BuzzDmAddResult.NeedsHistoryChoice
    val refused = results.values.firstOrNull { !it.isTransportFailure && !it.isNotYetAnswered() } ?: return BuzzDmAddResult.NoAnswer
    return BuzzDmAddResult.Refused(refused.message)
}

/**
 * A refusal that means "later", not "no": the pool keeps the event and sends it again, so the relay
 * may still accept it after we stopped waiting. That is a NIP-42 `auth-required:` (re-sent once the
 * AUTH lands; a NIP-46 or Android signer slower than the wait is enough) and a `rate-limited:` that
 * states its back-off (re-sent after it). A `rate-limited:` without one is the relay's last word:
 * the pool drops the event.
 */
internal fun PublishResult.isNotYetAnswered(): Boolean =
    !accepted &&
        when (MachineReadablePrefix.parse(message)) {
            MachineReadablePrefix.AUTH_REQUIRED -> true
            MachineReadablePrefix.RATE_LIMITED -> retryAfterMs != null
            else -> false
        }

/**
 * Whether the relay says a DM command made a new DM (`response:{…,"created":true}`), or null when the
 * OK does not say. Shared by the app and `amy buzz dm add-member`.
 */
fun buzzDmCreatedFromOk(message: String?): Boolean? =
    message
        ?.let { CREATED_FIELD.find(it) }
        ?.groupValues
        ?.get(1)
        ?.toBooleanStrict()

private val CREATED_FIELD = Regex("\"created\"\\s*:\\s*(true|false)")

/** The relay's refusal of a history tag from an agent (buzz `handle_dm_add_member`, `ONLY_PEOPLE_SHARE_HISTORY`). */
const val ONLY_PEOPLE_SHARE_HISTORY = "forbidden: only people can share a DM's history"

/**
 * True when the relay refused a DM add only because it carried a history tag and the sender is an
 * agent: Buzz lets only people choose about history, so the add is sent again untagged. Shared by the
 * app and `amy buzz dm add-member`.
 */
fun buzzDmAddRefusedHistoryTag(results: Map<NormalizedRelayUrl, PublishResult>): Boolean = results.values.none { it.accepted } && results.values.any { it.message == ONLY_PEOPLE_SHARE_HISTORY }

/** How long a group command waits for its OK; the AUTH warm-up can double it. */
private const val OK_TIMEOUT_SECS = 15L

/** How long a create or a Buzz edit waits for its OK, each attempt: the screen waits on it. */
private const val CREATE_OK_TIMEOUT_SECS = 8L

/** How long the AUTH warm-up read may take. */
private const val AUTH_WARM_UP_MS = 5_000L

/** A profile signed this recently is within Buzz's timestamp window and can be relayed as-is. */
private const val BUZZ_FRESH_PROFILE_SECS = 60L
