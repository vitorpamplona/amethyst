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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.util.firstFullCharOrEmoji
import com.vitorpamplona.amethyst.commons.util.replace
import java.math.BigDecimal

/**
 * Container for social interaction data attached to a [Note].
 *
 * This object is lazily allocated via [Note.socialOrCreate] — it stays null
 * on stub notes and leaf events (reactions, zaps, reposts) that never receive
 * their own interactions, saving memory on the majority of notes in the cache.
 *
 * All notes CAN receive social interactions (replies, reactions, boosts, zaps,
 * reports, OTS certifications, NIP-32 labels, quotes), but most never do.
 */
@Stable
class SocialInteractions {
    var replies = listOf<Note>()

    var reactions = mapOf<String, List<Note>>()

    var boosts = listOf<Note>()

    var reports = mapOf<User, List<Note>>()

    var zaps = mapOf<Note, Note?>()

    var zapsAmount: BigDecimal = BigDecimal.ZERO

    var zapPayments = mapOf<Note, Note?>()

    /** OTS certification note (NIP-03). Previously tracked only via flow. */
    var ots: Note? = null

    /** NIP-32 labels. Maps namespace → list of label notes. */
    var labels = mapOf<String, List<Note>>()

    /** Notes that explicitly quote this note. */
    var quotes = listOf<Note>()

    /**
     * Clears all child references and returns the list of all child notes
     * that were removed (for cache cleanup).
     */
    fun removeAllChildren(): List<Note> {
        val toBeRemoved =
            replies +
                reactions.values.flatten() +
                boosts +
                reports.values.flatten() +
                zaps.keys +
                zaps.values.filterNotNull() +
                zapPayments.keys +
                zapPayments.values.filterNotNull() +
                labels.values.flatten() +
                quotes +
                listOfNotNull(ots)

        replies = listOf()
        reactions = mapOf()
        boosts = listOf()
        reports = mapOf()
        zaps = mapOf()
        zapPayments = mapOf()
        zapsAmount = BigDecimal.ZERO
        ots = null
        labels = mapOf()
        quotes = listOf()

        return toBeRemoved
    }

    /**
     * Migrates all social interaction references to a target [SocialInteractions].
     * Used when an [AddressableNote] is replaced by a newer version.
     */
    fun migrateAllTo(
        target: SocialInteractions,
        source: Note,
        destination: Note,
    ) {
        replies.forEach {
            target.replies = target.replies + it
            it.replyTo = it.replyTo?.replace(source, destination)
        }
        reactions.forEach { (_, notes) ->
            notes.forEach {
                target.addReactionDirect(it)
                it.replyTo = it.replyTo?.replace(source, destination)
            }
        }
        boosts.forEach {
            target.boosts = target.boosts + it
            it.replyTo = it.replyTo?.replace(source, destination)
        }
        reports.forEach { (_, notes) ->
            notes.forEach {
                target.addReportDirect(it)
                it.replyTo = it.replyTo?.replace(source, destination)
            }
        }
        zaps.forEach { (key, value) ->
            target.zaps = target.zaps + Pair(key, value)
            key.replyTo = key.replyTo?.replace(source, destination)
            value?.let { it.replyTo = it.replyTo?.replace(source, destination) }
        }
        labels.forEach { (ns, notes) ->
            val existing = target.labels[ns]
            target.labels =
                if (existing != null) {
                    target.labels + Pair(ns, existing + notes)
                } else {
                    target.labels + Pair(ns, notes)
                }
        }
        quotes.forEach {
            if (it !in target.quotes) {
                target.quotes = target.quotes + it
            }
        }
        ots?.let { target.ots = it }

        // Clear source
        replies = listOf()
        reactions = mapOf()
        boosts = listOf()
        reports = mapOf()
        zaps = mapOf()
        zapPayments = mapOf()
        zapsAmount = BigDecimal.ZERO
        ots = null
        labels = mapOf()
        quotes = listOf()
    }

    // --- Internal helpers for migration ---

    private fun addReactionDirect(note: Note) {
        val tags = note.event?.tags ?: emptyArray()
        val reaction = note.event?.content?.firstFullCharOrEmoji(ImmutableListOfLists(tags)) ?: "+"

        val existing = reactions[reaction]
        reactions =
            if (existing == null) {
                reactions + Pair(reaction, listOf(note))
            } else if (!existing.contains(note)) {
                reactions + Pair(reaction, existing + note)
            } else {
                reactions
            }
    }

    private fun addReportDirect(note: Note) {
        val author = note.author ?: return
        val existing = reports[author]
        reports =
            if (existing == null) {
                reports + Pair(author, listOf(note))
            } else if (!existing.contains(note)) {
                reports + Pair(author, existing + note)
            } else {
                reports
            }
    }
}
