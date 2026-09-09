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
package com.vitorpamplona.quartz.experimental.ratings

/**
 * The `m` (mark) vocabulary of kind-34259 entity ratings, plus the `d`-tag prefixing rule
 * that goes with it.
 *
 * The spec keeps this open-ended — "example values could include 'event', 'profile', 'relay',
 * 'hashtag', 'books', 'movies'" — so the constants below are the known marks, not an
 * exhaustive enum. Anything else is a valid mark this client simply does not render richly.
 */
object RatingMark {
    const val EVENT = "event"
    const val PROFILE = "profile"
    const val RELAY = "relay"
    const val HASHTAG = "hashtag"
    const val BOOKS = "books"
    const val MOVIES = "movies"

    /** Spec: "If empty it is assumed to be a nostr event." */
    fun orDefault(mark: String?): String = if (mark.isNullOrEmpty()) EVENT else mark

    /**
     * Removes the `<mark>:` prefix the spec asks for on ids that are not unique on their own
     * ("If you want to rate a hashtag use `hashtag:<tag>` as the identifier for the d-tag").
     *
     * Only strips when the prefix actually matches [mark], so a `d` that happens to contain
     * colons for other reasons (an addressable coordinate, say) survives intact.
     */
    fun stripPrefix(
        dTag: String,
        mark: String?,
    ): String {
        if (mark.isNullOrEmpty()) return dTag
        val prefix = "$mark:"
        return if (dTag.startsWith(prefix)) dTag.substring(prefix.length) else dTag
    }

    /** The inverse of [stripPrefix] — builds the `d` value for a rating of [id] under [mark]. */
    fun applyPrefix(
        id: String,
        mark: String?,
    ): String {
        if (mark.isNullOrEmpty()) return id
        val prefix = "$mark:"
        return if (id.startsWith(prefix)) id else prefix + id
    }
}
