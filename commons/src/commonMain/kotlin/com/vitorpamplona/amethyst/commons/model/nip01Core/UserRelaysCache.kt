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
package com.vitorpamplona.amethyst.commons.model.nip01Core

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import kotlin.collections.mapOf
import kotlin.collections.plus

@Stable
data class RelayInfo(
    var lastEvent: Long,
    var counter: Int,
) {
    fun countNewEvent(eventTime: Long) {
        if (eventTime > lastEvent) {
            lastEvent = eventTime
        }
        counter++
    }
}

@Stable
class Wrapper(
    val data: Map<NormalizedRelayUrl, RelayInfo>,
)

val DefaultOrder =
    compareByDescending<Map.Entry<NormalizedRelayUrl, RelayInfo>> { it.value.counter }
        .thenByDescending { it.value.lastEvent }
        .thenBy { it.hashCode() }

@Stable
class UserRelaysCache {
    var data: Map<NormalizedRelayUrl, RelayInfo> = mapOf()
    private var flow: WeakReference<MutableStateFlow<Wrapper>>? = null

    fun flow() =
        flow?.get() ?: synchronized(this) {
            flow?.get() ?: MutableStateFlow(Wrapper(data)).also { flow = WeakReference(it) }
        }

    fun add(
        relay: NormalizedRelayUrl,
        eventTime: Long,
    ) {
        val here = data[relay]
        if (here == null) {
            data = data + Pair(relay, RelayInfo(eventTime, 1))
        } else {
            here.countNewEvent(eventTime)
        }

        flow?.get()?.tryEmit(Wrapper(data))
    }

    fun allOrNull() = data.keys.ifEmpty { null }

    fun mostUsed(): List<NormalizedRelayUrl> = data.entries.sortedWith(DefaultOrder).map { it.key }

    fun mostUsedNonLocalRelay() = data.filter { !it.key.isLocalHost() }.maxByOrNull { it.value.counter }?.key
}
