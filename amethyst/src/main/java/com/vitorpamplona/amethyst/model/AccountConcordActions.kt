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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordChannelLastReadRoute
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityList.withControlRoot
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlRootWrap
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.cord05Invites.InviteRelayDictionary
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap

/** Name of the default Concord community Admin role minted by "Make admin". */
private const val CONCORD_ADMIN_ROLE = "Admin"

/**
 * How often a joined Concord community's stored invite link is re-resolved to check whether
 * we were left out of a Refounding (see `recoverStrandedConcordCommunities`). Stranding is
 * rare and silent, so this trades detection latency for not turning the revision tick into a
 * relay-fetch loop.
 */
private const val RECOVERY_CHECK_INTERVAL_MS = 15 * 60 * 1000L

/**
 * Concord (encrypted communities) orchestration for an [Account]: join/create/
 * invite flows, channel messages/reactions/edits/typing, roles and moderation,
 * community refound/recovery, metadata and channel management, and control-plane
 * sync. Event building lives in the commons `ConcordActions`/`ConcordModeration`
 * objects; this class wires them to the account's signer, session manager,
 * channel list, and relay client. Rumor ingestion stays on [Account]
 * (`consumeConcordRumorGated`), which is wired into `ConcordSessionManager`.
 */
class AccountConcordActions(
    private val account: Account,
) {
    /**
     * Add a joined Concord community (secret-bearing entry) to the private kind-13302
     * list, and announce a self-signed Guestbook JOIN so this member is visible to
     * whoever later refounds the community (CORD-06 re-keys the Guestbook membership).
     */
    suspend fun joinConcordCommunity(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
    ) {
        account.sendMyPublicAndPrivateOutbox(account.concordChannelList.follow(entry))
        announceConcordGuestbookJoin(entry, inviteCreator, inviteLabel)
    }

    /** Publishes a Guestbook JOIN (kind 3306) for [entry] to its community relays. */
    private suspend fun announceConcordGuestbookJoin(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey?,
        inviteLabel: String?,
    ) {
        if (!account.isWriteable()) return
        val guestbook = ConcordActions.guestbookPlane(entry.root.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
        val wrap = ConcordActions.buildGuestbookJoin(account.signer, guestbook, TimeUtils.now(), inviteCreator, inviteLabel)
        account.concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /**
     * Create a new Concord community: mint its genesis (metadata + #general),
     * publish the owner-signed genesis wraps to [relays] (or our outbox), and add
     * the secret-bearing entry to the kind-13302 joined list. Returns the new
     * community id, or null if not writeable.
     */
    suspend fun createConcordCommunity(
        name: String,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): String? {
        if (!account.isWriteable()) return null
        val relayUrls =
            relays.ifEmpty {
                account.outboxRelays.flow.value
                    .map { it.url }
            }
        val community = ConcordActions.createCommunity(account.signer, name, TimeUtils.now(), description, relayUrls, icon)

        val publishTo = relayUrls.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        community.genesisWraps.forEach { account.client.publish(it, publishTo) }

        joinConcordCommunity(
            ConcordCommunityListEntry(
                id = community.communityIdHex,
                owner = community.ownerPubKey,
                ownerSalt = community.ownerSalt.toHexKey(),
                root = community.communityRoot.toHexKey(),
                rootEpoch = community.rootEpoch,
                // The creator is the founding staff member (CORD-02 §2): it keeps the write
                // secret and publishes only the derived pubkey to everyone else.
                controlPk = community.controlPkHex,
                controlRoot = community.controlRoot.toHexKey(),
                relays = relayUrls,
                name = name,
                addedAt = TimeUtils.now() * 1000,
            ),
        )
        return community.communityIdHex
    }

    /**
     * Mint a shareable invite link for a joined community and publish its
     * kind-33301 public bundle to the community relays. Returns the `…/invite/…`
     * URL, or null if the community isn't joined or isn't writeable.
     */
    suspend fun mintConcordInvite(
        communityId: String,
        base: String = "https://amethyst.social",
    ): String? {
        if (!account.isWriteable()) return null
        val entry =
            account.concordChannelList.liveCommunities.value
                .firstOrNull { it.id == communityId } ?: return null
        val invite =
            ConcordActions.inviteFor(
                communityIdHex = entry.id,
                ownerPubKey = entry.owner,
                ownerSaltHex = entry.ownerSalt,
                communityRootHex = entry.root,
                rootEpoch = entry.rootEpoch,
                name = entry.name,
                relays = entry.relays,
                // The joiner can never derive the Control Plane address, so the bundle carries
                // it (CORD-05 §1). Null on a legacy community, which has none to carry.
                controlPk = entry.controlPk,
            )
        val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), entry.relays)

        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { account.outboxRelays.flow.value }
        if (publishTo.isNotEmpty()) account.client.publish(minted.bundleEvent, publishTo)
        return minted.url
    }

    /** Drop a joined Concord community from the private kind-13302 list by its id. */
    suspend fun leaveConcordCommunity(communityId: String) = account.sendMyPublicAndPrivateOutbox(account.concordChannelList.unfollow(communityId))

    /**
     * Redeem a Concord invite link (`…/invite/<naddr>#<fragment>`): parse it, fetch
     * the kind-33301 public bundle from the link's relays (+ our outbox), unlock it
     * with the fragment token, and add the resulting secret-bearing entry to the
     * kind-13302 joined list.
     *
     * Returns a [ConcordInviteResult] that separates the failure modes so the UI can
     * both explain what went wrong and decide whether a retry could ever help — a
     * bundle we can't open (e.g. minted by a newer client) must not strand the user
     * on a spinner that retries forever.
     *
     * A bundle whose `expires_at` has passed is rejected with
     * [ConcordInviteResult.Expired]. Expiry is resolved inside
     * [ConcordActions.classifyInvite], so it is enforced on every redeem path rather
     * than being a field nobody reads.
     *
     * **This must only ever be called from an explicit user action.** It contacts
     * relay URLs carried in the link (chosen by whoever minted it) and publishes a
     * Guestbook JOIN signed by this account, so calling it on deep-link arrival would
     * leak the user's IP and enroll them without consent — see `ConcordInviteScreen`.
     *
     * If the resolved community is already in the joined list, this returns
     * [ConcordInviteResult.Joined] without re-following or re-announcing a Guestbook
     * JOIN, so reopening an old invite for a community you're already in simply takes
     * you to it.
     */
    suspend fun joinConcordViaInvite(url: String): ConcordInviteResult {
        if (!account.isWriteable()) return ConcordInviteResult.InvalidLink
        val parsed = ConcordActions.parseInviteLink(url) ?: return ConcordInviteResult.InvalidLink

        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + account.outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return ConcordInviteResult.NotReachable

        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = account.client.fetchAll(filters = filters)

        // Resolve the coordinate per CORD-05 §2 (newest wins; a vsk=9 tombstone revokes even over a
        // stale openable copy) so we honour revocation and can tell the user *why* a link won't open
        // instead of stranding them on a spinner that retries a link we can never redeem.
        val bundle =
            when (val status = ConcordActions.classifyInvite(wraps, parsed.fragment.token)) {
                is InviteBundleStatus.Live -> status.invite
                is InviteBundleStatus.Expired -> return ConcordInviteResult.Expired
                InviteBundleStatus.Revoked -> return ConcordInviteResult.Revoked
                InviteBundleStatus.Unreadable -> return ConcordInviteResult.Incompatible
                InviteBundleStatus.Absent -> return ConcordInviteResult.NotReachable
            }

        // Already a member? Just take the user to the community. Re-following and re-announcing a
        // Guestbook JOIN (kind 3306) would spam the community relays with a fresh join every time an
        // old invite is reopened, so short-circuit to Joined — the screen forwards to the community
        // either way ("take me there", not "join again").
        if (account.concordChannelList.liveCommunities.value
                .any { it.id == bundle.communityId }
        ) {
            return ConcordInviteResult.Joined(bundle.communityId)
        }

        val entry =
            ConcordCommunityListEntry(
                id = bundle.communityId,
                owner = bundle.owner,
                ownerSalt = bundle.ownerSalt,
                root = bundle.communityRoot,
                rootEpoch = bundle.rootEpoch,
                // Read access to the Control Plane, never write (CORD-05 §1). Absent = the
                // community is still pre-split, so we fold it at the legacy address.
                controlPk = bundle.controlPk,
                relays = bundle.relays,
                name = bundle.name,
                addedAt = TimeUtils.now() * 1000,
                // Anchor for stranded recovery: keep the link we joined through, domain-agnostic, so a
                // Refounding that leaves us out of the recipient set is recoverable later. See
                // recoverStrandedConcordCommunities().
                inviteRef = ConcordActions.bareInviteRef(url),
            )
        joinConcordCommunity(entry)
        return ConcordInviteResult.Joined(bundle.communityId)
    }

    /**
     * Post [text] to a Concord channel: derive the channel plane key, build an
     * encrypted-seal kind-1059 wrap authored by that plane key (not our identity),
     * fold it locally for an instant echo, and publish it to the community's relays.
     * The `p` tag is ephemeral, so this never routes through the DM outbox — it goes
     * straight to the community relay set. Returns false if not writeable or the
     * community isn't currently joined/folded.
     */
    suspend fun sendConcordChannelMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        replyTo: Note? = null,
        replyMode: ReplyMode = ReplyMode.INLINE,
        imetas: List<IMetaTag> = emptyList(),
    ): Boolean {
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)

        // NIP-30 custom-emoji tags for any `:shortcode:` the user typed, so the message renders the
        // custom image everywhere (the kind-9 rumor carries them; recipients render via the tags).
        val emojiTags =
            account.emoji
                .findEmojiTags(text)
                .map { it.toTagArray() }
                .toTypedArray()

        val parent = replyTo?.event
        val wrap =
            when {
                // A minichat reply is a kind-1111 thread comment (carrying encrypted image imetas when
                // the user attached media); an inline reply is a kind-9 message quoting the parent; a
                // fresh post is a plain kind-9 message.
                parent != null && replyMode == ReplyMode.MINICHAT && imetas.isNotEmpty() ->
                    ConcordActions.buildChannelImageReply(account.signer, channelKey, channelIdHex, entry.rootEpoch, parent, text, imetas, TimeUtils.now(), emojiTags)
                parent != null && replyMode == ReplyMode.MINICHAT ->
                    ConcordActions.buildChannelReply(account.signer, channelKey, channelIdHex, entry.rootEpoch, parent, text, TimeUtils.now(), emojiTags)
                parent != null ->
                    ConcordActions.buildChannelInlineReply(account.signer, channelKey, channelIdHex, entry.rootEpoch, parent, text, TimeUtils.now(), emojiTags)
                else ->
                    ConcordActions.buildChannelMessage(account.signer, channelKey, channelIdHex, entry.rootEpoch, text, TimeUtils.now(), emojiTags)
            }
        trackConcordDelivery(entry, channelKey, wrap)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Send a channel message carrying encrypted image attachments ([imetas], built by the composer
     * from the encrypted upload) — Armada's `encryptAttachments` shape. The ciphertext URLs are
     * appended to [text] and each rides as a NIP-92 `imeta` with `aes-gcm` decryption params. With no
     * attachments this is just a plain [sendConcordChannelMessage].
     */
    suspend fun sendConcordChannelImageMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        imetas: List<IMetaTag>,
    ): Boolean {
        if (imetas.isEmpty()) return sendConcordChannelMessage(communityId, channelIdHex, text)
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the caption, same as a plain message.
        val emojiTags =
            account.emoji
                .findEmojiTags(text)
                .map { it.toTagArray() }
                .toTypedArray()
        val wrap = ConcordActions.buildChannelImageMessage(account.signer, channelKey, channelIdHex, entry.rootEpoch, text, imetas, TimeUtils.now(), emojiTags)
        trackConcordDelivery(entry, channelKey, wrap)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * React to a Concord message with [reaction] (e.g. `"+"`, an emoji). Mirrors
     * [sendConcordChannelMessage]: builds a kind-7 rumor bound to the message's
     * channel/epoch, wraps it on the plane, and publishes it — so the reaction stays
     * inside the encrypted channel (never a plaintext public kind-7 that would leak
     * the message id). [note] must be a Concord channel message (carries a
     * [ConcordChannel] gatherer).
     */
    suspend fun reactToConcordMessage(
        note: Note,
        reaction: String,
    ): Boolean {
        if (!account.isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val entry = account.concordSessions.sessionFor(communityId)?.entry ?: return false

        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // A custom-emoji reaction is a `:shortcode:` content that needs its NIP-30 `emoji` tag to
        // resolve to an image on the other side; a plain unicode/`+` reaction yields no tags.
        val emojiTags =
            account.emoji
                .findEmojiTags(reaction)
                .map { it.toTagArray() }
                .toTypedArray()
        val wrap = ConcordActions.buildChannelReaction(account.signer, channelKey, channelIdHex, entry.rootEpoch, target, reaction, TimeUtils.now(), emojiTags)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Edit my own Concord channel message [note] to [newText]. Mirrors
     * [reactToConcordMessage]: builds a kind-3302 [ChannelChat.edit] rumor bound to the
     * message's channel/epoch, wraps it on the plane, and publishes it — so the edit stays
     * inside the encrypted channel (a public edit would e-tag the private rumor id onto
     * public relays). The receiving side overlays the newest edit onto the target message;
     * only the *original author's* edits are applied, so we gate to my own kind-9 messages.
     * Returns false if [note] isn't an editable Concord message I authored.
     */
    suspend fun editConcordChannelMessage(
        note: Note,
        newText: String,
    ): Boolean {
        if (!account.isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        // Edits only apply to plain kind-9 messages, and only the author may edit their own.
        if (target !is ChatEvent || target.pubKey != account.signer.pubKey) return false

        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val entry = account.concordSessions.sessionFor(communityId)?.entry ?: return false

        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the new text, same as a fresh message.
        val emojiTags =
            account.emoji
                .findEmojiTags(newText)
                .map { it.toTagArray() }
                .toTypedArray()
        val wrap = ConcordActions.buildChannelEdit(account.signer, channelKey, channelIdHex, entry.rootEpoch, target, newText, TimeUtils.now(), emojiTags)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Publish a typing heartbeat (kind-23311, ephemeral 21059) to a Concord channel — call at
     * most every few seconds while composing. Not folded locally (we never show our own typing);
     * ephemeral, so relays broadcast but never store it.
     */
    suspend fun sendConcordTyping(
        communityId: String,
        channelIdHex: String,
    ) {
        if (!account.isWriteable()) return
        val entry = account.concordSessions.sessionFor(communityId)?.entry ?: return
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        val wrap = ConcordActions.buildChannelTyping(account.signer, channelKey, channelIdHex, entry.rootEpoch, TimeUtils.now())
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /** Instant local echo (the session folds it back as a Note) + publish to the community relays. */
    private fun publishConcordWrap(
        entry: ConcordCommunityListEntry,
        wrap: Event,
    ) {
        account.concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) account.client.publish(wrap, relays)
    }

    /**
     * Registers an own Concord channel message with the delivery tracker so its chat
     * bubble shows relay-acceptance ticks. Relays OK the encrypted [wrap], but the feed
     * shows the inner rumor, so we re-open the wrap (we just built it, so this always
     * succeeds) to key the tracker by the rumor id the bubble is drawn from. Reactions
     * and typing wraps skip this — they never become a feed row.
     */
    private fun trackConcordDelivery(
        entry: ConcordCommunityListEntry,
        channelKey: GroupKey,
        wrap: Event,
    ) {
        val rumorId = ConcordStreamEnvelope.openOrNull(wrap, channelKey)?.rumor?.id ?: return
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        account.chatDeliveryTracker.trackWrappedPublic(rumorId, wrap.id, relays)
    }

    // ── Concord roles & moderation (CORD-04) ─────────────────────────────────
    // Each publishes a Control Plane edition; authority is enforced at fold time by
    // every client's AuthorityResolver, so a call by someone who doesn't outrank the
    // target is simply dropped on fold. Owner-authored calls always take effect.

    /**
     * The Control Plane keys for a moderation write, or null when this account cannot
     * publish there: on a split epoch only `control_root` holders can mint a wrap that
     * verifies at the plane's address (CORD-02 §2), and wrapping without the secret
     * throws rather than missigning. Rank and key possession can diverge — a freshly
     * promoted staffer writes only once their `control_wrap` is adopted (CORD-04 §3),
     * and the UI gates on rank — so every moderation verb no-ops through this check
     * instead of crashing on a rank-gated action.
     */
    private fun controlKeysForWrite(session: ConcordCommunitySession): ControlPlaneKeys? {
        val cp = session.controlPlaneKeys()
        if (!cp.canWrite) {
            Log.w("Concord") { "Control write refused for ${session.entry.id}: control_root not held at epoch ${session.entry.rootEpoch} (CORD-02 §2)" }
            return null
        }
        return cp
    }

    /** Grant [member] exactly [roleIds] (empty list revokes their roles). */
    suspend fun grantConcordRole(
        communityId: String,
        member: HexKey,
        roleIds: List<String>,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        // A Grant that first makes its member staff must deliver the control_root in the same
        // edition (CORD-04 §3) — grantWithStaffDelivery attaches the pairwise wrap when the
        // roles carry a Control-writing bit and we hold the secret to hand over.
        val wrap =
            ConcordModeration.grantWithStaffDelivery(
                actor = account.signer,
                controlPlane = cp,
                communityId = communityId.hexToByteArray(),
                member = member,
                roleIds = roleIds,
                current = session.controlEditions(),
                createdAt = TimeUtils.now(),
                owner = session.entry.owner,
                controlRoot = session.entry.controlRoot?.hexToByteArray(),
                epoch = session.entry.rootEpoch,
            )
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** The default community Admin role: position 1, holding every management + moderation permission. */
    private fun concordAdminRole() =
        RoleEntity(
            name = CONCORD_ADMIN_ROLE,
            position = 1,
            permissions =
                ConcordPermissions
                    .of(
                        ConcordPermissions.MANAGE_ROLES,
                        ConcordPermissions.MANAGE_CHANNELS,
                        ConcordPermissions.MANAGE_METADATA,
                        ConcordPermissions.KICK,
                        ConcordPermissions.BAN,
                        ConcordPermissions.MANAGE_MESSAGES,
                        ConcordPermissions.CREATE_INVITE,
                    ).toWire(),
        )

    /**
     * If [note] is a Concord channel message whose author the OWNER may toggle
     * "admin" on, returns `(communityId, memberHex, isAlreadyAdmin)`. Only the owner
     * qualifies — the Admin role sits at position 1 and the resolver requires the
     * granter to *strictly* outrank it, which only the owner (rank 0) does. Null for
     * the owner's own note, the owner as target, or a non-owner actor.
     */
    fun concordAdminTarget(note: Note): Triple<String, HexKey, Boolean>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == account.signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val session = account.concordSessions.sessionFor(communityId) ?: return null
        val state = session.state.value ?: return null
        // Rank alone isn't enough on a split epoch: the Grant edition takes the control_root
        // (CORD-02 §2), so don't offer an action the verb would refuse.
        if (!session.controlPlaneKeys().canWrite) return null
        if (state.authority.isOwner(author) || !state.authority.isOwner(account.signer.pubKey)) return null
        val adminRoleId =
            state.roles.entries
                .firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
                ?.key
        val isAdmin = adminRoleId != null && adminRoleId in state.authority.rolesOf(author)
        return Triple(communityId, author, isAdmin)
    }

    /** Promote [member] to the community Admin role, defining that role first if it doesn't exist yet. */
    suspend fun makeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false

        val existing =
            session.state.value
                ?.roles
                ?.entries
                ?.firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
        val roleIdHex =
            existing?.key ?: run {
                val roleId = RandomInstance.bytes(32)
                val roleWrap = ConcordModeration.defineRole(account.signer, cp, roleId, concordAdminRole(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
                publishConcordWrap(session.entry, roleWrap)
                roleId.toHexKey()
            }

        // Admin carries every management bit, so this Grant makes its member staff: it must
        // deliver the control_root alongside the rank (CORD-04 §3), or the new admin holds
        // authority it cannot publish under.
        val grantWrap =
            ConcordModeration.grantWithStaffDelivery(
                actor = account.signer,
                controlPlane = cp,
                communityId = communityId.hexToByteArray(),
                member = member,
                roleIds = listOf(roleIdHex),
                current = session.controlEditions(),
                createdAt = TimeUtils.now(),
                owner = session.entry.owner,
                controlRoot = session.entry.controlRoot?.hexToByteArray(),
                epoch = session.entry.rootEpoch,
            )
        publishConcordWrap(session.entry, grantWrap)
        return true
    }

    /** Revoke all roles from [member] (demote an admin back to a plain member). */
    suspend fun removeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        val grantWrap = ConcordModeration.grant(account.signer, cp, communityId.hexToByteArray(), member, emptyList(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, grantWrap)
        return true
    }

    /**
     * If [note] is a Concord channel message whose author this account is allowed to
     * ban — the actor outranks the target and holds the BAN permission, and the target
     * is neither the owner nor the actor — returns `(communityId, memberHex)`. Null
     * otherwise, so the UI offers Ban only where we are willing to act.
     *
     * The rank half is ours alone. CORD-04 rank-gates role grants (`canActOn`) but the
     * BANLIST is a single whole-list entity, so neither this client's fold nor Armada's
     * rank-checks the *contents* of a banlist edition — both gate only on the author's
     * BAN bit (Armada: `banlistGate` → `isAuthorized(.., Permissions.BAN)`, while its
     * role path uses the rank-aware `canActOnPosition`). A moderator's ban of an admin
     * above them is therefore *accepted* by every client today. Since we cannot refuse
     * such a ban without diverging from Armada, we at least refuse to author one — this
     * restricts what we write, never what we accept, so it cannot split consensus.
     * Enforcing it on the fold needs a spec change; see the QA plan's open findings.
     */
    fun concordBanTarget(note: Note): Pair<String, HexKey>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == account.signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val session = account.concordSessions.sessionFor(communityId) ?: return null
        val authority = session.state.value?.authority ?: return null
        // Rank alone isn't enough on a split epoch: the banlist edition takes the control_root
        // (CORD-02 §2), so don't offer an action the verb would refuse.
        if (!session.controlPlaneKeys().canWrite) return null
        if (authority.isOwner(author)) return null
        // The owner short-circuits rather than going through canActOn: canActOn starts at
        // hasPermission, which is false while banned, and a rogue BAN holder *can* currently put
        // the owner on the banlist (see the KDoc) — routing the owner through it would let them be
        // locked out of moderating their own community.
        val canBan = authority.isOwner(account.signer.pubKey) || authority.canActOn(account.signer.pubKey, author, ConcordPermissions.BAN)
        return if (canBan) communityId to author else null
    }

    /** Add [member] to the community banlist. */
    suspend fun banConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        val wrap = ConcordModeration.ban(account.signer, cp, communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Remove [member] from the community banlist. */
    suspend fun unbanConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        val wrap = ConcordModeration.unban(account.signer, cp, communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    // ── Concord refounding / rekey (CORD-06) ──────────────────────────────────
    // A ban is a soft removal — the banned member still holds the room key and can
    // still decrypt traffic; every client just declines to *show* their posts. A
    // Refounding is the hard removal: it rotates the community_root, so a removed
    // member's key stops working for anything published afterwards.

    /**
     * Remove [removed] from the community absolutely (CORD-06 Refounding): ban them,
     * roll the `community_root`, re-key every retained member (Guestbook membership ∪
     * observed authors ∪ the privileged roster ∪ self) via kind-3303 blobs, and republish the compacted
     * Control Plane under the new root. A removed member keeps the prior root (so
     * their history stays readable) but receives no blob, so they can never decrypt
     * anything published after the rotation.
     *
     * Requires ownership or the BAN permission; returns false otherwise (or if the
     * community isn't joined/writeable, or a target is the owner).
     */
    suspend fun refoundConcordCommunity(
        communityId: String,
        removed: Set<HexKey>,
    ): Boolean {
        if (!account.isWriteable()) return false
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        val state = session.state.value ?: return false
        val authority = state.authority
        val iCanBan = authority.isOwner(account.signer.pubKey) || authority.effectivePermissions(account.signer.pubKey).has(ConcordPermissions.BAN)
        if (!iCanBan) return false
        val removedLower = removed.mapTo(HashSet()) { it.lowercase() }
        if (removedLower.isEmpty() || removedLower.any { authority.isOwner(it) }) return false
        // A Refounding writes the current plane (the pre-rotation bans) and the new one (the
        // compaction), so on a split epoch it takes the current control_root (CORD-02 §2). A
        // rank-qualified refounder whose secret hasn't arrived yet must wait for re-delivery.
        val cp = controlKeysForWrite(session) ?: return false

        // 1. Ban the removed members on the current Control Plane so the compacted snapshot —
        //    and thus the new epoch — carries the ban. publishConcordWrap folds it in locally
        //    first, so each subsequent edition chains onto the updated banlist head.
        for (target in removedLower) {
            val banWrap = ConcordModeration.ban(account.signer, cp, communityId.hexToByteArray(), target, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
            publishConcordWrap(session.entry, banWrap)
        }

        // 2. Recipient set: everyone we're keeping, minus the removed and the already-banned.
        //    Uses allMembers() — Guestbook joins ∪ OBSERVED AUTHORS ∪ roster ∪ owner — not just the
        //    Guestbook set. Most members never send a Guestbook Join (Amethyst announces one, other
        //    clients need not), so building the set without observed authors silently expelled every
        //    member who had only ever posted: they hold no role, receive no blob, and the Refounding
        //    strands them. That mainly hit cross-client communities, where Armada members are the
        //    bulk of the roster.
        //
        //    Still a floor, not a census (see allMembers): a member who joined without a Guestbook
        //    motion, holds no role, and has never posted leaves no trace to find, so a Refounding
        //    cannot re-key them. Stranded recovery is what gets those members back.
        val recipients =
            (session.allMembers() + account.signer.pubKey)
                .mapTo(HashSet()) { it.lowercase() }
                .apply {
                    removeAll(removedLower)
                    removeAll(authority.bannedMembers())
                }.toList()

        // 3. Build the refounding: new root, compacted Control Plane, per-recipient rekey blobs.
        val entry = session.entry
        val newRoot = RandomInstance.bytes(32)
        // A fresh control_root is minted beside the new root at every Refounding (CORD-02 §2),
        // so a demoted staffer's retained secret dies with the epoch — and a legacy community
        // upgrades to the split as a side effect of its next ban (CORD-06 §3).
        val newControlRoot = RandomInstance.bytes(32)
        // The staff set the new secret goes to: the owner plus everyone holding a
        // Control-writing bit (CORD-04 §3). They get the 136-byte blob, every other
        // recipient the 104-byte one carrying the pubkey alone. (The builder mints a
        // blob per recipient, so staff who aren't recipients are simply never reached.)
        val staff = authority.staffMembers()
        val build =
            ConcordActions.buildRefounding(
                rotatorSigner = account.signer,
                communityId = communityId,
                priorRoot = entry.root.hexToByteArray(),
                newRoot = newRoot,
                newControlRoot = newControlRoot,
                rootEpoch = entry.rootEpoch,
                priorControlWraps = session.controlPlaneWraps(),
                priorControlKeys = cp,
                recipientsXOnly = recipients,
                staffXOnly = staff,
                createdAt = TimeUtils.now(),
            )

        // 4. Publish the compacted Control Plane (the new epoch's state) then the rekey blobs
        //    (the key that unlocks it) to the community relays.
        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (publishTo.isNotEmpty()) {
            build.controlWraps.forEach { account.client.publish(it, publishTo) }
            build.rekeyWraps.forEach { account.client.publish(it, publishTo) }
        }

        // 5. Adopt the new epoch ourselves. This rebuilds our session under the new root and
        //    re-folds the compacted Control Plane (with the ban), dropping the removed members.
        adoptConcordRoot(entry, newRoot, build.newEpoch, build.newControlKeys.address.hexToByteArray(), newControlRoot)
        return true
    }

    // Rotations we've already adopted ("communityId:epoch"), so a base-rekey wrap still buffered
    // in the pre-rebuild window (the session rebuild off `liveCommunities` is async) is not
    // adopted — and re-published — twice on successive revision ticks.
    private val adoptedConcordRotations = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Persist a rotated access root/epoch for [entry], keeping the prior root as a
     * [HeldRoot], and re-announce our Guestbook membership at the new epoch so the
     * fresh epoch's Guestbook re-seeds (a later Refounding re-keys that membership —
     * without this, cascading removals would lose everyone but the roster). No-op if
     * this exact rotation was already adopted.
     */
    private suspend fun adoptConcordRoot(
        entry: ConcordCommunityListEntry,
        newRoot: ByteArray,
        newEpoch: Long,
        newControlPk: ByteArray? = null,
        newControlRoot: ByteArray? = null,
    ) {
        if (!adoptedConcordRotations.add("${entry.id}:$newEpoch")) return
        // The epoch we're leaving is banked with the address it was folded at, so its Control
        // Plane stays subscribable for the anti-rollback floor (a split epoch's address can
        // never be re-derived, only remembered — CORD-02 §2).
        val held = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root, entry.controlPk, entry.controlRoot)).distinctBy { it.epoch }
        val next =
            ConcordCommunityListEntry(
                id = entry.id,
                owner = entry.owner,
                ownerSalt = entry.ownerSalt,
                root = newRoot.toHexKey(),
                rootEpoch = newEpoch,
                // A rotation that delivered no control material is a legacy, pre-split one
                // (CORD-06 §3): the new epoch keeps folding at the legacy address, and the
                // stale prior-epoch values must NOT be carried into it.
                controlPk = newControlPk?.toHexKey(),
                controlRoot = newControlRoot?.toHexKey(),
                heldRoots = held,
                privateChannels = entry.privateChannels,
                relays = entry.relays,
                name = entry.name,
                addedAt = entry.addedAt,
                // The invite_ref anchor must survive a rotation, or the *next* Refounding we're left
                // out of would be unrecoverable.
                inviteRef = entry.inviteRef,
                excludedAtEpoch = entry.excludedAtEpoch,
                // Unknown keys another client wrote (Armada's list is `[k: string]: unknown`)
                // must survive our rotation write, or we delete their data on every rekey.
                residue = entry.residue,
            )
        account.sendMyPublicAndPrivateOutbox(account.concordChannelList.follow(next))
        announceConcordGuestbookJoin(next, inviteCreator = null, inviteLabel = null)
    }

    /**
     * Drain any buffered inbound base-rotation rekeys (CORD-06 receive path): for
     * each joined community, look for our new root among the kind-3303 wraps seen at
     * our next base-rekey address. If a role-authorized rotator (owner or a current,
     * non-banned BAN-holder) delivered us one, adopt it. Idempotent — once adopted, the
     * session rebuilds at the new epoch and its next-rekey address moves on, so a stale
     * wrap never re-triggers. Called on every Concord revision tick.
     *
     * Authority is the roster, never key possession: any non-banned BAN-holder may
     * rotate, including for the owner. The owner deliberately does NOT refuse a root
     * authored by someone else — refusing would strand the owner alone on the dead
     * epoch whenever an admin legitimately rotates, and would diverge from Armada,
     * which forks a community across clients. Self-escalation to BAN is prevented
     * upstream by the role rank gate in AuthorityResolver.
     *
     * A rotation carries only (newRoot, newEpoch, rotator); there is no recipient list,
     * so a receiver cannot tell who was left out, and a BAN-holder can evict anyone (the
     * owner included) by omission — nothing on this receive path can prevent it. The
     * cure is after the fact: see [recoverStrandedConcordCommunities], which re-resolves
     * the invite link the membership was joined through and merges forward.
     */
    internal suspend fun drainConcordRekeys() {
        if (!account.isWriteable()) return
        for (session in account.concordSessions.sessions()) {
            val wraps = session.pendingBaseRekeyWraps()
            if (wraps.isEmpty()) continue
            val entry = session.entry
            val received =
                ConcordActions.openBaseRekey(
                    wraps = wraps,
                    baseRekey = session.nextBaseRekeyKey(),
                    recipientSigner = account.signer,
                    communityId = entry.id,
                    priorRoot = entry.root.hexToByteArray(),
                    rootEpoch = entry.rootEpoch,
                ) ?: continue
            if (received.newEpoch <= entry.rootEpoch) continue
            val authority = session.state.value?.authority ?: continue

            // hasPermission, not effectivePermissions: the latter ignores the banlist, so a BAN-holder
            // who has themselves been banned could still rotate the whole community.
            val authorized = authority.isOwner(received.rotator) || authority.hasPermission(received.rotator, ConcordPermissions.BAN)
            if (!authorized) continue
            adoptConcordRoot(entry, received.newRoot, received.newEpoch, received.newControlPk, received.newControlRoot)
        }
    }

    /**
     * Adopt a `control_root` delivered to us by a staff-making Grant (CORD-04 §3): the
     * promoting edition carries the secret in `control_wrap`, NIP-44-encrypted under the
     * granter↔member pairwise key, so promotion and key delivery are one signed edition
     * with nothing separate to watch an inbox for.
     *
     * Adoption is gated twice and fails closed both times. The secret is adopted only if
     * it derives to exactly the `control_pk` we already hold for the named epoch — a
     * garbage wrap is attributable griefing, nothing worse — and only from a Grant our own
     * fold honors, so a rogue cannot feed us a key by minting an edition nobody accepts.
     * The epoch check matters because compaction re-wraps a Grant head verbatim across
     * Refoundings, so a folded head can legitimately carry a wrap minted for a prior epoch.
     *
     * Idempotent: once the entry holds the secret there is nothing to adopt. Runs on the
     * revision tick, like the rekey drain.
     */
    internal suspend fun drainConcordStaffGrants() {
        if (!account.isWriteable()) return
        val me = account.signer.pubKey.lowercase()
        for (session in account.concordSessions.sessions()) {
            val entry = session.entry
            // Already staff at this epoch, or a legacy community with no split to join.
            val heldControlPk = entry.controlPk
            if (entry.controlRoot != null || heldControlPk == null) continue
            val state = session.state.value ?: continue
            // Only a Grant our fold honors can deliver: an unauthorized edition hands us nothing.
            if (!state.authority.isStaff(me)) continue

            val myGrantCoordinate =
                ConcordKeyDerivation
                    .grantCoordinate(entry.id.hexToByteArray(), me.hexToByteArray())
                    .toHexKey()
            val delivered =
                session
                    .controlEditions()
                    .filter { it.entityKind == ControlEntityKind.GRANT && it.entityIdHex == myGrantCoordinate }
                    // Newest first: a re-issued Grant (a lost key, a head superseded before we
                    // fetched it) carries the fresher wrap.
                    .sortedByDescending { it.version }
                    .firstNotNullOfOrNull { edition ->
                        val wrap = ConcordJson.decodeOrNull<GrantEntity>(edition.content)?.controlWrap ?: return@firstNotNullOfOrNull null
                        val opened = ControlRootWrap.openOrNull(wrap, account.signer, edition.author) ?: return@firstNotNullOfOrNull null
                        if (opened.epoch != entry.rootEpoch) return@firstNotNullOfOrNull null
                        // Fails closed: a secret that doesn't derive to the pk we hold is dropped,
                        // never adopted — we will not split ourselves off from the plane's readers.
                        if (!ControlRootWrap.derivesTo(opened.controlRoot, entry.id.hexToByteArray(), entry.rootEpoch, heldControlPk)) return@firstNotNullOfOrNull null
                        opened.controlRoot
                    } ?: continue

            account.sendMyPublicAndPrivateOutbox(
                account.concordChannelList.follow(entry.withControlRoot(delivered.toHexKey())),
            )
        }
    }

    // Last time we re-resolved each community's invite_ref, so the recovery sweep rides the
    // Concord revision tick (which fires on every structural change) without turning it into a
    // relay-fetch loop.
    private val lastConcordRecoveryCheck = ConcurrentHashMap<String, Long>()

    /**
     * Stranded recovery (CORD-05/06 receive path). A Refounding carries only
     * `(newRoot, newEpoch, rotator)` — **no recipient list** — so a member simply left
     * out of the rekey recipient set receives nothing and sits on the dead epoch
     * forever while everyone else moves on. This happens to any member, the owner
     * included, and [drainConcordRekeys] cannot prevent it: there is no message to
     * miss detecting.
     *
     * The way back is the invite link the membership was joined through
     * ([ConcordCommunityListEntry.inviteRef], persisted by [joinConcordViaInvite] and
     * carried through every rotation by [adoptConcordRoot]). The community keeps
     * re-minting its bundle at that same addressable coordinate, so a bundle there at
     * a **strictly higher** epoch than ours proves we were left behind — and carries
     * the new root. Same or lower epoch is a no-op. Memberships with no link (direct
     * invites, legacy entries) are inert here; that is expected, not an error.
     *
     * The merge itself ([ConcordActions.recoverStranded]) is epoch-monotonic and keeps
     * both the `invite_ref` anchor (so the *next* exclusion is recoverable too) and the
     * entry's [HeldRoot]s (so prior-epoch history the member legitimately holds stays
     * derivable). We then re-announce the Guestbook at the new epoch, exactly as an
     * ordinary rotation does, so the recovered member is visible to whoever refounds
     * next instead of being silently dropped again.
     *
     * Called on the Concord revision tick, but rate-limited per community
     * ([RECOVERY_CHECK_INTERVAL_MS]) — a tick with nothing to do costs a map lookup.
     */
    internal suspend fun recoverStrandedConcordCommunities() {
        if (!account.isWriteable()) return
        val now = TimeUtils.nowMillis()
        for (entry in account.concordChannelList.liveCommunities.value) {
            val inviteRef = entry.inviteRef ?: continue
            val last = lastConcordRecoveryCheck[entry.id]
            if (last != null && now - last < RECOVERY_CHECK_INTERVAL_MS) continue
            lastConcordRecoveryCheck[entry.id] = now

            val parsed = ConcordActions.parseInviteLink(inviteRef) ?: continue
            val relays =
                (
                    parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } +
                        entry.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                ).toSet()
            if (relays.isEmpty()) continue

            val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
            val wraps = account.client.fetchAll(filters = filters)
            // Only a live bundle recovers: an expired/revoked link is not a rotation we missed.
            val bundle = (ConcordActions.classifyInvite(wraps, parsed.fragment.token) as? InviteBundleStatus.Live)?.invite ?: continue

            val merged = ConcordActions.recoverStranded(entry, bundle) ?: continue
            if (!adoptedConcordRotations.add("${entry.id}:${merged.rootEpoch}")) continue
            Log.i("Concord", "Stranded recovery: ${entry.id} ${entry.rootEpoch} -> ${merged.rootEpoch}")
            account.sendMyPublicAndPrivateOutbox(account.concordChannelList.follow(merged))
            announceConcordGuestbookJoin(merged, inviteCreator = null, inviteLabel = null)
        }
    }

    /**
     * Replace the community metadata (name / icon / description / relays) with a new
     * Control-Plane edition. Honored on fold only when this account holds
     * MANAGE_METADATA (or is the owner); dropped otherwise, like every other edition.
     */
    suspend fun editConcordMetadata(
        communityId: String,
        name: String,
        description: String?,
        icon: ImagePointer?,
        banner: ImagePointer?,
        relays: List<String>,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        val metadata = MetadataEntity(name = name, icon = icon, banner = banner, description = description, relays = relays)
        val wrap = ConcordModeration.editMetadata(account.signer, cp, communityId.hexToByteArray(), metadata, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Create a new public text channel in [communityId] (CORD-03/04 channel edition). Honored at fold
     * only when this account holds MANAGE_CHANNELS (or is the owner); the button should be gated on
     * the same predicate. The channel id is a fresh random 32-byte entity id.
     */
    suspend fun createConcordChannel(
        communityId: String,
        name: String,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        val channelId = RandomInstance.bytes(32)
        val channel = ChannelEntity(name = name.trim())
        val wrap = ConcordModeration.defineChannel(account.signer, cp, channelId, channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Rename an existing channel (chains the next channel edition onto its head). MANAGE_CHANNELS only. */
    suspend fun renameConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        // Carry the standing definition forward and change only the name. A ChannelEntity built from
        // scratch defaults `private` and `voice` to false, so renaming a private channel used to
        // publish an edition declaring it PUBLIC — and a voice channel became a text channel.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = ChannelEntity(name = name.trim(), private = standing?.private ?: false, voice = standing?.voice ?: false)
        val wrap = ConcordModeration.defineChannel(account.signer, cp, channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Delete (tombstone) a channel — terminal; its id is never reused. MANAGE_CHANNELS only. */
    suspend fun deleteConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = account.concordSessions.sessionFor(communityId) ?: return false
        if (!account.isWriteable()) return false
        val cp = controlKeysForWrite(session) ?: return false
        // Same as rename: preserve the standing flags so a tombstone does not also silently
        // reclassify the channel it retires.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = ChannelEntity(name = name.trim(), private = standing?.private ?: false, voice = standing?.voice ?: false, deleted = true)
        val wrap = ConcordModeration.defineChannel(account.signer, cp, channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Read-only preview of an invite link: parse it, fetch the kind-33301 bundle from
     * the link's relays (+ our outbox), and unlock it with the fragment token — WITHOUT
     * joining. Returns the [CommunityInvite] (name, relays, community coordinates) so a
     * card can show what the link opens, or null if the link is invalid/unreadable.
     */
    suspend fun peekConcordInvite(url: String): CommunityInvite? {
        val parsed = ConcordActions.parseInviteLink(url) ?: return null
        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + account.outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return null
        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = account.client.fetchAll(filters = filters)
        return wraps.firstNotNullOfOrNull { ConcordActions.openBundle(it, parsed.fragment.token) }
    }

    /**
     * Bootstrap the Concord hub from the network: fetch this account's kind-13302
     * joined-communities list and fold the newest into [LocalCache], so communities
     * we joined on another Concord client with this key surface here.
     *
     * We query a wide relay set because different Concord clients publish this
     * private list to different places: the reference clients (Armada/Vector) push
     * it to the Concord **stock relays** (e.g. relay.ditto.pub), while a user may
     * also have copied it onto their **own** outbox/read relays. Our normal account
     * subscription never asks for kind 13302, so without this explicit fetch a
     * community joined on Armada would never appear — even if the list sits on the
     * user's own outbox.
     *
     * Read-only import: kind 13302 is replaceable, so folding an older copy is a
     * no-op and this is safe to call on every hub open. Merging our own edits with
     * a foreign writer's is a separate concern (newest-wins replaceable).
     *
     * [extraRelays] are additional relays to query — the bootstrap relays saved on the
     * bottom-bar tabs of pinned communities. A community's private list frequently lives
     * only on the community's own relays (never the user's outbox), so a community pinned
     * to the bottom bar would otherwise never surface when opened cold.
     */
    suspend fun importConcordCommunities(extraRelays: Set<NormalizedRelayUrl> = emptySet()) {
        val stock = InviteRelayDictionary.STOCK.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        val relays = (stock + account.mineRelays.flow.value + account.outboxRelays.flow.value + extraRelays).toSet()
        if (relays.isEmpty()) return
        val filter = Filter(kinds = listOf(ConcordCommunityListEvent.KIND), authors = listOf(account.signer.pubKey))
        // Stock relays like relay.ditto.pub can be slow (~10–20s to first response), so give
        // the fetch a generous window to drain every relay before we pick the newest copy.
        val events = account.client.fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = 30_000L)
        val newest = events.filterIsInstance<ConcordCommunityListEvent>().maxByOrNull { it.createdAt }
        val entryCount = newest?.let { runCatching { it.decrypt(account.signer).size }.getOrElse { -1 } } ?: 0
        Log.d(
            "Concord",
            "importConcordCommunities: queried ${relays.size} relays, fetched ${events.size} 13302 event(s), " +
                "newest=${newest?.id?.take(8)}@${newest?.createdAt}, decoded $entryCount entr${if (entryCount == 1) "y" else "ies"}",
        )
        newest?.let { account.cache.justConsumeMyOwnEvent(it) }
    }

    /**
     * One-shot warm of every channel of [entries] so a community's channel list and the Messages inbox
     * fill in without the user opening each channel one by one. Per channel, a channel read before is
     * caught up from its last-read time (accurate unread badge + the missed messages ready when it
     * opens) while a channel never read pulls only its single newest wrap for a preview — see
     * [ConcordSubscriptionPlanner.channelPreviewFilters].
     *
     * This is deliberately **not** a live subscription: every wrap the drain pulls flows through the
     * global cache connector (`CacheClientConnector` → `LocalCache.justConsume` → `concordSessions.ingest`),
     * so it lands in the channel's message store the previews/unread counts read — and the always-on
     * plane subscription ([RelaySubscriptionsCoordinator.concordChannels]) keeps them fresh afterward.
     * So this only needs to run when a community's channels first fold (the account preload) or its
     * screen is opened. One drain per call: all [entries]' per-channel filters are grouped by relay.
     */
    suspend fun warmConcordChannelPreviews(entries: List<ConcordCommunityListEntry>) {
        val filters =
            entries.flatMap { entry ->
                val state =
                    account.concordSessions
                        .sessionFor(entry.id)
                        ?.state
                        ?.value ?: return@flatMap emptyList()
                ConcordSubscriptionPlanner.channelPreviewFilters(
                    entry,
                    state,
                    lastReadFor = { channelIdHex ->
                        account.loadLastRead(concordChannelLastReadRoute(entry.id, channelIdHex))
                    },
                    accountPubKey = account.userProfile().pubkeyHex,
                )
            }
        if (filters.isEmpty()) return
        val byRelay = filters.groupBy { it.relay }.mapValues { (_, group) -> group.map { it.filter } }
        account.client.fetchAll(filters = byRelay, idleTimeoutMs = 20_000L)
    }

    /**
     * COMPLETE-mode Control-Plane sync — Armada's plane-sweep discipline for the one plane that must
     * never fold on a truncated edition set.
     *
     * The Control Plane defines the channel list, the roster and the banlist, so a *partial* fold
     * silently drops channels or mis-renders membership. Two ways that happens, both closed here:
     *  - **Forward-cursor gap:** the live plane subscription advances a `since` cursor, so an edition
     *    with a `created_at` below the high-water mark that we never actually ingested — an unban
     *    published while we were offline, a CORD-06 compaction re-wrap under a newly-held epoch — is
     *    never asked for again and stays invisible. This sweep uses **no `since`**: it re-fetches the
     *    whole plane every run.
     *  - **Per-filter cap:** a relay caps a REQ's result (~100/filter on relay.dreamith.to), which can
     *    crop a busy Control Plane. This **pages past the cap** ([fetchAllPagesFromPool] walks `until`
     *    cursors until a plane is drained), so the fold sees every edition regardless of the cap.
     *
     * Current + every held-prior epoch's Control Plane is swept (the anti-rollback floor folds from the
     * priors). Wraps ingest through the global cache connector → [concordSessions] like every other
     * Concord drain; AUTH is the shared stream-key handler. Merging communities that share a relay into
     * one filter is safe here precisely because we page — the cap no longer truncates. The live control
     * subscription still carries brand-new editions in real time; this is the periodic completeness pass.
     */
    suspend fun syncConcordControlPlanes(entries: List<ConcordCommunityListEntry>) {
        if (entries.isEmpty()) return
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableSet<String>>()
        for (entry in entries) {
            for (sub in ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry))) {
                for (relay in sub.relays) authorsByRelay.getOrPut(relay) { HashSet() }.add(sub.pubKeyHex)
            }
        }
        if (authorsByRelay.isEmpty()) return
        // No `since`, no `limit` → fetchAllPages treats each filter as unbounded and pages until a
        // plane is fully drained (empty page), so the whole Control Plane lands regardless of the cap.
        val byRelay = authorsByRelay.mapValues { (_, authors) -> listOf(ConcordActions.planeFilterFor(authors.toList())) }
        var drained = 0
        account.client.fetchAllPagesFromPool(filters = byRelay) { _, _ -> drained++ }
        Log.d("Concord", "syncConcordControlPlanes: paged ${authorsByRelay.size} relay(s), drained $drained control wrap(s)")
    }
}
