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
package com.vitorpamplona.quartz.nip64Chess.challenge.accept

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.tags.ChallengeEventTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveChessGameAcceptHintProviderTest {
    private val me = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val challenger = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val challenge = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!

    private fun accept(vararg tags: Array<String>) = LiveChessGameAcceptEvent("00".repeat(32), me, 1, arrayOf(arrayOf("d", "g1"), *tags), "", "00".repeat(64))

    @Test
    fun aChallengeSeenWithoutARelayLinksButDoesNotHintTheChallengersKeyAsARelay() {
        // A writer that drops the empty relay slot shifts the author into it: ["e", <id>, <pubkey>].
        val event = accept(arrayOf("e", challenge, challenger))

        assertEquals(listOf(challenge), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
    }

    @Test
    fun assembleKeepsTheEmptyRelaySlot() {
        val tag = ChallengeEventTag.assemble(challenge, null, challenger)
        assertEquals(listOf("e", challenge, "", challenger), tag.toList())

        val event = accept(tag)
        assertEquals(listOf(challenge), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
    }

    @Test
    fun aChallengeWithItsRelayIsHinted() {
        val event = accept(ChallengeEventTag.assemble(challenge, relay, challenger))

        assertEquals(listOf(challenge), event.linkedEventIds())
        assertEquals(listOf(challenge to relay), event.eventHints().map { it.eventId to it.relay })
    }
}
