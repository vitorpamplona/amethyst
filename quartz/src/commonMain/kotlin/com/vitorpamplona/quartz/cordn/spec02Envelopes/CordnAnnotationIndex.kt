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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One delivered message: the envelope, and where the coordinator put it.
 *
 * The cursor is carried only for tie-breaking (see [CordnAnnotationIndex]) and
 * is never an identity — `spec/02.md` §7 and `spec/00.md` §4-5 are both explicit
 * that a cursor is scoped to one coordinator and one group.
 */
data class CordnDeliveredMessage(
    val envelope: CordnEnvelope,
    val cursor: Long,
)

/**
 * Reactions, edits, deletions and pins folded onto the messages they modify.
 *
 * Annotations can target any message in the group, so resolving them per row
 * while rendering is quadratic in a list that is usually virtualised. This
 * folds them once.
 *
 * ## The authorization rules are here, not in the UI
 *
 * They differ per annotation and they are the whole reason this is protocol
 * code rather than view-model code:
 *
 * - **Edits and deletions are author-only.** The sender must be the sender of
 *   the target. Without that check anyone in the group can rewrite anyone's
 *   words, which is worse than no edit feature at all.
 * - **A deletion must name the target's kind.** A `k` tag that disagrees with
 *   the stored message is a mismatch, not a match with a typo.
 * - **Pins are any-member.** Deliberately, and matching the reference client:
 *   pinning is a shared act in a group where `admin_pubkeys` is usually empty
 *   (`spec/01.md` — an empty admin set means egalitarian, permanently).
 * - **Deletion beats edit.** A deleted message stops accepting edits, so a
 *   late edit cannot resurrect text its author already withdrew. That is why
 *   deletions are folded before edits and the order is load-bearing.
 *
 * Everything here is authenticated before it arrives: the envelope's `pubKey`
 * was bound to the MLS sender by [CordnApplicationMessage.open], so "same
 * author" is a real check rather than a claim comparison.
 */
class CordnAnnotationIndex private constructor(
    /** Every message by envelope id, annotations included. */
    val byId: Map<HexKey, CordnDeliveredMessage>,
    /** target id → emoji → the senders who reacted with it. */
    val reactions: Map<HexKey, Map<String, Set<HexKey>>>,
    /** target id → the winning edit. */
    val edits: Map<HexKey, CordnDeliveredMessage>,
    /** Targets whose deletion passed every check. */
    val deleted: Set<HexKey>,
    /** target id → the winning pin op. Only `ADD` entries are actually pinned. */
    val pins: Map<HexKey, PinState>,
) {
    /** Who pinned a message and when, kept so a pin list can be ordered. */
    data class PinState(
        val targetId: HexKey,
        val op: CordnMessageReferences.PinOp,
        val pinnedBy: HexKey,
        val pinnedAt: Long,
        val cursor: Long,
    )

    /** The text to show for [id]: its newest accepted edit, or the original. */
    fun contentOf(id: HexKey): String? = edits[id]?.envelope?.content ?: byId[id]?.envelope?.content

    fun isEdited(id: HexKey): Boolean = edits.containsKey(id)

    fun isDeleted(id: HexKey): Boolean = deleted.contains(id)

    fun isPinned(id: HexKey): Boolean = pins[id]?.op == CordnMessageReferences.PinOp.ADD

    /** Currently pinned targets, newest pin first. */
    fun pinnedIds(): List<HexKey> =
        pins.values
            .filter { it.op == CordnMessageReferences.PinOp.ADD }
            .sortedWith(compareByDescending<PinState> { it.pinnedAt }.thenByDescending { it.cursor })
            .map { it.targetId }

    companion object {
        /**
         * Folds [messages] — a whole group's stream, annotations included.
         *
         * Four passes, and the order matters exactly once: deletions before
         * edits, so a withdrawn message cannot be edited back into existence.
         * The rest are independent.
         */
        fun of(messages: List<CordnDeliveredMessage>): CordnAnnotationIndex {
            val byId = messages.associateBy { it.envelope.id }

            val reactions = mutableMapOf<HexKey, MutableMap<String, MutableSet<HexKey>>>()
            messages.forEach { message ->
                val ref =
                    CordnMessageReferences.reaction(
                        message.envelope.kind,
                        message.envelope.content,
                        message.envelope.tags,
                    ) ?: return@forEach
                reactions
                    .getOrPut(ref.targetId) { mutableMapOf() }
                    .getOrPut(ref.reaction) { mutableSetOf() }
                    .add(message.envelope.pubKey)
            }

            val deleted = mutableSetOf<HexKey>()
            messages.forEach { message ->
                val ref = CordnMessageReferences.delete(message.envelope.kind, message.envelope.tags) ?: return@forEach
                val target = byId[ref.targetId] ?: return@forEach
                // A `k` that disagrees with the stored message is a mismatch.
                if (ref.targetKind != target.envelope.kind) return@forEach
                // Author-only. The pubkeys are MLS-authenticated, not claimed.
                if (target.envelope.pubKey != message.envelope.pubKey) return@forEach
                deleted.add(ref.targetId)
            }

            val edits = mutableMapOf<HexKey, CordnDeliveredMessage>()
            messages.forEach { message ->
                val ref =
                    CordnMessageReferences.edit(
                        message.envelope.kind,
                        message.envelope.content,
                        message.envelope.tags,
                    ) ?: return@forEach
                if (ref.targetId in deleted) return@forEach
                val target = byId[ref.targetId] ?: return@forEach
                if (target.envelope.pubKey != message.envelope.pubKey) return@forEach

                val current = edits[ref.targetId]
                // Newest wins. Ties break on cursor, which the coordinator makes
                // monotonic (spec/00.md §4) -- two edits in the same second are
                // otherwise a coin flip that two clients could call differently.
                if (current == null ||
                    message.envelope.createdAt > current.envelope.createdAt ||
                    (message.envelope.createdAt == current.envelope.createdAt && message.cursor > current.cursor)
                ) {
                    edits[ref.targetId] = message
                }
            }

            val pins = mutableMapOf<HexKey, PinState>()
            messages.forEach { message ->
                val ref = CordnMessageReferences.pin(message.envelope.kind, message.envelope.tags) ?: return@forEach
                // No author check: pinning is any-member. See the class KDoc.
                val current = pins[ref.targetId]
                if (current == null ||
                    message.envelope.createdAt > current.pinnedAt ||
                    (message.envelope.createdAt == current.pinnedAt && message.cursor > current.cursor)
                ) {
                    pins[ref.targetId] =
                        PinState(
                            targetId = ref.targetId,
                            op = ref.op,
                            pinnedBy = message.envelope.pubKey,
                            pinnedAt = message.envelope.createdAt,
                            cursor = message.cursor,
                        )
                }
            }

            return CordnAnnotationIndex(byId, reactions, edits, deleted, pins)
        }
    }
}
