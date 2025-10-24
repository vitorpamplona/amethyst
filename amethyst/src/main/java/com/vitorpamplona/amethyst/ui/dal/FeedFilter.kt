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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.logTime

abstract class FeedFilter<T> : IFeedFilter<T> {
    override fun loadTop(): List<T> {
        val feed =
            logTime(
                debugMessage = { "${this.javaClass.simpleName} FeedFilter returning ${it.size} objects" },
                block = ::feed,
            )
        return feed.take(limit())
    }

    override fun loadOlderNotes(
        afterNote: T?,
        limit: Int,
    ): List<T> =
        logTime(
            debugMessage = { "${this.javaClass.simpleName} FeedFilter loading ${it.size} older objects" },
            block = { loadOlderNotesImpl(afterNote, limit) },
        )

    /** Default implementation - can be overridden by specific filters */
    open fun loadOlderNotesImpl(
        afterNote: T?,
        limit: Int,
    ): List<T> {
        // Default implementation returns empty list
        // Specific filters can override this for prefetching support
        return emptyList()
    }
}

interface IFeedFilter<T> {
    fun loadTop(): List<T>

    /** Load older notes for prefetching */
    fun loadOlderNotes(
        afterNote: T?,
        limit: Int,
    ): List<T>

    fun limit(): Int = 500

    /** Returns a string that serves as the key to invalidate the list if it changes. */
    fun feedKey(): Any

    fun showHiddenKey(): Boolean = false

    fun feed(): List<T>
}
