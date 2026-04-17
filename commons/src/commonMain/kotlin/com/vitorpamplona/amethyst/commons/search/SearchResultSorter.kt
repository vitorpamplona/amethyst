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

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.utils.currentTimeSeconds

object SearchResultSorter {
    fun sortEvents(
        events: List<Event>,
        order: SearchSortOrder,
        searchText: String,
    ): List<Event> =
        when (order) {
            SearchSortOrder.NEWEST -> {
                events.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
            }

            SearchSortOrder.OLDEST -> {
                events.sortedWith(compareBy<Event> { it.createdAt }.thenBy { it.id })
            }

            SearchSortOrder.RELEVANCE -> {
                if (searchText.isBlank()) {
                    events.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
                } else {
                    events.sortedByDescending { scoreEvent(it, searchText) }
                }
            }

            SearchSortOrder.POPULAR -> {
                // Raw Event has no zap-total; callers that hold Note objects should sort by
                // zapsAmount directly. Fall back to newest so the option is still harmless here.
                events.sortedWith(compareByDescending<Event> { it.createdAt }.thenBy { it.id })
            }

            else -> {
                events
            }
        }

    fun sortPeople(
        people: List<User>,
        order: SearchSortOrder,
    ): List<User> =
        when (order) {
            SearchSortOrder.NAME_AZ -> people.sortedBy { it.toBestDisplayName().lowercase() }
            SearchSortOrder.NAME_ZA -> people.sortedByDescending { it.toBestDisplayName().lowercase() }
            else -> people
        }

    fun scoreEvent(
        event: Event,
        searchText: String,
    ): Double {
        val query = searchText.trim().lowercase()
        if (query.isEmpty()) return event.createdAt.toDouble()

        var score = 0.0
        val content = event.content.lowercase()
        val tokens = query.split("\\s+".toRegex())

        // Exact phrase match in content
        if (content.contains(query)) {
            score += 10.0
        }

        // Per-token scoring
        for (token in tokens) {
            if (token.isEmpty()) continue
            val wordBoundary = "\\b${Regex.escape(token)}\\b".toRegex()
            if (wordBoundary.containsMatchIn(content)) {
                score += 5.0
            } else if (content.contains(token)) {
                score += 2.0
            }
        }

        // Article title boost
        if (event is LongTextNoteEvent) {
            val title = event.title()?.lowercase()
            if (title != null) {
                if (title.contains(query)) {
                    score += 15.0
                }
                for (token in tokens) {
                    if (token.isEmpty()) continue
                    if (title.contains(token)) {
                        score += 3.0
                    }
                }
            }
        }

        // Recency tiebreaker (normalized 0..1)
        val now = currentTimeSeconds()
        val age = (now - event.createdAt).coerceAtLeast(1)
        val maxAge = 365L * 24 * 3600 // 1 year
        score += (1.0 - (age.toDouble() / maxAge).coerceIn(0.0, 1.0))

        return score
    }
}
