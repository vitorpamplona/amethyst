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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Takes every set from upstream at once, and hands the collector the union of everything that
 * arrived since it last took one. Nothing is dropped, and a slow collector makes the next set
 * bigger instead of making a queue longer: what waits is bounded by the distinct items, not by
 * how many sets came in.
 */
fun <T> Flow<Set<T>>.coalesced(): Flow<Set<T>> =
    channelFlow {
        val lock = KmpLock()
        var pending = LinkedHashSet<T>()
        val ready = Channel<Unit>(Channel.CONFLATED)
        launch {
            try {
                collect { set ->
                    lock.withLock { pending.addAll(set) }
                    ready.trySend(Unit)
                }
            } finally {
                ready.close()
            }
        }
        for (signal in ready) {
            val batch = lock.withLock { pending.also { pending = LinkedHashSet() } }
            if (batch.isNotEmpty()) send(batch)
        }
        val rest = lock.withLock { pending }
        if (rest.isNotEmpty()) send(rest)
    }.buffer(Channel.RENDEZVOUS)
