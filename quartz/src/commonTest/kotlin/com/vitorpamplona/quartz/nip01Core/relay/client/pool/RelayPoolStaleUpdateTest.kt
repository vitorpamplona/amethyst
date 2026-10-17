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

import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RelayPool.updatePool] can be handed a stale set: NostrClient samples what its subscriptions,
 * counts and outbox want, and by the time the pool acts a publish may have sent an EVENT on a
 * relay the sample did not have. Measured with a one-at-a-time publisher: the pool dropped that
 * relay under the in-flight EVENT, its OK never came, and the publish stalled ~50 s until the
 * keep-alive redialed and the outbox re-sent it (answered `duplicate:`). The drop asks again first.
 */
class RelayPoolStaleUpdateTest {
    private val url = NormalizedRelayUrl("wss://relay.example.com/")
    private val other = NormalizedRelayUrl("wss://other.example.com/")

    private fun openedPool(): RelayPool {
        val sockets = FakeWebsocketBuilder()
        val pool = RelayPool(sockets)
        pool.getOrCreateRelay(url).connect()
        sockets.lastListener.onOpen(pingMillis = 10, compression = false)
        return pool
    }

    @Test
    fun aRelayStillWantedSurvivesAStaleSet() {
        val pool = openedPool()

        val added = pool.updatePool(emptySet()) { it == url }

        assertFalse(added)
        assertEquals(setOf(url), pool.availableRelays.value)
        assertEquals(setOf(url), pool.connectedRelays.value, "its socket stays open under the in-flight EVENT")
    }

    @Test
    fun aRelayNobodyWantsIsStillDropped() {
        val pool = openedPool()

        pool.updatePool(emptySet()) { false }

        assertEquals(emptySet(), pool.availableRelays.value)
        assertEquals(emptySet(), pool.connectedRelays.value)
    }

    @Test
    fun theUpdateSaysWhenItAddedARelay() {
        val pool = openedPool()

        assertTrue(pool.updatePool(setOf(url, other)), "other joined, and joining does not dial it")
        assertFalse(pool.updatePool(setOf(url, other)))
    }
}
