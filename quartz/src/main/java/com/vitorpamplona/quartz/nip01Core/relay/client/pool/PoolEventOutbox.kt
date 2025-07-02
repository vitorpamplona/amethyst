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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.any

class PoolEventOutbox(
    val event: Event,
    var relays: Set<NormalizedRelayUrl>,
) {
    private val tries = mutableMapOf<NormalizedRelayUrl, Tries>()

    fun updateRelays(newRelays: Set<NormalizedRelayUrl>) {
        relays = newRelays
    }

    fun isDone(url: NormalizedRelayUrl) = tries[url]?.let { it.isDone() } ?: false

    fun isDone() = relays.all { isDone(it) }

    fun relaysLeft(): Set<NormalizedRelayUrl> = relays.filterTo(mutableSetOf()) { !isDone(it) }

    fun isSupposedToGo(url: NormalizedRelayUrl) = url in relays && !isDone(url)

    fun forEachUnsentEvent(
        url: NormalizedRelayUrl,
        run: (url: Event) -> Unit,
    ) = if (isSupposedToGo(url)) run(event) else null

    fun newTry(url: NormalizedRelayUrl) {
        val currentTries = tries[url]
        if (currentTries != null) {
            currentTries.tries.add(TimeUtils.now())
        } else {
            tries.put(url, Tries(mutableListOf(TimeUtils.now())))
        }
    }

    fun newResponse(
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
    ) {
        val currentTries = tries[url]
        if (currentTries != null) {
            currentTries.responses.add(Response(success, message))
        } else {
            tries.put(
                url,
                Tries(
                    mutableListOf(TimeUtils.now() - 1),
                    mutableListOf(Response(success, message)),
                ),
            )
        }
    }

    // Tries 3 times
    class Tries(
        val tries: MutableList<Long> = mutableListOf(),
        val responses: MutableList<Response> = mutableListOf(),
    ) {
        fun isDone() = responses.any { it.success == true } || responses.size > 2 || tries.size > 3
    }

    class Response(
        val success: Boolean,
        val message: String,
    )
}
