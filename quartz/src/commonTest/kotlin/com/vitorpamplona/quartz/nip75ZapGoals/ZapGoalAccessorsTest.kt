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

import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZapGoalAccessorsTest {
    private val alice = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val bob = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eventA = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventB = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val relay = "wss://relay.damus.io/"
    private val address = "30023:$alice:article"

    private fun goal(vararg tags: Array<String>) = ZapGoalEvent("00".repeat(32), alice, 1, arrayOf(arrayOf("amount", "21000"), *tags), "Fund it", "00".repeat(64))

    @Test
    fun beneficiariesAreTheSplitsAZapWouldPay() {
        val goal =
            goal(
                arrayOf("zap", bob, relay, "2"),
                arrayOf("zap", "me@getalby.com"),
                // not a 64-hex key, and a zero weight: neither is paid
                arrayOf("zap", "zz".repeat(32), relay, "1"),
                arrayOf("zap", alice, relay, "0"),
                // a hex key with trailing junk is not a key either
                arrayOf("zap", alice + "00", relay, "1"),
            )

        assertEquals(listOf(bob, "me@getalby.com"), goal.beneficiaries().map { (it as? ZapSplitSetup)?.pubKeyHex ?: (it as ZapSplitSetupLnAddress).lnAddress })
        assertEquals(listOf(bob), goal.linkedPubKeys())
    }

    @Test
    fun theGoalLinksOneEventAndOneAddress() {
        val goal = goal(arrayOf("e", eventA, relay), arrayOf("a", "not-an-address"), arrayOf("a", address, relay))

        assertEquals(eventA, goal.goalEventId())
        assertEquals(address, goal.goalAddressId())
        assertEquals(listOf(eventA), goal.linkedEventIds())
        assertEquals(listOf(address), goal.linkedAddressIds())
    }

    @Test
    fun anUnlinkedGoalHasNoTarget() {
        val goal = goal()

        assertNull(goal.goalEventId())
        assertNull(goal.goalAddressId())
    }
}
