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

    /** `rate-limited:` refusals with a back-off, per relay; see [newResponse]. */
    private var backOffs = mapOf<NormalizedRelayUrl, Int>()

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
        retryAfterMs: Long? = null,
    ) {
        if (success || message.shouldDiscard()) {
            relaysRemaining = relaysRemaining - url
            // Map.minus copies even when the key is absent; most OKs find nothing to forget.
            if (url in failures) failures = failures - url
            if (url in backOffs) backOffs = backOffs - url
        } else if (retryAfterMs != null && (backOffs[url] ?: 0) < MAX_BACK_OFFS) {
            // NIP-01 `rate-limited:` with a back-off: the relay is up and will take the event
            // later; the pool re-sends it once the back-off passes. Like auth-required, that is
            // not a failure of the event, so it must not spend the retry budget — a busy relay
            // pacing a burst would otherwise drop events it was about to accept. Bounded, so a
            // relay that only ever says "later" still ends up a failure.
            backOffs = backOffs + (url to ((backOffs[url] ?: 0) + 1))
            failures = failures - url
        } else if (message.isAuthRequired()) {
            // NIP-42 AUTH challenge in flight. The relay responded, so it's up and simply wants
            // auth first: RelayAuthenticator signs, the relay re-issues OK, and syncFilters()
            // re-pumps this outbox to retry the publish. Reset the retry budget for this relay
            // (clear both tries and responses) so the send attempts accumulated across reconnects /
            // slow AUTH rounds can't exhaust the cap and drop the event before AUTH lands. Note
            // newTry() (the send path) grows `tries` and is NOT auth-aware, so only clearing
            // `responses` would still let a re-pumped event give up here. relaysRemaining is left
            // as-is: the event must stay pending for this relay until AUTH unlocks it.
            failures = failures - url
        } else {
            val currentTries = failures[url]
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

    companion object {
        /** `rate-limited:` back-offs one relay may state for one event before they count as failures. */
        const val MAX_BACK_OFFS = 10
    }

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
