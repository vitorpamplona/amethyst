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
package com.vitorpamplona.amethyst.commons.model.nip25Reactions

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/**
 * Shared action for reacting to events (likes, emoji reactions).
 * Supports public reactions, custom emoji reactions, and NIP-17 private group reactions.
 *
 * Full-featured version extracted from Android for maximum code reuse across platforms.
 */
object ReactionAction {
    /**
     * Creates a signed reaction event.
     *
     * @param event The event to react to
     * @param reaction The reaction content (e.g., "+", "❤️", ":custom_emoji:")
     * @param signer The NostrSigner to sign the event
     * @param relayHint Optional relay hint URL where the original event was seen
     * @return Signed ReactionEvent ready to broadcast
     * @throws IllegalStateException if signer is not writeable
     */
    suspend fun reactTo(
        eventHint: EventHintBundle<Event>,
        reaction: String,
        signer: NostrSigner,
    ): ReactionEvent {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot react: signer is not writeable")
        }

        // Handle custom emoji reactions (format: ":emoji_name:")
        val template =
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrlTag.decode(reaction)
                if (emojiUrl != null) {
                    ReactionEvent.build(emojiUrl, eventHint)
                } else {
                    // Fallback to text if emoji decode fails
                    ReactionEvent.build(reaction, eventHint)
                }
            } else {
                ReactionEvent.build(reaction, eventHint)
            }

        return signer.sign(template)
    }

    /**
     * Creates a "like" reaction ("+").
     */
    suspend fun like(
        event: EventHintBundle<Event>,
        signer: NostrSigner,
    ): ReactionEvent = reactTo(event, "+", signer)

    /**
     * Advanced: React to an event with support for NIP-17 private groups.
     *
     * This method handles both public and private group reactions:
     * - For NIP17Group events: Creates private reactions within the group
     * - For regular events: Creates public reactions
     *
     * @param event The event to react to
     * @param eventHint EventHintBundle with relay information
     * @param reaction The reaction content (e.g., "+", "❤️", ":custom_emoji:")
     * @param signer The NostrSigner to sign the event
     * @param onPublic Callback for public reactions (returns signed ReactionEvent)
     * @param onPrivate Callback for private group reactions (returns NIP17Factory.Result)
     */
    suspend fun reactToWithGroupSupport(
        eventHint: EventHintBundle<Event>,
        reaction: String,
        signer: NostrSigner,
        onPublic: suspend (ReactionEvent) -> Unit,
        onPrivate: suspend (NIP17Factory.Result) -> Unit,
    ) {
        if (!signer.isWriteable()) {
            throw IllegalStateException("Cannot react: signer is not writeable")
        }

        val event = eventHint.event

        // Check if this is a NIP-17 private group event
        if (event is NIP17Group) {
            val users = event.groupMembers().toList()

            // Handle custom emoji reactions in groups
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrlTag.decode(reaction)
                if (emojiUrl != null) {
                    onPrivate(
                        NIP17Factory().createReactionWithinGroup(
                            emojiUrl = emojiUrl,
                            originalNote = eventHint,
                            to = users,
                            signer = signer,
                        ),
                    )
                    return
                }
            }

            // Regular text reaction in group
            onPrivate(
                NIP17Factory().createReactionWithinGroup(
                    content = reaction,
                    originalNote = eventHint,
                    to = users,
                    signer = signer,
                ),
            )
        } else {
            // Public reaction
            val template =
                if (reaction.startsWith(":")) {
                    val emojiUrl = EmojiUrlTag.decode(reaction)
                    if (emojiUrl != null) {
                        ReactionEvent.build(emojiUrl, eventHint)
                    } else {
                        ReactionEvent.build(reaction, eventHint)
                    }
                } else {
                    ReactionEvent.build(reaction, eventHint)
                }

            onPublic(signer.sign(template))
        }
    }

    /**
     * Android-compatible overload: React to a note with full validation.
     *
     * This accepts Note/User directly and handles all validation including
     * duplicate checking and writeability checks.
     *
     * @param note The note to react to
     * @param reaction The reaction content
     * @param by The user reacting
     * @param signer The NostrSigner to sign the event
     * @param onPublic Callback for public reactions
     * @param onPrivate Callback for private group reactions
     */
    suspend fun reactTo(
        note: Note,
        reaction: String,
        by: User,
        signer: NostrSigner,
        onPublic: (ReactionEvent) -> Unit,
        onPrivate: suspend (NIP17Factory.Result) -> Unit,
    ) {
        // All validation in commons
        if (!signer.isWriteable()) return
        if (note.hasReacted(by, reaction)) return

        val eventHint = note.toEventHint<Event>() ?: return

        reactToWithGroupSupport(eventHint, reaction, signer, onPublic, onPrivate)
    }
}
