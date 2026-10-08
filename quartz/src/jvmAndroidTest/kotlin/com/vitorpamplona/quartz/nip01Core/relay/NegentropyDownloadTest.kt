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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
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
 * The download half of a NIP-77 sync, against relays seen dropping it in production: the
 * reconcile named every id, and the `REQ`s for those ids came back short.
 */
class NegentropyDownloadTest {
    private val author = "ab".repeat(32)

    /** relay.nostr.net served 100 of a 500-id `REQ`, then `EOSE`: 1,100 of 5,219 downloaded. */
    @Test
    fun aRelayThatCapsTheIdsReqIsAskedForTheRest() =
        runBlocking {
            val result = sync(30, Filter(kinds = listOf(1))) { LimitsPolicy(RelayLimits(defaultLimit = 5, maxLimit = 10)) }

            assertEquals(30, result.needCount)
            assertEquals(30, result.downloaded, "every id the reconcile named is downloaded, ten at a time")
        }

    /** relay.conduit.market closes an ids-only filter: "wildcard subscriptions are not available". */
    @Test
    fun aRelayThatRefusesBareIdsGetsTheSyncKinds() =
        runBlocking {
            val result = sync(30, Filter(kinds = listOf(1))) { RefusesBareIds() }

            assertEquals(30, result.downloaded)
        }

    @Test
    fun aScopeWithoutKindsAddsItsAuthorsAfterARefusal() =
        runBlocking {
            val result = sync(30, Filter(authors = listOf(author))) { RefusesBareIds() }

            assertEquals(30, result.downloaded)
        }

    private suspend fun sync(
        count: Int,
        filter: Filter,
        policy: () -> IRelayPolicy,
    ): NegentropySyncResult {
        val hub = InProcessRelays(defaultPolicy = policy)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val client = NostrClient(hub, scope)
        try {
            hub.getOrCreate(InProcessRelays.DEFAULT_URL).preload(SyntheticEvents.batch(count, kind = 1) { author })
            return withTimeout(20_000) {
                client.negentropySync(relay = InProcessRelays.DEFAULT_URL, filter = filter, idleTimeoutMs = 5_000) { }
            }
        } finally {
            client.disconnect()
            scope.cancel()
            hub.close()
        }
    }

    /** Refuses a `REQ` filter that names ids and nothing else, as conduit does. */
    private class RefusesBareIds : PassThroughPolicy() {
        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> =
            if (cmd.filters.any { it.ids != null && it.kinds == null && it.authors == null && it.tags == null }) {
                PolicyResult.Rejected("blocked: wildcard subscriptions are not available when private messages are enabled")
            } else {
                PolicyResult.Accepted(cmd)
            }
    }
}
