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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * The growing list [fetchAsFlow] and [subscribeAsFlow] emit on every new event, built so that
 * neither adding an event nor emitting the list copies it.
 *
 * Those flows used to emit `currentEvents + event`, a fresh copy of everything so far on every
 * event: a 5,000-event answer copied ~12.5M references. Here events go into two append-only
 * arrays, one growing at the end ([append]) and one growing at the front ([prepend], stored
 * reversed), and [snapshot] is a read-only view of both at their current sizes. The view never
 * changes afterwards: later events land past the sizes it captured, or in a new array once one
 * grows. It reaches another thread safely through the flow's channel, which orders everything
 * written before the send before the collector's reads.
 *
 * Not thread-safe for writers: one subscription's events arrive one at a time.
 */
internal class EventListAccumulator {
    private var front = arrayOfNulls<Event>(INITIAL)
    private var frontSize = 0
    private var back = arrayOfNulls<Event>(INITIAL)
    private var backSize = 0

    val size: Int get() = frontSize + backSize

    fun append(event: Event) {
        if (backSize == back.size) back = back.copyOf(backSize * 2)
        back[backSize++] = event
    }

    fun prepend(event: Event) {
        if (frontSize == front.size) front = front.copyOf(frontSize * 2)
        front[frontSize++] = event
    }

    fun snapshot(): List<Event> = Snapshot(front, frontSize, back, backSize)

    private class Snapshot(
        private val front: Array<Event?>,
        private val frontSize: Int,
        private val back: Array<Event?>,
        private val backSize: Int,
    ) : AbstractList<Event>() {
        override val size: Int get() = frontSize + backSize

        override fun get(index: Int): Event {
            if (index < 0 || index >= size) throw IndexOutOfBoundsException("index $index, size $size")
            // The front array holds the prepended events oldest-first, so the newest is last.
            return if (index < frontSize) front[frontSize - 1 - index]!! else back[index - frontSize]!!
        }
    }

    private companion object {
        const val INITIAL = 16
    }
}
