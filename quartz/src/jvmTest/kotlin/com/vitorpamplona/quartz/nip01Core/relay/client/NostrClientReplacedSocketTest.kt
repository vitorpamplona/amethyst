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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.EOFException
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A socket can deliver its last messages and its end after the relay closed it (the OkHttp
 * adapter drains what it had received before reporting the end), and by then a reconnect may
 * have replaced it. What the old socket says must not reach the new session: a late EOSE would
 * end the new REQ's page early, and a late failure would tear the new socket down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientReplacedSocketTest {
    private val url = NormalizedRelayUrl("wss://replaced.example.com/")

    @Test
    fun aReplacedSocketsLastWordsDoNotReachTheNewSession() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            val heard = Collections.synchronizedList(mutableListOf<String>())
            val listener =
                object : SubscriptionListener {
                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        heard.add("eose")
                    }

                    override fun onCannotConnect(
                        relay: NormalizedRelayUrl,
                        message: String,
                        forFilters: List<Filter>?,
                    ) {
                        heard.add("cannot-connect")
                    }
                }
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("sub", mapOf(url to listOf(Filter(kinds = listOf(1)))), listener)
                val old = builder.lastListener
                old.onOpen(50, false)

                // A full reconnect replaces the socket.
                client.reconnect(onlyIfChanged = false)
                advanceTimeBy(500)
                runCurrent()
                assertEquals(2, builder.connectAttempts, "the socket was replaced")
                val current = builder.lastListener
                current.onOpen(50, false)

                old.onMessage("[\"EOSE\",\"sub\"]")
                old.onFailure(EOFException(), null, null)
                assertEquals(emptyList(), heard.toList(), "the old socket's EOSE and end were ignored")

                current.onMessage("[\"EOSE\",\"sub\"]")
                assertEquals(listOf("eose"), heard.toList(), "the current socket is heard")
            } finally {
                client.close()
            }
        }
}
