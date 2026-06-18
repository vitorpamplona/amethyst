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
package com.vitorpamplona.amethyst.service.localStore

import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalEventStoreTest {
    @Test
    fun keepsUserDirectoryKindsAndNothingElse() {
        // kind 0, relay lists and trusted assertions are persisted...
        assertTrue(MetadataEvent.KIND in LocalEventStore.PERSISTED_KINDS)
        assertTrue(AdvertisedRelayListEvent.KIND in LocalEventStore.PERSISTED_KINDS) // 10002
        assertTrue(ContactCardEvent.KIND in LocalEventStore.PERSISTED_KINDS) // 30382

        // ...but feed content is not.
        assertFalse(1 in LocalEventStore.PERSISTED_KINDS) // text note
        assertFalse(3 in LocalEventStore.PERSISTED_KINDS) // contact list
        assertFalse(7 in LocalEventStore.PERSISTED_KINDS) // reaction
    }

    @Test
    fun routesLocalUrlInProcessAndDelegatesEverythingElse() {
        val other = NormalizedRelayUrl("wss://relay.example.com/")
        val sentinel = NoopWebSocket()
        val delegate =
            object : WebsocketBuilder {
                var builtUrl: NormalizedRelayUrl? = null

                override fun build(
                    url: NormalizedRelayUrl,
                    out: WebSocketListener,
                ): WebSocket {
                    builtUrl = url
                    return sentinel
                }

                // delegate refuses everything — proves the local relay bypasses it.
                override fun canConnect(url: NormalizedRelayUrl) = false
            }

        // DB stays closed: nothing here touches the local relay's server.
        val store = LocalEventStore(File("unused/events.db"), CoroutineScope(Dispatchers.Unconfined))
        val builder = LocalRelayWebsocketBuilder(delegate, store)

        // A non-local URL is delegated to the real transport untouched.
        assertSame(sentinel, builder.build(other, NoopListener))
        assertEquals(other, delegate.builtUrl)

        // The local relay is always reachable even though the delegate refuses.
        assertTrue(builder.canConnect(LocalEventStore.LOCAL_RELAY_URL))
        // Non-local connectivity defers to the delegate.
        assertFalse(builder.canConnect(other))
    }

    private object NoopListener : WebSocketListener {
        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) = Unit

        override fun onMessage(text: String) = Unit

        override fun onClosed(
            code: Int,
            reason: String,
        ) = Unit

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) = Unit
    }

    private class NoopWebSocket : WebSocket {
        override fun needsReconnect() = false

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun send(msg: String) = true
    }
}
