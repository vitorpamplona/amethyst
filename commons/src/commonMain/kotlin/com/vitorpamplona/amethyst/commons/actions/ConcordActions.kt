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
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteBundle
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.MintedInviteLink
import com.vitorpamplona.quartz.concord.cord05Invites.ParsedInviteLink
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

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

    // ---- relay filters (what to REQ) -----------------------------------------

    /** Wraps at a plane/channel address: kind-1059 events authored by the stream key. */
    fun planeFilter(planePubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordKinds.WRAP), authors = listOf(planePubKeyHex))

    /** Wraps across several plane addresses on one relay: kind-1059 authored by any of them. */
    fun planeFilterFor(planePubKeysHex: List<HexKey>): Filter = Filter(kinds = listOf(ConcordKinds.WRAP), authors = planePubKeysHex)

    /** The public invite bundle for a link signer. */
    fun bundleFilter(linkSignerPubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordKinds.INVITE_BUNDLE), authors = listOf(linkSignerPubKeyHex))

    /** Pending direct invites addressed to the given member (indexed by k=3313). */
    fun directInvitesFilter(memberPubKeyHex: HexKey): Filter = Filter(kinds = listOf(ConcordKinds.WRAP), tags = mapOf("p" to listOf(memberPubKeyHex), "k" to listOf(ConcordKinds.DIRECT_INVITE.toString())))

    // ---- community lifecycle --------------------------------------------------

    /** Creates a community and its genesis editions (see [ConcordCommunityFactory]). */
    suspend fun createCommunity(
        ownerSigner: NostrSigner,
        name: String,
        createdAt: Long,
        description: String? = null,
        relays: List<String> = emptyList(),
    ): NewConcordCommunity = ConcordCommunityFactory.create(ownerSigner, name, createdAt, description, relays)

    /** Opens the control-plane [wraps] and folds them into the live community state. */
    fun foldCommunity(
        wraps: List<Event>,
        controlPlane: GroupKey,
        ownerPubKey: HexKey,
    ): ConcordCommunityState {
        val editions =
            wraps.mapNotNull { wrap ->
                ConcordStreamEnvelope.openOrNull(wrap, controlPlane)?.let { ControlEdition.fromRumor(it.rumor) }
            }
        return ConcordCommunityState.fold(editions, ownerPubKey)
    }

    // ---- channel chat ---------------------------------------------------------

    /** Builds an encrypted-seal channel message wrap to publish on the [channel] plane. */
    suspend fun buildChannelMessage(
        authorSigner: NostrSigner,
        channel: GroupKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        createdAt: Long,
    ): Event {
        val rumor = ChannelChat.message(authorSigner.pubKey, channelId, epoch, text, createdAt)
        return ConcordStreamEnvelope.wrap(rumor, channel, authorSigner, encrypted = true)
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
            .filter { it.kind == ConcordKinds.MESSAGE && ChannelChat.isBoundTo(it, channelId, epoch) }
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
}
