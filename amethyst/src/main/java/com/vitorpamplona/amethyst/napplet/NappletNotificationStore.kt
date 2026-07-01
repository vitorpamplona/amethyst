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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-coordinate registry of the notifications a napplet created via the NAP `notify` domain. Lives
 * in the main process (the broker's process) and survives broker rebuilds on account switch, so a
 * napplet's `notify.list`/`notify.dismiss` see the same set it created.
 *
 * Namespaced by applet coordinate, like [DataStoreNappletStorage]: a napplet can only ever see and
 * dismiss its **own** notifications. In-memory only — notifications are ephemeral UI, not durable state.
 */
object NappletNotificationStore {
    // coordinate -> (id -> notification), insertion-ordered so list() is stable.
    private val byCoordinate = ConcurrentHashMap<String, LinkedHashMap<String, NappletNotification>>()
    private val seq = AtomicLong(0)

    fun create(
        coordinate: String,
        title: String,
        body: String,
    ): String {
        val id = "n${System.currentTimeMillis()}-${seq.incrementAndGet()}"
        val map = byCoordinate.getOrPut(coordinate) { LinkedHashMap() }
        synchronized(map) { map[id] = NappletNotification(id, title, body) }
        return id
    }

    fun list(coordinate: String): List<NappletNotification> {
        val map = byCoordinate[coordinate] ?: return emptyList()
        return synchronized(map) { map.values.toList() }
    }

    fun dismiss(
        coordinate: String,
        id: String,
    ) {
        val map = byCoordinate[coordinate] ?: return
        synchronized(map) { map.remove(id) }
    }
}
