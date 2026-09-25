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
package com.vitorpamplona.quartz.experimental.decentralizedLists

import com.vitorpamplona.quartz.nip01Core.core.IEvent

/**
 * Common face of the four Decentralized Lists kinds (9998, 39998, 9999, 39999).
 *
 * Any of them can be the parent of a list item: headers are the standard parents, and the
 * spec's "nonstandard" method declares a list with a 9999/39999 item whose own parent is a
 * list of lists. So every kind knows how children must point at it.
 */
interface DecentralizedListEvent : IEvent {
    /**
     * The value a child item writes in its `z` tag to point at this event: the event id for
     * the regular kinds, the `kind:pubkey:d` coordinate for the addressable ones.
     */
    fun listPointer(): String
}

/**
 * The item kinds (9999, 39999). Items point at their parent list with one or more `z` tags and
 * carry the item itself in `p` / `e` / `t` / `a` tags.
 */
interface DecentralizedListItem : DecentralizedListEvent
