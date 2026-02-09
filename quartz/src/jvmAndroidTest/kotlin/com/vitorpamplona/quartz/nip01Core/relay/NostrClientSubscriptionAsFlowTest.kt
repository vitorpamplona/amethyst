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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.reqAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrClientSubscriptionAsFlowTest : BaseNostrClientTest() {
    fun List<Event>.printDates(): String {
        val starting = this[0].createdAt
        return joinToString { (it.createdAt - starting).toString() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNostrClientSubscriptionAsFlow() =
        runTest {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val flow =
                client.reqAsFlow(
                    relay = "wss://relay.damus.io",
                    filter =
                        Filter(
                            kinds = listOf(MetadataEvent.KIND),
                            limit = 10,
                        ),
                )

            var feedStates = listOf<Event>()
            val job =
                launch {
                    flow.collect {
                        Log.d("ZZ", "List timestamp deltas ${it.printDates()}")
                        feedStates = it
                    }
                }

            // Advance the test dispatcher to ensure emissions are processed
            while (feedStates.size < 10) {
                advanceUntilIdle()
            }

            job.cancel() // Cancel the collection job

            client.disconnect()
            appScope.cancel()

            assertEquals(10, feedStates.size)
        }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Test
    fun testNostrClientSubscriptionAsFlowDebouncing() =
        runTest {
            val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(socketBuilder, appScope)

            val flow =
                client.reqAsFlow(
                    relay = "wss://relay.damus.io",
                    filter =
                        Filter(
                            kinds = listOf(MetadataEvent.KIND),
                            limit = 10,
                        ),
                )

            var feedStates = listOf<Event>()
            val job =
                launch {
                    flow.debounce(100).collect {
                        Log.d("ZZ", "List timestamp deltas ${it.printDates()}")
                        feedStates = it
                    }
                }

            // Advance the test dispatcher to ensure emissions are processed
            while (feedStates.size < 10) {
                advanceUntilIdle()
            }

            job.cancel() // Cancel the collection job

            client.disconnect()
            appScope.cancel()

            assertEquals(10, feedStates.size)
        }
}
