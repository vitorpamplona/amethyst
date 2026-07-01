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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Relay auth is automatic — the relay sends the AUTH challenge and
 * [RelayAuthenticator] signs a reply in a fire-and-forget `scope.launch`. With an
 * external NIP-55 Android signer, the user can simply ignore the approval prompt,
 * which surfaces as [SignerExceptions.TimedOutException] from the signing lambda.
 *
 * The host scope passed in by Amethyst (`viewModelScope` + SupervisorJob) carries
 * no [CoroutineExceptionHandler], so before the fix an uncaught throwable from that
 * launch reached the platform default handler and crashed the whole app
 * (TimedOutException: "Could not sign: User didn't accept or reject in time.").
 *
 * These tests pin the contract: signing failures during auth must be swallowed,
 * and the happy path must still send the AUTH reply.
 */
class RelayAuthenticatorTimeoutTest {
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        @Volatile var captured: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            captured = listener
        }
    }

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        val sent = mutableListOf<Command>()

        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) {
            sent.add(cmd)
        }

        override fun sendIfConnected(cmd: Command) {
            sent.add(cmd)
        }

        override fun disconnect() = Unit
    }

    @Test
    fun signerTimeoutDuringAuthIsSwallowedAndDoesNotReachExceptionHandler() =
        runBlocking {
            var escaped: Throwable? = null
            val handler = CoroutineExceptionHandler { _, t -> escaped = t }
            // Unconfined runs the launched body eagerly so the assertion is deterministic.
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + handler)

            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, _ ->
                    throw SignerExceptions.TimedOutException("User didn't accept or reject in time.")
                },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")

            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))
            listener.onConnecting(relay)
            listener.onIncomingMessage(relay, "", AuthMessage("challenge-123"))

            assertNull(escaped, "TimedOutException must not escape the auth launch")
            assertTrue(relay.sent.isEmpty(), "No AUTH reply should be sent when signing fails")
        }

    @Test
    fun successfulAuthStillSendsAuthCommand() =
        runBlocking {
            var escaped: Throwable? = null
            val handler = CoroutineExceptionHandler { _, t -> escaped = t }
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + handler)

            val signer = NostrSignerInternal(KeyPair())
            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, _ ->
                    listOf(RelayAuthEvent.create(relay.url, "challenge-123", signer))
                },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")

            listener.onConnecting(relay)
            listener.onIncomingMessage(relay, "", AuthMessage("challenge-123"))

            assertNull(escaped, "Happy path must not surface any exception")
            assertTrue(
                relay.sent.any { it is AuthCmd },
                "A signed AUTH reply should be sent on the happy path",
            )
        }
}
