/**
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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import android.util.Log
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RelayAuthenticator(
    val client: NostrClient,
    val scope: CoroutineScope,
    val authenticate: suspend (challenge: String, relay: IRelayClient) -> Unit,
) {
    private val clientListener =
        object : IRelayClientListener {
            override fun onAuth(
                relay: IRelayClient,
                challenge: String,
            ) {
                scope.launch {
                    authenticate(challenge, relay)
                }
            }
        }

    init {
        Log.d("${this.javaClass.simpleName}", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("${this.javaClass.simpleName}", "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
