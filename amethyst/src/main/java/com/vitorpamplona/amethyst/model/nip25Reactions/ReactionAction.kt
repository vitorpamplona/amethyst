/**
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
package com.vitorpamplona.amethyst.model.nip25Reactions

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

class ReactionAction {
    companion object {
        suspend fun reactTo(
            note: Note,
            reaction: String,
            by: User,
            signer: NostrSigner,
            onPublic: (ReactionEvent) -> Unit,
            onPrivate: suspend (NIP17Factory.Result) -> Unit,
        ) {
            if (!signer.isWriteable()) return

            if (note.hasReacted(by, reaction)) {
                // has already liked this note
                return
            }

            val noteEvent = note.event
            if (noteEvent is NIP17Group) {
                val users = noteEvent.groupMembers().toList()

                if (reaction.startsWith(":")) {
                    val emojiUrl = EmojiUrlTag.decode(reaction)
                    if (emojiUrl != null) {
                        note.toEventHint<Event>()?.let {
                            onPrivate(
                                NIP17Factory().createReactionWithinGroup(
                                    emojiUrl = emojiUrl,
                                    originalNote = it,
                                    to = users,
                                    signer = signer,
                                ),
                            )
                        }

                        return
                    }
                }

                note.toEventHint<Event>()?.let {
                    onPrivate(
                        NIP17Factory().createReactionWithinGroup(
                            content = reaction,
                            originalNote = it,
                            to = users,
                            signer = signer,
                        ),
                    )
                }
                return
            } else {
                if (reaction.startsWith(":")) {
                    val emojiUrl = EmojiUrlTag.decode(reaction)
                    if (emojiUrl != null) {
                        note.event?.let {
                            val template = ReactionEvent.build(emojiUrl, EventHintBundle(it, note.relayHintUrl()))

                            onPublic(signer.sign(template))
                        }

                        return
                    }
                }

                note.toEventHint<Event>()?.let {
                    onPublic(signer.sign(ReactionEvent.build(reaction, it)))
                }
            }
        }
    }
}
