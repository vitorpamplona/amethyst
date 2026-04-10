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
package com.vitorpamplona.amethyst.ios.subscriptions

import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.currentTimeSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * iOS subscriptions coordinator. Routes relay events into the local cache
 * and emits new note bundles for UI consumption.
 */
class IosSubscriptionsCoordinator(
    private val scope: CoroutineScope,
    private val localCache: IosLocalCache,
) {
    private val _lastEventAt = MutableStateFlow<Long?>(null)
    val lastEventAt: StateFlow<Long?> = _lastEventAt.asStateFlow()

    /**
     * Central event router — consumes an event into the cache and emits to event stream.
     */
    fun consumeEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val consumed = localCache.consume(event, relay)
                if (consumed) {
                    _lastEventAt.value = currentTimeSeconds() * 1000
                    val note = localCache.getNoteIfExists(event.id) ?: return@launch
                    localCache.emitNewNotes(setOf(note))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("Coordinator: failed to consume kind=${event.kind} id=${event.id}: ${e.message}")
            }
        }
    }

    fun clear() {
        _lastEventAt.value = null
    }
}
