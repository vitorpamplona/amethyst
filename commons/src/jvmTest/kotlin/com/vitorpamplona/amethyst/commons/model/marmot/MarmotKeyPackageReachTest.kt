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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A KeyPackage is recorded as reaching only the relays that accepted it, so the watcher retries the rest. */
class MarmotKeyPackageReachTest {
    private val a = NormalizedRelayUrl("wss://a.relay.invalid/")
    private val b = NormalizedRelayUrl("wss://b.relay.invalid/")
    private val c = NormalizedRelayUrl("wss://c.relay.invalid/")

    private fun keyPackage(
        id: Char,
        createdAt: Long,
    ) = KeyPackageEvent(id.toString().repeat(64), "f".repeat(64), createdAt, arrayOf(arrayOf("d", "slot")), "", "0".repeat(128))

    @Test
    fun onlyAcceptingRelaysAreRecorded() =
        runBlocking {
            val reach = MarmotKeyPackageReach()
            val kp = keyPackage('1', 100)
            val accepted = reach.publish(kp, setOf(a, b, c)) { _, relays -> relays.associateWith { it == a } }
            assertEquals(setOf(a), accepted)
            val (held, reached) = reach.held().single()
            assertEquals(kp.id, held.id)
            assertEquals(setOf(a), reached)
            assertEquals("the refusing and silent relays are tried again", setOf(b, c), MarmotKeyPackageRelays.missing(setOf(a, b, c), reached))

            // The retry reaches one more; the record grows.
            reach.publish(kp, setOf(b, c)) { _, relays -> relays.associateWith { it == b } }
            assertEquals(setOf(a, b), reach.held().single().second)
        }

    @Test
    fun aPublishNoRelayAcceptedRecordsNothingAndANewerKeyPackageReplacesTheOld() =
        runBlocking {
            val reach = MarmotKeyPackageReach()
            assertTrue(reach.publish(keyPackage('1', 100), setOf(a)) { _, relays -> relays.associateWith { false } }.isEmpty())
            assertTrue(reach.isEmpty())

            reach.record(keyPackage('1', 100), setOf(a))
            reach.record(keyPackage('2', 200), setOf(b))
            assertEquals(
                "2".repeat(64),
                reach
                    .held()
                    .single()
                    .first.id,
            )
            reach.record(keyPackage('1', 100), setOf(c))
            assertEquals(
                "an older one never comes back",
                "2".repeat(64),
                reach
                    .held()
                    .single()
                    .first.id,
            )
        }
}
