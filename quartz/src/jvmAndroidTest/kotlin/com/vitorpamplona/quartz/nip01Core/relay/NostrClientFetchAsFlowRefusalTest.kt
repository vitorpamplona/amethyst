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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.fetchAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [fetchAsFlow] completes on EOSE — and, since the relay's answer is just as final, on a
 * refused REQ or an unreachable relay. It used to stay open for both, so a screen collecting
 * `lastOrNull()` (the NIP-43 members screen) sat in its loading state until it was left.
 */
class NostrClientFetchAsFlowRefusalTest {
    @Test
    fun aRefusedReqCompletesTheFlow() =
        runBlocking {
            val hub = InProcessRelays(defaultPolicy = { RefusesReq() })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val outcome = withTimeoutOrNull(5_000) { client.fetchAsFlow(InProcessRelays.DEFAULT_URL, Filter(kinds = listOf(1))).lastOrNull() ?: emptyList() }
                assertEquals(emptyList(), outcome, "the flow must complete, with no events")
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    @Test
    fun anUnreachableRelayCompletesTheFlow() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() }, scope)
            try {
                val outcome = withTimeoutOrNull(5_000) { client.fetchAsFlow("ws://127.0.0.1:1/".normalizeRelayUrl(), Filter(kinds = listOf(1))).lastOrNull() ?: emptyList() }
                assertEquals(emptyList(), outcome, "the flow must complete, with no events")
            } finally {
                client.disconnect()
                scope.cancel()
            }
        }

    private class RefusesReq : PassThroughPolicy() {
        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = PolicyResult.Rejected("restricted: members only")
    }
}
