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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An `auth-required:` OK keeps an event in the outbox only while someone could answer the relay's
 * challenge. With no auth responder on the client it is the relay's verdict, like any other OK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientAuthRequiredOutboxTest {
    private val url = NormalizedRelayUrl("wss://auth-gated.example.com/")
    private val event: Event = NostrSignerSync().sign(1_700_000_000L, 1, emptyArray(), "hello")

    /** A responder that tracks no state: its snapshot is IDLE, so an AUTH may still land. */
    private val responder =
        object : IAuthStatus {
            override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = false
        }

    private fun TestScope.publishRefusedForAuth(withResponder: Boolean): Int {
        val builder = FakeWebsocketBuilder()
        val client = NostrClient(builder, this)
        try {
            if (withResponder) client.addAuthResponder(responder)
            advanceTimeBy(500)
            runCurrent()
            client.publish(event, setOf(url))
            runCurrent()
            builder.lastListener.onOpen(50, false)
            runBlocking { builder.lastListener.onMessage("""["OK","${event.id}",false,"auth-required: sign in first"]""") }
            runCurrent()
            return client.activeOutboxCache(url).size
        } finally {
            client.close()
        }
    }

    @Test
    fun withNobodyToAnswerTheChallengeTheRefusalIsFinal() =
        runTest {
            assertEquals(0, publishRefusedForAuth(withResponder = false))
        }

    @Test
    fun withAResponderTheEventWaitsForTheAuth() =
        runTest {
            assertEquals(1, publishRefusedForAuth(withResponder = true))
        }
}
