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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

class PoolEventOutboxState(
    val event: Event,
    var relaysRemaining: Set<NormalizedRelayUrl>,
) {
    private var failures = mapOf<NormalizedRelayUrl, Tries>()

    fun updateRelays(newRelays: Set<NormalizedRelayUrl>) {
        relaysRemaining = newRelays
    }

    fun isDone() = relaysRemaining.isEmpty()

    fun relaysLeft(): Set<NormalizedRelayUrl> = relaysRemaining

    fun isSupposedToGo(url: NormalizedRelayUrl) = url in relaysRemaining

    fun forEachUnsentEvent(
        url: NormalizedRelayUrl,
        run: (url: Event) -> Unit,
    ) = if (isSupposedToGo(url)) run(event) else null

    fun remainingRelays() = relaysRemaining

    fun newTry(url: NormalizedRelayUrl) {
        val currentTries = failures[url]
        if (currentTries != null) {
            currentTries.addTriedTime(TimeUtils.now())
            if (currentTries.isDone()) {
                relaysRemaining = relaysRemaining - url
                failures = failures - url
            }
        } else {
            failures = failures + (url to Tries(listOf(TimeUtils.now())))
        }
    }

    fun newResponse(
        url: NormalizedRelayUrl,
        success: Boolean,
        message: String,
    ) {
        val currentTries = failures[url]
        if (success || message.shouldDiscard()) {
            relaysRemaining = relaysRemaining - url
            failures = failures - url
        } else {
            if (currentTries != null) {
                currentTries.addResponse(message)
            } else {
                failures = failures + (
                    url to
                        Tries(
                            listOf(TimeUtils.now() - 1),
                            listOf(message),
                        )
                )
            }
        }
    }

    fun String.shouldDiscard() =
        this.startsWith("replaced:") ||
            this.startsWith("pow:") ||
            this.startsWith("deleted:") ||
            this.startsWith("invalid:")

    // Tries 3 times
    class Tries(
        var tries: List<Long> = listOf(),
        var responses: List<String> = listOf(),
    ) {
        fun isDone() = responses.size > 2 || tries.size > 3

        fun addResponse(msg: String) {
            responses += msg
        }

        fun addTriedTime(tried: Long) {
            tries += tried
        }
    }
}
