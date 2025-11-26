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

class PoolEventOutboxState(
    val event: Event,
    var relays: Set<NormalizedRelayUrl>,
) {
    private var tries = mapOf<NormalizedRelayUrl, Tries>()

    fun updateRelays(newRelays: Set<NormalizedRelayUrl>) {
        relays = newRelays
    }

    fun isDone(url: NormalizedRelayUrl) = tries[url]?.isDone() ?: false

    fun isDone() = relays.all { isDone(it) }

    fun relaysLeft(): Set<NormalizedRelayUrl> = relays.filterTo(mutableSetOf()) { !isDone(it) }

    fun isSupposedToGo(url: NormalizedRelayUrl) = url in relays && !isDone(url)

    fun forEachUnsentEvent(
        url: NormalizedRelayUrl,
        run: (url: Event) -> Unit,
    ) = if (isSupposedToGo(url)) run(event) else null

    fun remainingRelays() = relays.filterTo(mutableSetOf(), ::isSupposedToGo)

    fun newTry(url: NormalizedRelayUrl) {
        val currentTries = tries[url]
        if (currentTries != null) {
            currentTries.addTriedTime(TimeUtils.now())
        } else {
            tries = tries + (url to Tries(listOf(TimeUtils.now())))
        }
    }

    fun newResponse(
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
    ) {
        val currentTries = tries[url]
        if (currentTries != null) {
            currentTries.addResponse(Response(success, message))
        } else {
            tries = tries + (
                url to
                    Tries(
                        listOf(TimeUtils.now() - 1),
                        listOf(Response(success, message)),
                    )
            )
        }
    }

    // Tries 3 times
    class Tries(
        var tries: List<Long> = listOf(),
        var responses: List<Response> = listOf(),
    ) {
        fun isDone() = responses.any { it.success } || responses.size > 2 || tries.size > 3

        fun addResponse(r: Response) {
            responses += r
        }

        fun addTriedTime(tried: Long) {
            tries += tried
        }
    }

    class Response(
        val success: Boolean,
        val message: String,
    )
}
