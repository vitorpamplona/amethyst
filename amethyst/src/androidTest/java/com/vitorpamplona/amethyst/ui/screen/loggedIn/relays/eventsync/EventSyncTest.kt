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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.service.okhttp.DefaultContentTypeInterceptor
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventSyncTest {
    companion object {
        val vitor = "wss://vitor.nostr1.com".normalizeRelayUrl()
        val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val rootClient =
            OkHttpClient
                .Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(DefaultContentTypeInterceptor("Amethyst/v1.05"))
                .build()
        val socketBuilder = BasicOkHttpWebSocket.Builder { url -> rootClient }
    }

    @Test
    fun testSync() =
        runBlocking {
            val sync =
                EventSync(
                    accountPubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                    relayDb = {
                        listOf(Constants.mom, Constants.nos)
                    },
                    outboxTargets = { setOf(vitor) },
                    inboxTargets = { setOf(vitor) },
                    dmTargets = { setOf(vitor) },
                    clientBuilder = {
                        NostrClient(socketBuilder, appScope)
                    },
                    scope = appScope,
                )

            sync.runSync()
        }
}
