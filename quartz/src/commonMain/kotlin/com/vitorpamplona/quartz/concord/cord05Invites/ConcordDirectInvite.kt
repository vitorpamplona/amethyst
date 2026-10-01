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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Direct Invite opened by its recipient (CORD-05 §6): the bundle plus the seal-verified [sender].
 *
 * [invite] is already bounded and owner-proof validated ([ConcordInviteBundle.validate]); expiry is
 * NOT enforced here — a parked invite still renders, only joining refuses ([isExpired]).
 * [sentAt] is the rumor's `created_at` (unix seconds), the sender's word: fine for ordering, never
 * for authority.
 */
class OpenedDirectInvite(
    val wrapId: HexKey,
    val sender: HexKey,
    val invite: CommunityInvite,
    val sentAt: Long,
) {
    /** True when the bundle's `expires_at` (unix ms) has passed: the preview renders, joining refuses. */
    fun isExpired(nowMs: Long = TimeUtils.nowMillis()): Boolean = ConcordInviteBundle.isExpired(invite, nowMs)
}

/**
 * Direct invites (CORD-05 §6): for a known npub, the invite skips the public bundle
 * and is delivered as a *standard* NIP-59 giftwrap — a kind-3313 rumor carrying the
 * [CommunityInvite], sealed (kind 13, signed by the inviter's real key) to the recipient and
 * wrapped (kind 1059, ephemeral single-use author) with `["p", recipient]` and a `["k", "3313"]`
 * index tag so the recipient can query for pending invites without decrypting every giftwrap.
 * Not the reversed stream wrap of CORD-01.
 *
 * Wire details pinned to Armada's `directInvite.ts`:
 *  - seal and wrap `created_at` are each tweaked into the past by up to [MAX_BACKDATE_SECS]
 *    (NIP-59), so the wrap leaks only "roughly when"; the rumor keeps the real send time;
 *  - when the bundle has an `expires_at` (unix ms) the wrap carries the matching NIP-40
 *    `["expiration", expires_at / 1000]`, so relays can prune a handoff that can no longer be used;
 *  - opening requires the rumor's claimed author to equal the seal's author (NIP-59 anti-spoofing),
 *    and the seal's signature to verify — the seal is what proves who invited.
 *
 * It cannot be revoked — the recipient holds the keys the moment it lands.
 */
object ConcordDirectInvite {
    const val KIND: Int = 3313
    const val TAG_P = "p"
    const val TAG_K = "k"

    /** NIP-59: outer (seal + wrap) timestamps are tweaked into the past by up to two days. */
    const val MAX_BACKDATE_SECS: Long = 2 * 24 * 60 * 60L

    private fun json(invite: CommunityInvite) = ConcordJson.instance.encodeToString(CommunityInvite.serializer(), invite)

    /** [now] minus a uniformly random `0 until` [MAX_BACKDATE_SECS] seconds (NIP-59's timestamp tweak). */
    fun tweakedPast(now: Long = TimeUtils.now()): Long = now - RandomInstance.int(MAX_BACKDATE_SECS.toInt())

    /**
     * Builds a giftwrapped direct invite from [senderSigner] to [recipientPubKey].
     * Returns the kind-1059 wrap to publish to the recipient's inbox relays (their kind-10050 DM
     * relays, else their NIP-65 read relays). [createdAt] is the rumor's real send time; the seal and
     * the wrap are each backdated from it independently ([tweakedPast]).
     */
    suspend fun build(
        senderSigner: NostrSigner,
        recipientPubKey: HexKey,
        invite: CommunityInvite,
        createdAt: Long = TimeUtils.now(),
    ): GiftWrapEvent {
        val rumor = RumorAssembler.assembleRumor<Event>(senderSigner.pubKey, createdAt, KIND, emptyArray(), json(invite))
        val seal = SealEvent.create(rumor, recipientPubKey, senderSigner, createdAt = tweakedPast(createdAt))

        // Wrap with a random single-use key, adding the ["k","3313"] index tag and, when the bundle
        // expires, the NIP-40 expiration matching it.
        val wrapSigner = NostrSignerInternal(KeyPair())
        val content = wrapSigner.nip44Encrypt(seal.toJson(), recipientPubKey)
        val tags =
            listOfNotNull(
                arrayOf(TAG_P, recipientPubKey),
                arrayOf(TAG_K, KIND.toString()),
                invite.expiresAt?.let { arrayOf(ExpirationTag.TAG_NAME, (it / 1000).toString()) },
            ).toTypedArray()
        return wrapSigner.sign(
            createdAt = tweakedPast(createdAt),
            kind = GiftWrapEvent.KIND,
            tags = tags,
            content = content,
        )
    }

    /** True when [wrap] is a giftwrap carrying the `["k","3313"]` Direct Invite index tag (a hint, not authority). */
    fun isInviteTagged(wrap: Event): Boolean = wrap.kind == GiftWrapEvent.KIND && wrap.tags.any { it.size >= 2 && it[0] == TAG_K && it[1] == KIND.toString() }

    /**
     * True when [wrap]'s NIP-40 `expiration` (unix seconds) is at or before [nowSecs]: an expired
     * handoff is never decrypted or surfaced.
     */
    fun isWrapExpired(
        wrap: Event,
        nowSecs: Long = TimeUtils.now(),
    ): Boolean = wrap.tags.isExpirationBefore(nowSecs)

    /**
     * The `since` to query invite wraps from, given the newest wrap `created_at` already seen:
     * rewound by [MAX_BACKDATE_SECS] because wraps are backdated (a wrap published after the last
     * sweep can carry an older timestamp). Null on a cold inbox — fetch everything.
     */
    fun inboxSince(newestWrapCreatedAt: Long?): Long? = newestWrapCreatedAt?.takeIf { it > MAX_BACKDATE_SECS }?.let { it - MAX_BACKDATE_SECS }

    /**
     * Opens a direct-invite giftwrap addressed to [recipientSigner]. Null — never a throw — unless
     * every layer checks out: a kind-1059 wrap that decrypts to a kind-13 seal with a valid
     * signature, whose rumor claims the seal's author (anti-spoofing), is kind 3313 (the rumor kind
     * is the authority, not the outer `k` hint), and carries a [CommunityInvite] that passes the §1
     * bounds and the owner proof ([ConcordInviteBundle.validate]).
     */
    suspend fun open(
        wrap: Event,
        recipientSigner: NostrSigner,
    ): OpenedDirectInvite? = definitiveOrNull { openOrRetry(wrap, recipientSigner) }

    /**
     * [open], except that a failure that says nothing about the wrap — the coroutine cancelled, or
     * the signer unable to answer right now (timed out, busy, not approved, not found) — is thrown
     * instead of reported as "not an invite", so an inbox can leave the wrap to be retried rather
     * than write it off for good. Null still means definitively not a valid invite for us.
     */
    suspend fun openOrRetry(
        wrap: Event,
        recipientSigner: NostrSigner,
    ): OpenedDirectInvite? {
        if (wrap.kind != GiftWrapEvent.KIND) return null
        val plaintext = decryptOrNull { recipientSigner.nip44Decrypt(wrap.content, wrap.pubKey) } ?: return null
        val seal =
            try {
                Event.fromJson(plaintext)
            } catch (_: Exception) {
                return null
            }
        return openSealOrRetry(wrap.id, seal, recipientSigner)
    }

    /**
     * [open] from the kind-13 [seal] down, for a pipeline that already peeled the wrap [wrapId]
     * (e.g. the general NIP-17 giftwrap inbox, which honours an untagged invite all the same).
     */
    suspend fun openSeal(
        wrapId: HexKey,
        seal: Event,
        recipientSigner: NostrSigner,
    ): OpenedDirectInvite? = definitiveOrNull { openSealOrRetry(wrapId, seal, recipientSigner) }

    /** [openSeal] with [openOrRetry]'s transient-failure contract. */
    suspend fun openSealOrRetry(
        wrapId: HexKey,
        seal: Event,
        recipientSigner: NostrSigner,
    ): OpenedDirectInvite? {
        if (seal !is SealEvent) return null
        if (!runCatching { seal.verify() }.getOrDefault(false)) return null
        val plaintext = decryptOrNull { recipientSigner.nip44Decrypt(seal.content, seal.pubKey) } ?: return null
        val rumor =
            try {
                Rumor.fromJson(plaintext)
            } catch (_: Exception) {
                return null
            }
        return openRumor(wrapId, seal, rumor)
    }

    /**
     * [openSeal] for a pipeline that already decrypted [seal] into [rumor] — the rumor exactly as the
     * seal carries it, its claimed `pubkey` NOT yet overwritten by the seal's (see
     * [SealEvent.unsealRumorThrowing]) — so the invite costs no second decrypt. Validates only: the
     * seal's signature, the NIP-59 anti-spoofing author check, the rumor kind, the §1 bounds and the
     * owner proof. Never throws.
     */
    fun openRumor(
        wrapId: HexKey,
        seal: Event,
        rumor: Rumor,
    ): OpenedDirectInvite? {
        if (seal !is SealEvent) return null
        return try {
            if (!seal.verify()) return null
            // NIP-59 anti-spoofing: the rumor's claimed author must be the seal's signer. The generic
            // unseal path overwrites the rumor's pubkey with the seal's, which hides a mismatch; here
            // a mismatch is a forgery and the whole invite is refused.
            val claimed = rumor.pubKey ?: return null
            if (!claimed.equals(seal.pubKey, ignoreCase = true)) return null
            if (rumor.kind != KIND) return null
            // Bounded like a fetched bundle (CORD-05 §6: "the §1 bounds apply"), and validated
            // exactly as one: the community_id must self-certify the owner.
            val content = rumor.content ?: return null
            val invite =
                ConcordJson
                    .decodeOrNull<CommunityInvite>(content)
                    ?.let { ConcordInviteBundle.bound(it) }
                    ?.takeIf { ConcordInviteBundle.validate(it) }
                    ?: return null
            OpenedDirectInvite(wrapId, seal.pubKey.lowercase(), invite, rumor.createdAt ?: seal.createdAt)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * True for a signer failure that says the signer could not answer *now* — timed out, not
     * approved (yet), backgrounded without permission, not found — not that the payload is
     * undecryptable, so the same wrap may open on a later try. A signer that tried and failed
     * ([SignerExceptions.CouldNotPerformException], which is also how a local key reports a payload
     * that is not for it) is definitive.
     */
    fun isTransientSignerFailure(e: Throwable): Boolean =
        e is SignerExceptions.TimedOutException ||
            e is SignerExceptions.ManuallyUnauthorizedException ||
            e is SignerExceptions.AutomaticallyUnauthorizedException ||
            e is SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException ||
            e is SignerExceptions.SignerNotFoundException

    /** [decrypt]'s plaintext, null when the payload is not for us, rethrowing a transient failure. */
    private suspend fun decryptOrNull(decrypt: suspend () -> String): String? =
        try {
            decrypt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isTransientSignerFailure(e)) throw e
            null
        }

    /** Runs [block], turning a transient failure into null for the callers that never retry. */
    private suspend fun definitiveOrNull(block: suspend () -> OpenedDirectInvite?): OpenedDirectInvite? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /**
     * Opens a direct-invite giftwrap addressed to [recipientSigner] and returns the
     * [CommunityInvite], or null if it isn't a valid direct invite for this user. See [open], which
     * also returns the verified sender.
     */
    suspend fun parse(
        wrap: GiftWrapEvent,
        recipientSigner: NostrSigner,
    ): CommunityInvite? = open(wrap, recipientSigner)?.invite
}
