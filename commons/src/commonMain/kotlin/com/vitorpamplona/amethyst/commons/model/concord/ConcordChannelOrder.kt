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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord02Community.ConcordChannel as FoldedChannel
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelOrder as SidebarOrder

/**
 * The order a community's channel list shows its Channels in: Quartz's
 * [SidebarOrder] (CORD-03 §2), the same on every client. A Channel's `position` rank key first, then
 * Armada's legacy `custom["armada.order"]` position, then name as UTF-8 bytes, then id, so a
 * community Armada ordered lists alike here and one that never ordered anything reads by name.
 * Without this the list followed the fold's map order, which put `#general` below channels
 * created after it.
 */
object ConcordChannelOrder {
    val comparator: Comparator<Map.Entry<String, FoldedChannel>> = Comparator { a, b -> SidebarOrder.comparator.compare(a.value, b.value) }

    fun <T : Map.Entry<String, FoldedChannel>> sorted(channels: Collection<T>): List<T> = channels.sortedWith(comparator)
}
