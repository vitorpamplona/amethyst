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

import com.vitorpamplona.quartz.nip01Core.core.Event

object SearchResultFilter {
    fun filter(
        events: List<Event>,
        query: SearchQuery,
    ): List<Event> {
        var result = events

        // Dedup by event ID
        result = result.distinctBy { it.id }

        // Exclusion terms (client-side)
        if (query.excludeTerms.isNotEmpty()) {
            result =
                result.filter { event ->
                    query.excludeTerms.none { term ->
                        event.content.contains(term, ignoreCase = true)
                    }
                }
        }

        // Pseudo-kind: reply (kind 1 with e tag)
        if ("reply" in query.pseudoKinds) {
            result = result.filter { event -> isReply(event) }
        }

        // Pseudo-kind: media (kind 1 with imeta tag or image URLs)
        if ("media" in query.pseudoKinds) {
            result = result.filter { event -> isMedia(event) }
        }

        // Sort by createdAt descending
        return result.sortedByDescending { it.createdAt }
    }

    fun isReply(event: Event): Boolean = event.kind == 1 && event.tags.any { it.size >= 2 && it[0] == "e" }

    fun isMedia(event: Event): Boolean {
        if (event.kind != 1) return false
        // Check for imeta tag
        if (event.tags.any { it.size >= 2 && it[0] == "imeta" }) return true
        // Check for image/video URLs in content
        return IMAGE_URL_PATTERN.containsMatchIn(event.content)
    }

    private val IMAGE_URL_PATTERN =
        Regex(
            """https?://\S+\.(jpg|jpeg|png|gif|webp|svg|mp4|webm|mov)""",
            RegexOption.IGNORE_CASE,
        )
}
