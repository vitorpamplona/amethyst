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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayAuthPromptBusTest {
    private val relay = NormalizedRelayUrl("wss://auth.relay.test")

    @Test
    fun deliversTheUsersChoiceToTheWaitingCaller() =
        runTest {
            val bus = RelayAuthPromptBus()

            val collector = async { bus.prompts.first() }
            val caller = async { bus.requestDecision(relay, emptyList()) }

            collector.await().respond(UserAuthChoice.ALWAYS_ALLOW)
            assertEquals(UserAuthChoice.ALWAYS_ALLOW, caller.await())
        }

    @Test
    fun concurrentChallengesForSameRelayShareOnePrompt() =
        runTest {
            val bus = RelayAuthPromptBus()

            // Capture the single surfaced prompt before the two challenges fire.
            val surfaced = async { bus.prompts.first() }
            val first = async { bus.requestDecision(relay, emptyList()) }
            val second = async { bus.requestDecision(relay, emptyList()) }

            surfaced.await().respond(UserAuthChoice.ALLOW_ONCE)

            // Both waiters get the one answer. If the second had NOT been deduped it would have
            // surfaced its own unanswered prompt and timed out to DISMISS — so this proves dedup.
            assertEquals(UserAuthChoice.ALLOW_ONCE, first.await())
            assertEquals(UserAuthChoice.ALLOW_ONCE, second.await())
        }

    @Test
    fun unansweredPromptTimesOutToDismiss() =
        runTest {
            val bus = RelayAuthPromptBus(timeoutMs = 1_000L)

            // No one ever responds; the call must not hang, it resolves to DISMISS.
            assertEquals(UserAuthChoice.DISMISS, bus.requestDecision(relay, emptyList()))
        }
}
