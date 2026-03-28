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
import com.vitorpamplona.quartz.nip01Core.relay.client.DefaultNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class NostrClientQueryCountTest : BaseNostrClientTest() {
    val fiatjaf = "wss://pyramid.fiatjaf.com".normalizeRelayUrl()
    val utxo = "wss://news.utxo.one".normalizeRelayUrl()

    val metadata = Filter(kinds = listOf(0))
    val outboxRelays = Filter(kinds = listOf(10002))

    @Test
    fun testQueryCountSuspend() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = DefaultNostrClient(socketBuilder, appScope)

            val result = client.count(fiatjaf, metadata)

            assertTrue((result?.count ?: 0) > 1)

            client.disconnect()
            appScope.cancel()
        }

    @Test
    fun testQueryCountSuspendAllEvents() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = DefaultNostrClient(socketBuilder, appScope)

            val result = client.count(fiatjaf, Filter())

            assertTrue((result?.count ?: 0) > 1)

            client.disconnect()
            appScope.cancel()
        }

    @Test
    fun testQueryCountSuspendMultipleRelays() =
        runBlocking {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = DefaultNostrClient(socketBuilder, appScope)

            val results =
                client.count(
                    mapOf(
                        fiatjaf to listOf(metadata, outboxRelays),
                        utxo to listOf(metadata, outboxRelays),
                    ),
                )

            results.forEach { (url, countResult) ->
                println("${url.url}: ${countResult.count}")
                assertTrue(countResult.count > 1)
            }

            client.disconnect()
            appScope.cancel()
        }
}
