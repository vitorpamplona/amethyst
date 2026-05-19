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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.commons.ui.notifications.Card
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event

val DefaultFeedOrder: Comparator<Note> =
    compareByDescending<Note> { it.createdAt() }.thenBy { it.idHex }

val DefaultFeedOrderEvent: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }

val DefaultFeedOrderCard: Comparator<Card> =
    compareByDescending<Card> { it.createdAt() }.thenBy { it.id() }

// Snapshots createdAt once per note so the comparator stays consistent even if
// another thread swaps a Note's event mid-sort (e.g. a newer AddressableEvent
// arriving from a relay). Avoids TimSort's "Comparison method violates its
// general contract!" IllegalArgumentException.
fun Iterable<Note>.sortedByDefaultFeedOrder(): List<Note> =
    map { it to (it.createdAt() ?: 0L) }
        .sortedWith(compareByDescending<Pair<Note, Long>> { it.second }.thenBy { it.first.idHex })
        .map { it.first }
