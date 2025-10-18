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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface IAuthStatus {
    fun hasFinishedAuthentication(relay: NormalizedRelayUrl): Boolean
}

object EmptyIAuthStatus : IAuthStatus {
    override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = true
}

class RelayAuthenticator(
    val client: INostrClient,
    val scope: CoroutineScope,
    val signWithAllLoggedInUsers: suspend (EventTemplate<RelayAuthEvent>) -> List<RelayAuthEvent>,
) : IAuthStatus {
    private val authStatus = mutableMapOf<NormalizedRelayUrl, RelayAuthStatus>()

    private val clientListener =
        object : IRelayClientListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is AuthMessage -> authenticate(relay, msg)
                    is OkMessage -> checkAuthResults(relay, msg)
                }
            }

            override fun onConnecting(relay: IRelayClient) {
                authStatus.put(relay.url, RelayAuthStatus())
            }

            override fun onDisconnected(relay: IRelayClient) {
                authStatus.remove(relay.url)
            }
        }

    private fun authenticate(
        relay: IRelayClient,
        msg: AuthMessage,
    ) {
        scope.launch {
            val ev = RelayAuthEvent.build(relay.url, msg.challenge)
            signWithAllLoggedInUsers(ev).forEach { authEvent ->
                // only send replies to new challenges to avoid infinite loop:
                if (authStatus[relay.url]?.saveAuthSubmission(authEvent) == true) {
                    relay.sendIfConnected(AuthCmd(authEvent))
                }
            }
        }
    }

    private fun checkAuthResults(
        relay: IRelayClient,
        msg: OkMessage,
    ) {
        // if this is the OK of an auth event, renew all subscriptions and resend all outgoing events.
        if (authStatus[relay.url]?.checkAuthResults(msg.eventId, msg.success) == true) {
            client.renewFilters(relay)
        }
    }

    override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = authStatus[relay]?.hasFinishedAllAuths() != false

    init {
        Log.d("RelayAuthenticator", "Init, Subscribe")
        client.subscribe(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayAuthenticator", "Destroy, Unsubscribe")
        client.unsubscribe(clientListener)
    }
}
