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
package com.vitorpamplona.amethyst.ios.ui.reactions

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

/**
 * Aggregates reactions on a note into display-ready data.
 *
 * Groups reactions by their content (emoji/text), counts duplicates,
 * and resolves custom emoji URLs from NIP-30 tags.
 *
 * Note.reactions is Map<String, List<Note>> where key is the reaction content.
 *
 * Returns a sorted list of ReactionDisplay, most popular first.
 */
fun aggregateReactions(note: Note): List<ReactionDisplay> {
    val reactionsMap = note.reactions
    if (reactionsMap.isEmpty()) return emptyList()

    return reactionsMap
        .map { (content, reactionNotes) ->
            // Resolve custom emoji from the first reaction event with this content
            val customEmoji =
                reactionNotes.firstNotNullOfOrNull { reactionNote ->
                    val event = reactionNote.event as? ReactionEvent ?: return@firstNotNullOfOrNull null
                    val emojis = event.taggedEmojis()
                    val shortcode = content.removePrefix(":").removeSuffix(":")
                    emojis.firstOrNull { it.code == shortcode }
                }

            ReactionDisplay(
                content = content.ifBlank { "+" },
                customEmoji = customEmoji,
                count = reactionNotes.size,
            )
        }.sortedByDescending { it.count }
}
