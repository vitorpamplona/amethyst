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
package com.vitorpamplona.amethyst.commons.search

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip50Search.EventSearchMatcher
import com.vitorpamplona.quartz.nip50Search.SearchQuery as Nip50Query

/** One emoji a search found, and the pack (kind 30030) it came from. */
@Immutable
data class EmojiSearchHit(
    val emoji: EmojiUrlTag,
    val pack: Note,
)

/**
 * Pulls the single emojis a search asked for out of the emoji packs it found.
 *
 * A pack matches a search when its title, description or any shortcode holds the terms (Quartz's
 * `EventSearchMatcher`), so typing `cat` returns every pack with a `:blobcat:` in it, as a whole
 * pack. What the reader wanted is the cat. This takes the found packs and keeps the emojis whose
 * own shortcode holds every term, by the same rule the pack was matched by: a case-insensitive
 * substring. A pack matched only by its title gives no emojis here; its card still shows.
 */
object EmojiSearch {
    /** The most emojis one search hands back: a screenful of tiles, not a pack dump. */
    const val MAX_HITS = 120

    /**
     * The search's terms as shortcode fragments: lowercased, with any `:` a reader typed around a
     * shortcode removed. Empty when the search has no free text (a bare `kind:emoji`), which asks
     * for packs, not for emojis.
     */
    fun terms(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return EventSearchMatcher
            .splitTerms(Nip50Query.parse(text).terms)
            .mapNotNull { term -> term.trim(':').lowercase().takeIf { it.isNotEmpty() } }
    }

    /** True when [code] holds every one of [terms]. */
    fun matches(
        code: String,
        terms: List<String>,
    ): Boolean = terms.isNotEmpty() && terms.all { code.contains(it, ignoreCase = true) }

    /**
     * The emojis in [notes] (only the emoji packs among them count) whose shortcode holds every
     * one of [terms], one per image, best first: a shortcode that is the term exactly, then one
     * that starts with it, then the rest, each in the order the packs were found.
     */
    fun find(
        notes: List<Note>,
        terms: List<String>,
        max: Int = MAX_HITS,
    ): List<EmojiSearchHit> {
        if (terms.isEmpty()) return emptyList()

        val exact = ArrayList<EmojiSearchHit>()
        val prefix = ArrayList<EmojiSearchHit>()
        val rest = ArrayList<EmojiSearchHit>()
        val seenUrls = HashSet<String>()
        val first = terms[0]

        for (note in notes) {
            val pack = note.event as? EmojiPackEvent ?: continue
            for (emoji in pack.publicEmojis()) {
                if (!matches(emoji.code, terms) || !seenUrls.add(emoji.url)) continue
                val hit = EmojiSearchHit(emoji, note)
                when {
                    terms.size == 1 && emoji.code.equals(first, ignoreCase = true) -> exact.add(hit)
                    emoji.code.startsWith(first, ignoreCase = true) -> prefix.add(hit)
                    else -> rest.add(hit)
                }
            }
        }

        val all = ArrayList<EmojiSearchHit>(exact.size + prefix.size + rest.size)
        all.addAll(exact)
        all.addAll(prefix)
        all.addAll(rest)
        return if (all.size > max) all.subList(0, max) else all
    }
}
