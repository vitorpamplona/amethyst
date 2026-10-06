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
package com.vitorpamplona.quartz.nip75ZapGoals

import kotlin.test.Test
import kotlin.test.assertEquals

class ZapGoalHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val friend = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val cited = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val quoted = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val citedEvent = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val relay = "wss://relay.damus.io/"
    private val relay2 = "wss://nos.lol/"

    @Test
    fun zapSplitBeneficiariesAreLinkedPeople() {
        val goal =
            ZapGoalEvent(
                "00".repeat(32),
                author,
                1700000000,
                arrayOf(
                    arrayOf("amount", "210000"),
                    arrayOf("p", friend, relay),
                    arrayOf("zap", cited, relay2, "2"),
                    arrayOf("zap", quoted, "", "1"),
                    // zero weight pays nothing: not a beneficiary
                    arrayOf("zap", citedEvent, relay, "0"),
                ),
                "Fund the relay",
                "00".repeat(64),
            )

        assertEquals(listOf(friend, cited, quoted), goal.linkedPubKeys())
        assertEquals(listOf(friend to relay, cited to relay2), goal.pubKeyHints().map { it.pubkey to it.relay.url })
    }
}
