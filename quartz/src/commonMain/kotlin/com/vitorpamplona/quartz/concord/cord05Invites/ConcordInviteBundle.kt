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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.vsk
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * What the events fetched at an invite's addressable coordinate `(33301,
 * link_signer, d="")` actually resolve to under CORD-05 §2. The coordinate is
 * replaceable, so its live state is the **newest** event: a revocation tombstone
 * (`vsk=9`) supersedes and buries the bundle even if an older openable copy still
 * lingers on some relay, which is exactly the "fetcher finds the grave instead of
 * keys" behaviour the spec mandates.
 */
sealed interface InviteBundleStatus {
    /** A live `vsk=6` bundle that opened with the link token. */
    data class Live(
        val invite: CommunityInvite,
    ) : InviteBundleStatus

    /** The newest event at the coordinate is a `vsk=9` revocation tombstone — the link was retired. */
    data object Revoked : InviteBundleStatus

    /**
     * A `vsk=6` bundle that opened and validated, but whose `expires_at` is in the past.
     * The [invite] is still carried so a preview can render what the link *would* have
     * opened; joining must be refused (CORD-05 — an expiry that nobody enforces is
     * decorative).
     */
    data class Expired(
        val invite: CommunityInvite,
    ) : InviteBundleStatus

    /**
     * Something is at the coordinate, but it isn't a `vsk=6` bundle this client can open —
     * a wrong/expired token, or a sub-kind (e.g. a mis-posted registry `vsk=8`) or format
     * newer than we support.
     */
    data object Unreadable : InviteBundleStatus

    /** Nothing was found at the coordinate on any queried relay (unreachable, expired, or not yet propagated). */
    data object Absent : InviteBundleStatus
}

/** A freshly minted public invite link: the shareable URL, the link keys, and the bundle to publish. */
class MintedInviteLink(
    val url: String,
    val linkSignerPubKey: String,
    val linkSignerPrivKey: ByteArray,
    val token: ByteArray,
    val bundleEvent: Event,
)

/**
 * The public invite bundle (CORD-05): a kind-33301 addressable event whose
 * content is the [CommunityInvite] NIP-44-encrypted under the bundle key derived
 * from the link's 16-byte unlock token. The event is signed by a per-link
 * `link_signer` keypair (so re-posting refreshes keys) and tagged
 * `["d",""],["vsk","6"]`.
 *
 * A server that indexes the naddr never holds the token, so it can never open the
 * bundle. Pinned to the Concord v2 reference client.
 */
object ConcordInviteBundle {
    const val KIND = ConcordInviteBundleEvent.KIND

    /**
     * A bundle naming more Channels than this is refused before anything is allocated for it
     * (CORD-05 §1: "reject a bundle carrying more than a sane channel count (Vector's ceiling is
     * 256)"). A bundle is attacker-crafted input reached by following a link.
     */
    const val MAX_BUNDLE_CHANNELS = 256

    /**
     * A bundle's `relays` are truncated to the Community's relay cap before anything connects to
     * them (CORD-05 §1, CORD-02 §6's "up to 5"): a hostile link must not be a connect storm.
     */
    const val MAX_COMMUNITY_RELAYS = 5

    /**
     * Bounds an attacker-crafted [invite] (CORD-05 §1 MUST): null when it names more than
     * [MAX_BUNDLE_CHANNELS] Channels, otherwise the invite with `relays` de-duplicated and
     * truncated to [MAX_COMMUNITY_RELAYS]. Every redeem path — link bundle, Direct Invite —
     * goes through [validate], which applies this.
     */
    fun bound(invite: CommunityInvite): CommunityInvite? {
        if (invite.channels.size > MAX_BUNDLE_CHANNELS) return null
        val relays =
            invite.relays
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_COMMUNITY_RELAYS)
        return if (relays == invite.relays) invite else invite.copy(relays = relays)
    }

    /**
     * True when [event] is really the bundle coordinate `(33301, linkSigner, d="")` (CORD-05 §2):
     * the right kind, authored by [linkSignerPubKey], an empty `d`, and a valid signature. A relay
     * filter is a hint, not a proof — a relay (or anyone who can write to one) could otherwise
     * serve a forged newer `vsk 9` and revoke a link it never owned.
     */
    fun isAtCoordinate(
        event: Event,
        linkSignerPubKey: HexKey,
    ): Boolean {
        if (event.kind != KIND) return false
        if (!event.pubKey.equals(linkSignerPubKey, ignoreCase = true)) return false
        val d = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "d" }?.getOrNull(1) ?: ""
        if (d != "") return false
        return event.verify()
    }

    private fun json(invite: CommunityInvite) = ConcordJson.instance.encodeToString(CommunityInvite.serializer(), invite)

    /** Builds a kind-33301 bundle event carrying [invite], encrypted under [token] and signed by [linkSignerPrivKey]. */
    fun build(
        linkSignerPrivKey: ByteArray,
        token: ByteArray,
        invite: CommunityInvite,
        createdAt: Long,
    ): Event {
        val bundleKey = ConcordKeyDerivation.inviteBundleKey(token)
        val content = Nip44.v2.encrypt(json(invite), bundleKey).encodePayload()
        val signer = NostrSignerSync(KeyPair(privKey = linkSignerPrivKey))
        return signer.sign(ConcordInviteBundleEvent.build(content, createdAt))
    }

    /**
     * Builds the kind-33301 revocation tombstone that retires the link owned by [linkSignerPrivKey]
     * (CORD-05 §2). It re-posts the link's own coordinate with empty content and `vsk=9`, so the
     * newest event there is a grave rather than keys and [classify] resolves the link
     * [InviteBundleStatus.Revoked] for everyone who resolves it afterwards.
     *
     * Only the creator can do this: the coordinate is addressable and authored by the link signer,
     * so retiring a link requires the `link_signer` secret — which lives in the creator's kind-13303
     * Invite List and nowhere else. Losing that secret makes a link permanently un-revokable, which
     * is why the list is written before a link is ever handed out.
     */
    fun buildRevocation(
        linkSignerPrivKey: ByteArray,
        createdAt: Long,
    ): Event = NostrSignerSync(KeyPair(privKey = linkSignerPrivKey)).sign(ConcordInviteBundleEvent.buildRevocation(createdAt))

    /**
     * Decrypts a kind-33301 bundle [event] with the link [token], or null if it isn't a valid bundle.
     * The result is already [bound]ed (CORD-05 §1), so an over-long relay list never reaches a caller.
     */
    fun parse(
        event: Event,
        token: ByteArray,
    ): CommunityInvite? {
        if (event.kind != KIND) return null
        return try {
            val bundleKey = ConcordKeyDerivation.inviteBundleKey(token)
            ConcordJson.decodeOrNull<CommunityInvite>(Nip44.v2.decrypt(event.content, bundleKey))?.let { bound(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves the events fetched at an invite's addressable coordinate into a single
     * [InviteBundleStatus] under CORD-05 §2 replaceable semantics. The newest event
     * wins: a `vsk=9` revocation tombstone marks the link [InviteBundleStatus.Revoked]
     * even when an older, still-openable bundle is also present (so a stale relay copy
     * can't resurrect a retired link). Otherwise the **newest** `vsk=6` bundle that opens +
     * validates with [token] is [InviteBundleStatus.Live]; anything else present is
     * [InviteBundleStatus.Unreadable], and an empty set is [InviteBundleStatus.Absent].
     *
     * An opened bundle whose `expires_at` has passed (compared against [nowMs], unix
     * milliseconds) resolves to [InviteBundleStatus.Expired] rather than
     * [InviteBundleStatus.Live], so the expiry is actually enforced at the one place
     * every redeeming client already funnels through.
     *
     * Only events really at the coordinate count ([isAtCoordinate]: kind, author ==
     * [linkSignerPubKey], `d == ""`, valid signature) — the relay filter is not trusted, so a
     * forged newer revocation cannot kill a link, nor a forged bundle hijack one.
     */
    fun classify(
        wraps: List<Event>,
        linkSignerPubKey: HexKey,
        token: ByteArray,
        nowMs: Long = TimeUtils.nowMillis(),
    ): InviteBundleStatus {
        val genuine = wraps.filter { isAtCoordinate(it, linkSignerPubKey) }
        return classifyGenuine(genuine, token, nowMs)
    }

    private fun classifyGenuine(
        wraps: List<Event>,
        token: ByteArray,
        nowMs: Long,
    ): InviteBundleStatus {
        val newest = wraps.maxByOrNull { it.createdAt } ?: return InviteBundleStatus.Absent
        if (newest.tags.vsk() == ControlEntityKind.INVITE_REVOKED) return InviteBundleStatus.Revoked
        // Newest-first, not fetch order. [wraps] arrives straight off `fetchAll`, i.e. in relay
        // arrival order, so opening whichever copy decrypts first is a coin flip between editions.
        // That matters because a Refounding re-mints every live link at its OWN coordinate with the
        // new epoch's root: a relay still serving the pre-Refounding bundle would otherwise hand the
        // joiner the root of the epoch the community just left, and they would join, see planes
        // nobody reads, and get no error saying why. The revocation branch above already resolves
        // newest-wins; the live branch has to agree with it.
        val invite = wraps.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { parse(it, token)?.takeIf { i -> validate(i) } }
        return when {
            invite == null -> InviteBundleStatus.Unreadable
            isExpired(invite, nowMs) -> InviteBundleStatus.Expired(invite)
            else -> InviteBundleStatus.Live(invite)
        }
    }

    /**
     * Validates that an [invite]'s owner + salt actually reproduce its community_id
     * (CORD-02 self-certification), so a bundle cannot smuggle a false OWNER.
     *
     * It does NOT bind `community_root`, and nothing here proves the bundle's minter is
     * a member of the community it names. `community_id` commits only to (owner, salt) —
     * both public in any invite — so an attacker can mint a bundle carrying a real
     * community's id, owner and salt alongside a root of their own. A joiner adopts that
     * root, believes they are in the real community, and posts into planes the attacker
     * can read.
     *
     * This is a CORD-05 limitation rather than an implementation gap: Armada's
     * `validateBundle` checks exactly the same thing and likewise leaves the root
     * unbound, so a stricter unilateral rule here would break interop without
     * protecting anyone. Verifying the adopted root's control plane does not close it
     * either — sealed editions carry the owner's own signature, so an attacker can
     * re-wrap genuine owner editions into their plane, which is precisely what a
     * legitimate compaction does. Closing it needs a spec change: commit the root into
     * the self-certifying id, or require the bundle to be signed by a roster-authorized
     * member. Raise with the Concord/Armada authors before diverging.
     */
    fun validate(invite: CommunityInvite): Boolean {
        if (invite.channels.size > MAX_BUNDLE_CHANNELS) return false
        val owner = invite.owner.hexToByteArrayOrNull() ?: return false
        val salt = invite.ownerSalt.hexToByteArrayOrNull() ?: return false
        return ConcordKeyDerivation.communityId(owner, salt).toHexKey() == invite.communityId
    }

    /** True if the invite has an expiry in the past (blocks joining; preview still renders). Time in unix ms. */
    fun isExpired(
        invite: CommunityInvite,
        nowMs: Long,
    ): Boolean = invite.expiresAt?.let { it < nowMs } ?: false

    /**
     * Mints a complete public invite link for [invite]: generates a fresh 16-byte
     * token and a per-link signer, builds the bundle event and the shareable
     * `{base}/invite/{naddr}#{fragment}` URL (with optional bootstrap [relays]).
     */
    fun mintLink(
        base: String,
        invite: CommunityInvite,
        createdAt: Long,
        relays: List<String>? = null,
    ): MintedInviteLink {
        val token = RandomInstance.bytes(16)
        val linkSigner = KeyPair()
        val bundleEvent = build(linkSigner.privKey!!, token, invite, createdAt)
        val url = ConcordInviteLink.buildUrl(base, linkSigner.pubKey.toHexKey(), token, relays)
        return MintedInviteLink(url, linkSigner.pubKey.toHexKey(), linkSigner.privKey, token, bundleEvent)
    }
}
