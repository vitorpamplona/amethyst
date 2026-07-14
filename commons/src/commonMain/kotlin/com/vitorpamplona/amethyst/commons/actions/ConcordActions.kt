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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.Guestbook
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookAction
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookEntry
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordDirectInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteBundle
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.MintedInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.ParsedInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.RefoundingBuild
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/** One decrypted, verified Concord channel message projected for display. */
data class ConcordChatMessage(
    val id: HexKey,
    val author: HexKey,
    val content: String,
    val createdAt: Long,
    val channelId: HexKey,
    val epoch: Long,
)

/**
 * Concord community verbs — pure builders, plane-key derivation, relay-filter
 * assembly, and event folding usable from amy CLI, the Android app, and any other
 * non-UI consumer.
 *
 * Like [DmActions], this object never touches the network: create/send builders
 * return events to publish, the read side takes already-fetched wraps and folds
 * them. The caller (amy `Context`, an Android ViewModel) owns publish/drain and
 * persistence of the community's secrets.
 */
object ConcordActions {
    // ---- plane key derivation -------------------------------------------------

    fun controlPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, rootEpoch)

    fun publicChannel(
        communityRoot: ByteArray,
        channelId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch)

    /** The Guestbook Plane address for a community at [rootEpoch] — where join/leave motions ride. */
    fun guestbookPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.guestbookPlaneKey(communityRoot, communityId, rootEpoch)

    /**
     * The base-rotation rekey address a member watches to receive the *next* epoch's
     * Refounding (CORD-06 §2): `base-rekey-pseudonym(current_root, community_id,
     * rootEpoch + 1)`. Precomputed from the root the member already holds.
     */
    fun nextBaseRekeyPlane(
        communityRoot: ByteArray,
        communityId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.baseRekeyAddress(communityRoot, communityId, rootEpoch + 1)

    // ---- relay filters (what to REQ) -----------------------------------------

    /** Wraps at a plane/channel address: kind-1059 events authored by the stream key. */
    fun planeFilter(planePubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordStreamEnvelope.KIND_WRAP), authors = listOf(planePubKeyHex))

    /** Wraps across several plane addresses on one relay: kind-1059 authored by any of them. */
    fun planeFilterFor(planePubKeysHex: List<HexKey>): Filter = Filter(kinds = listOf(ConcordStreamEnvelope.KIND_WRAP), authors = planePubKeysHex)

    /** The public invite bundle for a link signer. */
    fun bundleFilter(linkSignerPubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordInviteBundleEvent.KIND), authors = listOf(linkSignerPubKeyHex))

    /** Pending direct invites addressed to the given member (indexed by k=3313). */
    fun directInvitesFilter(memberPubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordStreamEnvelope.KIND_WRAP), tags = mapOf("p" to listOf(memberPubKeyHex), "k" to listOf(ConcordDirectInvite.KIND.toString())))

    // ---- community lifecycle --------------------------------------------------

    /** Creates a community and its genesis editions (see [ConcordCommunityFactory]). */
    suspend fun createCommunity(
        ownerSigner: NostrSigner,
        name: String,
        createdAt: Long,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): NewConcordCommunity = ConcordCommunityFactory.create(ownerSigner, name, createdAt, description, relays, icon)

    /** Opens the control-plane [wraps] into their [ControlEdition]s (drops any that don't open/parse). */
    fun controlEditions(
        wraps: List<Event>,
        controlPlane: GroupKey,
    ): List<ControlEdition> =
        wraps.mapNotNull { wrap ->
            ConcordStreamEnvelope.openOrNull(wrap, controlPlane)?.let { ControlEdition.fromRumor(it.rumor) }
        }

    /** Opens the control-plane [wraps] and folds them into the live community state. */
    fun foldCommunity(
        wraps: List<Event>,
        controlPlane: GroupKey,
        ownerPubKey: HexKey,
    ): ConcordCommunityState = ConcordCommunityState.fold(controlEditions(wraps, controlPlane), ownerPubKey)

    // ---- channel chat ---------------------------------------------------------

    /** Builds an encrypted-seal channel message wrap to publish on the [channel] plane. */
    suspend fun buildChannelMessage(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
    ): Event {
        val rumor = ChannelChat.message(authorSigner.pubKey, channelId, epoch, text, createdAt, extraTags)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
    }

    /**
     * Builds an encrypted-seal channel message wrap carrying one or more encrypted image [imetas]
     * (Armada `encryptAttachments` shape) to publish on the [channel] plane.
     */
    suspend fun buildChannelImageMessage(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        imetas: List<IMetaTag>,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.imageMessage(authorSigner.pubKey, channelId, epoch, text, imetas, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
    }

    /** Builds an encrypted-seal inline quote-reply wrap (kind-9 message quoting [parent] via `q`) on the [channel] plane. */
    suspend fun buildChannelInlineReply(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        parent: Event,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
    ): Event {
        val rumor = ChannelChat.inlineReply(authorSigner.pubKey, channelId, epoch, text, parent.id, parent.pubKey, createdAt, extraTags)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
    }

    /** Builds an encrypted-seal thread-reply wrap (kind-1111 NIP-22 comment on [parent]) on the [channel] plane. */
    suspend fun buildChannelReply(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        parent: Event,
        text: String,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.reply(authorSigner.pubKey, channelId, epoch, text, parent, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
    }

    /** Builds an encrypted-seal reaction wrap (kind 7 against [target]) on the [channel] plane. */
    suspend fun buildChannelReaction(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        target: Event,
        reaction: String,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.reaction(authorSigner.pubKey, channelId, epoch, target.id, target.pubKey, target.kind, reaction, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
    }

    /**
     * Builds an **ephemeral** typing heartbeat wrap (kind-23311 rumor, kind-21059 wrap)
     * on the [channel] plane. Relays broadcast but never store it; publish every few
     * seconds while the user is composing.
     */
    suspend fun buildChannelTyping(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.typing(authorSigner.pubKey, channelId, epoch, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true, ephemeral = true, createdAt = createdAt)
    }

    /**
     * Opens the channel [wraps], keeps the kind-9 messages correctly bound to
     * [channelId]/[epoch], and returns them oldest-first (createdAt, then id).
     */
    fun channelMessages(
        wraps: List<Event>,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
    ): List<ConcordChatMessage> =
        wraps
            .mapNotNull { wrap -> ConcordStreamEnvelope.openOrNull(wrap, channel)?.rumor }
            .filter { it.kind == ChatEvent.KIND && ChannelChat.isBoundTo(it, channelId, epoch) }
            .map { ConcordChatMessage(it.id, it.pubKey, it.content, it.createdAt, channelId, epoch) }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))

    /**
     * Opens the channel [wraps] and returns every validated inner rumor bound to
     * [channelId]/[epoch] — messages (kind 9), replies (1111), reactions (7),
     * deletes (5), edits, etc. — as typed [Event]s. The caller lands these in a
     * store keyed by rumor id so the normal reaction/reply/delete/OTS machinery
     * wires up automatically. Deduping is left to that store (rumor ids are stable
     * content hashes), so this may return duplicates across mirrored wraps.
     */
    fun channelRumors(
        wraps: List<Event>,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
    ): List<Event> =
        wraps
            .mapNotNull { wrap -> ConcordStreamEnvelope.openOrNull(wrap, channel)?.rumor }
            .filter { ChannelChat.isBoundTo(it, channelId, epoch) }

    // ---- invites --------------------------------------------------------------

    /** Builds a [CommunityInvite] from a freshly created (or joined) community's public info. */
    fun inviteFor(
        communityIdHex: HexKey,
        ownerPubKey: HexKey,
        ownerSaltHex: HexKey,
        communityRootHex: HexKey,
        rootEpoch: Long,
        name: String,
        relays: List<String>,
    ): CommunityInvite =
        CommunityInvite(
            communityId = communityIdHex,
            owner = ownerPubKey,
            ownerSalt = ownerSaltHex,
            communityRoot = communityRootHex,
            rootEpoch = rootEpoch,
            relays = relays,
            name = name,
        )

    /** Mints a shareable public invite link + bundle event (see [ConcordInviteBundle.mintLink]). */
    fun mintInviteLink(
        base: String,
        invite: CommunityInvite,
        createdAt: Long,
        relays: List<String>? = null,
    ): MintedInviteLink = ConcordInviteBundle.mintLink(base, invite, createdAt, relays)

    /** Parses a shareable invite URL into its pointer + private fragment. */
    fun parseInviteLink(url: String): ParsedInviteLink? = ConcordInviteLink.parseUrl(url)

    /** Decrypts + validates a fetched bundle event with the link token; null if invalid. */
    fun openBundle(
        bundleEvent: Event,
        token: ByteArray,
    ): CommunityInvite? = ConcordInviteBundle.parse(bundleEvent, token)?.takeIf { ConcordInviteBundle.validate(it) }

    /** Derives the control plane described by a redeemed [invite] so the joiner can read it. */
    fun controlPlaneFor(invite: CommunityInvite): GroupKey = controlPlane(invite.communityRoot.hexToByteArray(), invite.communityId.hexToByteArray(), invite.rootEpoch)

    // ---- guestbook (CORD-02 §5) ----------------------------------------------

    /**
     * Builds a self-signed Guestbook JOIN (kind 3306) wrap on the community's
     * Guestbook Plane. Membership is off-consensus best-effort presence, but it is
     * the member-visible roster a Refounding rotates keys to (CORD-06), so a client
     * announces one on create/join to be re-keyed on future removals.
     */
    suspend fun buildGuestbookJoin(
        memberSigner: NostrSigner,
        guestbook: GroupKey,
        createdAt: Long,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
    ): Event {
        val rumor = Guestbook.join(memberSigner.pubKey, createdAt, inviteCreator = inviteCreator, inviteLabel = inviteLabel)
        return ConcordStreamEnvelope.wrap(rumor, guestbook, memberSigner, encrypted = true, createdAt = createdAt)
    }

    /** Opens the guestbook [wraps] into their live membership set (joins minus later leaves). */
    fun guestbookMembers(
        wraps: List<Event>,
        guestbook: GroupKey,
    ): Set<HexKey> {
        val latest = HashMap<HexKey, GuestbookEntry>()
        for (wrap in wraps) {
            val rumor = ConcordStreamEnvelope.openOrNull(wrap, guestbook)?.rumor ?: continue
            val entry = Guestbook.parse(rumor) ?: continue
            val prev = latest[entry.member.lowercase()]
            if (prev == null || entry.createdAt > prev.createdAt) latest[entry.member.lowercase()] = entry
        }
        return latest.values.filter { it.action == GuestbookAction.JOIN }.mapTo(HashSet()) { it.member.lowercase() }
    }

    // ---- refounding / rekey (CORD-06) ----------------------------------------

    /**
     * Builds a whole-community Refounding (CORD-06 §3): the compacted Control Plane
     * re-sealed under [newRoot] plus the base-rotation rekey blobs delivering
     * [newRoot] to [recipientsXOnly]. Pure — the caller sources the recipient set
     * and owns publish + persistence.
     */
    suspend fun buildRefounding(
        rotatorSigner: NostrSigner,
        communityId: HexKey,
        priorRoot: ByteArray,
        newRoot: ByteArray,
        rootEpoch: Long,
        priorControlWraps: List<Event>,
        priorControlKey: GroupKey,
        recipientsXOnly: List<HexKey>,
        createdAt: Long,
    ): RefoundingBuild =
        ConcordRefounding.build(
            rotatorSigner = rotatorSigner,
            communityId = communityId.hexToByteArray(),
            priorRoot = priorRoot,
            newRoot = newRoot,
            rootEpoch = rootEpoch,
            priorControlWraps = priorControlWraps,
            priorControlKey = priorControlKey,
            recipientsXOnly = recipientsXOnly,
            createdAt = createdAt,
        )

    /**
     * Receives an inbound base rotation for the member behind [recipientSigner]:
     * finds the delivered new root across the buffered kind-3303 [wraps], verifying
     * scope, epoch and continuity against the [priorRoot] the member holds. Returns
     * the new root + rotator (for the caller to authorize) or null if not re-keyed.
     */
    suspend fun openBaseRekey(
        wraps: List<Event>,
        baseRekey: GroupKey,
        recipientSigner: NostrSigner,
        priorRoot: ByteArray,
        rootEpoch: Long,
    ): ReceivedRefounding? = ConcordRefounding.findNewRoot(wraps, baseRekey, recipientSigner, priorRoot, rootEpoch)
}
