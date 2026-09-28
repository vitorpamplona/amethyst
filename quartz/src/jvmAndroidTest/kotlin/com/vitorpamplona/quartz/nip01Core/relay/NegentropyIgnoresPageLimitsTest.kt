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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A relay's REQ page limits must not bound a NIP-77 reconcile. With them applied, a relay with
 * `default_limit` 5 reconciled the newest 5 of 30 events and called the set complete.
 */
class NegentropyIgnoresPageLimitsTest {
    @Test
    fun aReconcileCoversTheWholeSetPastDefaultAndMaxLimit() =
        runBlocking {
            val hub = InProcessRelays(defaultPolicy = { LimitsPolicy(RelayLimits(defaultLimit = 5, maxLimit = 10)) })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                hub.getOrCreate(InProcessRelays.DEFAULT_URL).preload(SyntheticEvents.batch(30, kind = 1))

                val result =
                    withTimeout(20_000) {
                        client.negentropySync(relay = InProcessRelays.DEFAULT_URL, filter = Filter(kinds = listOf(1))) { }
                    }

                // The reconcile itself — how many ids the relay said it holds. The download that
                // follows is ordinary REQs, which default_limit rightly still pages.
                assertEquals(30, result.needCount, "the reconcile saw the whole set, not a default_limit page")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }
}
