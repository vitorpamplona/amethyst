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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class NIP17Factory {
    data class Result(
        val msg: Event,
        val wraps: List<GiftWrapEvent>,
    )

    /**
     * Build one NIP-59 gift wrap per recipient.
     *
     * The rumor (kind 14) `created_at` is implicitly shared across all wraps
     * because [event] is signed once by the caller before the per-recipient
     * loop runs — every seal encodes the same rumor `id`. This anchors
     * cross-recipient dedupe + reaction/receipt targeting on group sends.
     *
     * Per NIP-17, the gift wrap's `p` tag MAY carry the recipient's primary
     * DM inbox relay as a hint. Pass [recipientRelayHints] to surface those;
     * the default `{ null }` lambda preserves the historical 2-element tag
     * shape for every recipient.
     *
     * When [signer] is a [NostrSignerRemote] (NIP-46 bunker), seal building
     * is rate-limited to [BUNKER_PARALLELISM] concurrent operations. Each
     * seal needs `nip44_encrypt` + `sign` round-trips against the bunker; a
     * 5-recipient group otherwise launches 10 concurrent in-flight RPCs and
     * saturates the bunker socket. Local signers (NostrSignerInternal,
     * NostrSignerSync) run fully parallel — no semaphore overhead.
     *
     * The proper fix is the batched `nip44_get_conversation_keys` NIP-46
     * RPC (separate plan); this is the interim throttle until that lands.
     */
    private suspend fun createWraps(
        event: Event,
        to: Set<HexKey>,
        signer: NostrSigner,
        recipientRelayHints: (HexKey) -> NormalizedRelayUrl? = { null },
    ): List<GiftWrapEvent> {
        val innerExpDelta =
            event.expiration()?.let {
                if (it > event.createdAt) {
                    it - event.createdAt
                } else {
                    null
                }
            }

        val bunkerLimiter = if (signer is NostrSignerRemote) Semaphore(BUNKER_PARALLELISM) else null

        return mapNotNullAsync(
            to.toList(),
        ) { next ->
            val build: suspend () -> GiftWrapEvent = {
                GiftWrapEvent.create(
                    event =
                        SealedRumorEvent.create(
                            event = event,
                            encryptTo = next,
                            expirationDelta = innerExpDelta,
                            signer = signer,
                        ),
                    recipientPubKey = next,
                    expirationDelta = innerExpDelta,
                    recipientRelayHint = recipientRelayHints(next),
                )
            }
            bunkerLimiter?.withPermit { build() } ?: build()
        }
    }

    companion object {
        /**
         * Max concurrent in-flight NIP-46 RPCs when building wraps via a
         * remote signer. Empirically a sweet spot — covers parallelism
         * speedup for 2–4 recipient sends without saturating typical
         * bunker apps (nsec.app, Amber, Keychat) that serialize requests
         * internally past ~10 in-flight.
         */
        const val BUNKER_PARALLELISM = 4
    }

    suspend fun createMessageNIP17(
        template: EventTemplate<ChatMessageEvent>,
        signer: NostrSigner,
        recipientRelayHints: (HexKey) -> NormalizedRelayUrl? = { null },
    ): Result {
        val senderMessage = signer.sign(template)
        val wraps = createWraps(senderMessage, senderMessage.groupMembers(), signer, recipientRelayHints)
        return Result(
            msg = senderMessage,
            wraps = wraps,
        )
    }

    /**
     * Gift-wraps a kind-1 note (a private reply or private post for the
     * public feed) instead of publishing it. Recipients are exactly the
     * p-tags carried by the template, plus the sender's self-copy. The
     * signed inner event never leaves the device — only its unsigned rumor
     * form travels inside the seals.
     */
    suspend fun createNoteNIP17(
        template: EventTemplate<TextNoteEvent>,
        signer: NostrSigner,
    ): Result {
        val senderNote = signer.sign(template)
        val wraps = createWraps(senderNote, senderNote.taggedUserIds().plus(signer.pubKey).toSet(), signer)
        return Result(
            msg = senderNote,
            wraps = wraps,
        )
    }

    suspend fun createEncryptedFileNIP17(
        template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>,
        signer: NostrSigner,
        recipientRelayHints: (HexKey) -> NormalizedRelayUrl? = { null },
    ): Result {
        val senderMessage = signer.sign(template)
        val wraps = createWraps(senderMessage, senderMessage.groupMembers(), signer, recipientRelayHints)

        return Result(
            msg = senderMessage,
            wraps = wraps,
        )
    }

    /**
     * Gift-wraps a NIP-09 deletion request that retracts rumor-only events
     * (private reactions/replies). The deletion must reach the same
     * participants the retracted rumor was wrapped to — published publicly
     * it would e-tag the private rumor id onto public relays.
     */
    suspend fun createDeletionNIP17(
        template: EventTemplate<DeletionEvent>,
        to: List<HexKey>,
        signer: NostrSigner,
    ): Result {
        val deletion = signer.sign(template)
        val wraps = createWraps(deletion, to.plus(signer.pubKey).toSet(), signer)
        return Result(
            msg = deletion,
            wraps = wraps,
        )
    }

    suspend fun createReactionWithinGroup(
        content: String,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
        recipientRelayHints: (HexKey) -> NormalizedRelayUrl? = { null },
    ): Result {
        val senderPublicKey = signer.pubKey
        val template = ReactionEvent.build(content, originalNote)

        val senderReaction = signer.sign(template)
        val wraps = createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer, recipientRelayHints)
        return Result(
            msg = senderReaction,
            wraps = wraps,
        )
    }

    suspend fun createReactionWithinGroup(
        emojiUrl: EmojiUrlTag,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
        recipientRelayHints: (HexKey) -> NormalizedRelayUrl? = { null },
    ): Result {
        val senderPublicKey = signer.pubKey
        val template = ReactionEvent.build(emojiUrl, originalNote)

        val senderReaction = signer.sign(template)
        val wraps = createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer, recipientRelayHints)

        return Result(
            msg = senderReaction,
            wraps = wraps,
        )
    }
}
