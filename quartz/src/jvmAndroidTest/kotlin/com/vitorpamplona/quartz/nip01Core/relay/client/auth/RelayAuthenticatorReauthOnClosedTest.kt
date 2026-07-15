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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * NIP-42: a relay delivers the challenge only in an `AUTH` message, never in a `CLOSED`. When a REQ
 * is refused with an `auth-required:` CLOSED *after* the initial AUTH (e.g. a Concord channel-plane
 * REQ mounted once the control plane folds in its channel stream keys), the relay does not
 * re-challenge — the client is expected to reuse the stored challenge. These tests pin that:
 *  - a REQ refused with `auth-required:` re-signs against the stored challenge and sends AUTH for
 *    the newly-available identities;
 *  - the dedup makes it loop-safe (a second refusal with no new keys sends nothing);
 *  - a non-`auth-required` CLOSED never triggers a re-auth;
 *  - the re-auth is non-interactive (never asks the signing lambda to prompt).
 */
class RelayAuthenticatorReauthOnClosedTest {
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

    private fun authedPubKeys(relay: FakeRelayClient) = relay.sent.filterIsInstance<AuthCmd>().map { it.event.pubKey }

    /** Acks the newest AUTH the client sent, as a well-behaved relay would, so the auth isn't left in flight. */
    private fun ackNewestAuth(
        listener: RelayConnectionListener,
        relay: FakeRelayClient,
    ) {
        val newest =
            relay.sent
                .filterIsInstance<AuthCmd>()
                .last()
                .event
        listener.onIncomingMessage(relay, "", OkMessage.accepted(newest.id))
    }

    @Test
    fun authRequiredClosedReauthsNewlyAvailableKeyReusingStoredChallenge() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

            val control = NostrSignerInternal(KeyPair())
            val channel = NostrSignerInternal(KeyPair())

            // Starts with only the control identity; the channel identity becomes available later
            // (mirrors a Concord control-plane fold revealing a channel stream key).
            val available = mutableListOf(control)
            val interactiveFlags = mutableListOf<Boolean>()

            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, template, interactive ->
                    interactiveFlags.add(interactive)
                    available.map { it.sign(template) }
                },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")
            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

            listener.onConnecting(relay)
            listener.onIncomingMessage(relay, "", AuthMessage("chal-1"))
            // A well-behaved relay acks the control AUTH, so nothing is left in flight.
            ackNewestAuth(listener, relay)

            assertEquals(listOf(control.pubKey), authedPubKeys(relay), "Initial AUTH signs only the control key")
            assertEquals(listOf(true), interactiveFlags, "The fresh AUTH challenge is interactive")

            // Control plane folds → the channel stream key is now available.
            available.add(channel)

            // The channel-plane REQ is refused because the connection isn't AUTHed as the channel key.
            listener.onIncomingMessage(
                relay,
                "",
                ClosedMessage("channel-sub", MachineReadablePrefix.AUTH_REQUIRED.format("authenticate first")),
            )

            assertEquals(
                listOf(control.pubKey, channel.pubKey),
                authedPubKeys(relay),
                "The auth-required CLOSED re-auths, sending AUTH for the newly-available channel key (control deduped)",
            )
            assertEquals(false, interactiveFlags.last(), "A re-auth off a CLOSED is non-interactive")
            ackNewestAuth(listener, relay)

            // Loop-safety: a second refusal with no new keys must send nothing more.
            listener.onIncomingMessage(
                relay,
                "",
                ClosedMessage("channel-sub", MachineReadablePrefix.AUTH_REQUIRED.format("still authenticating")),
            )
            assertEquals(
                listOf(control.pubKey, channel.pubKey),
                authedPubKeys(relay),
                "A repeated auth-required CLOSED with no new identity is a no-op (dedup by pubkey+challenge)",
            )
        }

    @Test
    fun burstOfAuthRequiredClosedsWhileAnAuthIsInFlightIsCoalesced() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val control = NostrSignerInternal(KeyPair())
            val channel = NostrSignerInternal(KeyPair())
            val available = mutableListOf(control)

            var signCalls = 0
            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, template, _ ->
                    signCalls++
                    available.map { it.sign(template) }
                },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")
            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

            listener.onConnecting(relay)
            listener.onIncomingMessage(relay, "", AuthMessage("chal-1"))
            // Control AUTH is deliberately NOT acked → it stays in flight.
            available.add(channel)
            val callsBeforeBurst = signCalls

            // A relay refuses every open sub at once. While the control AUTH is unresolved, these must
            // NOT each re-sign (which would re-hit an external signer per ledger-ALLOW account).
            repeat(5) {
                listener.onIncomingMessage(
                    relay,
                    "",
                    ClosedMessage("sub-$it", MachineReadablePrefix.AUTH_REQUIRED.format("auth first")),
                )
            }
            assertEquals(callsBeforeBurst, signCalls, "No re-sign while an AUTH is still in flight")

            // Once the in-flight AUTH resolves, the next refusal re-auths the newly-available key.
            ackNewestAuth(listener, relay)
            listener.onIncomingMessage(
                relay,
                "",
                ClosedMessage("sub-x", MachineReadablePrefix.AUTH_REQUIRED.format("auth first")),
            )
            assertTrue(authedPubKeys(relay).contains(channel.pubKey), "Channel key is authed once the burst settles")
        }

    @Test
    fun nonAuthRequiredClosedDoesNotReauth() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val signer = NostrSignerInternal(KeyPair())

            var signCalls = 0
            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, template, _ ->
                    signCalls++
                    listOf(signer.sign(template))
                },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")
            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

            listener.onConnecting(relay)
            listener.onIncomingMessage(relay, "", AuthMessage("chal-1"))
            val afterInitial = signCalls

            listener.onIncomingMessage(relay, "", ClosedMessage("sub", MachineReadablePrefix.ERROR.format("bad req")))
            listener.onIncomingMessage(relay, "", ClosedMessage("sub", MachineReadablePrefix.RESTRICTED.format("nope")))

            assertEquals(afterInitial, signCalls, "A non-auth-required CLOSED must not trigger a re-auth")
        }

    @Test
    fun authRequiredClosedBeforeAnyChallengeIsIgnored() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            val signer = NostrSignerInternal(KeyPair())

            val client = CapturingClient()
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, template, _ -> listOf(signer.sign(template)) },
            )
            val listener = client.captured ?: error("RelayAuthenticator did not register a listener")
            val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

            listener.onConnecting(relay)
            // No AUTH challenge received yet → no stored challenge → nothing to reuse.
            listener.onIncomingMessage(
                relay,
                "",
                ClosedMessage("sub", MachineReadablePrefix.AUTH_REQUIRED.format("authenticate first")),
            )

            assertTrue(relay.sent.isEmpty(), "Without a stored challenge there is nothing to re-auth with")
            assertFalse(relay.sent.any { it is AuthCmd })
        }
}
