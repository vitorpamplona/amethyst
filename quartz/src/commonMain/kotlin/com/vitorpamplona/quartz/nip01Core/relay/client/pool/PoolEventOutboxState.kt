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
import kotlin.concurrent.Volatile

class PoolEventOutboxState(
    val event: Event,
    @Volatile var relaysRemaining: Set<NormalizedRelayUrl>,
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

    /** Records a send attempt to [url]. Returns true if the retry budget is now exhausted and the
     *  relay was dropped (i.e. we gave up delivering this event to [url]). */
    fun newTry(url: NormalizedRelayUrl): Boolean {
        val currentTries = failures[url]
        if (currentTries != null) {
            currentTries.addTriedTime(TimeUtils.now())
            if (currentTries.isDone()) {
                relaysRemaining = relaysRemaining - url
                failures = failures - url
                return true
            }
        } else {
            failures = failures + (url to Tries(listOf(TimeUtils.now())))
        }
        return false
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
        } else if (message.isAuthRequired()) {
            // NIP-42: the relay wants us to authenticate before it will accept
            // this event. This is NOT a real rejection — once RelayAuthenticator
            // completes the AUTH handshake, syncFilters re-sends the event. So we
            // keep it pending and must NOT record this as a failure response,
            // otherwise a relay that NAKs every unauthed EVENT would exhaust the
            // retry budget (Tries.isDone) and drop the event before AUTH lands.
            // Leave relaysRemaining and failures untouched.
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

    /**
     * NIP-42 machine-readable prefix a relay uses to tell us an event was held
     * back pending authentication. The event should be re-sent after AUTH, not
     * retried-then-discarded like an ordinary failure.
     */
    fun String.isAuthRequired() = this.startsWith("auth-required:") || this == "auth-required"

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
