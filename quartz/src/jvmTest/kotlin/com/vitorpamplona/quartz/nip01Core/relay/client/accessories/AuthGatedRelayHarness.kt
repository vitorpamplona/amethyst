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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * A relay that genuinely requires NIP-42 AUTH, wired to a real [NostrClient].
 *
 * Everything on the path is production code:
 *  - the relay is quartz's [NostrServer] under [FullAuthPolicy], which challenges
 *    on connect and answers every un-authenticated REQ / COUNT with
 *    `CLOSED auth-required:`;
 *  - the transport is [InProcessWebSocket], the same one geode's tests use, so
 *    the client sees ordinary frames arriving on another thread;
 *  - the responder is a real [RelayAuthenticator] attached to the same client,
 *    exactly as `AccountViewModel` (Amethyst) and `Context` (amy) attach it.
 *
 * Only the socket is in-process. That is the point: it makes the measurement
 * deterministic and offline while leaving the NIP-42 exchange real.
 *
 * @param signer signs the AUTH challenge. `null` models a responder that is
 *   attached but cannot satisfy this relay — a user who ignores the NIP-55
 *   prompt, a policy that declines the relay, a remote bunker that times out —
 *   which is the "auth never satisfies" case the accessories must not confuse
 *   with silence.
 * @param attachAuthenticator false leaves the client with no NIP-42 responder
 *   at all, which is the honest "nobody can answer" configuration.
 * @param signDelayMs how long the responder takes to produce the signature.
 *   Non-zero is what makes these tests **deterministic**: the challenge is issued
 *   at connect and answered concurrently with the first REQ, so with an instant
 *   signer the AUTH sometimes wins the race and the REQ is never refused at all —
 *   the very path under test would silently not run. A delay longer than the REQ
 *   round-trip guarantees the `auth-required:` refusal happens first, every time.
 */
class AuthGatedRelayHarness(
    private val signer: NostrSigner? = NostrSignerInternal(KeyPair()),
    attachAuthenticator: Boolean = true,
    private val signDelayMs: Long = 0,
) : AutoCloseable {
    private val store = EventStore(null)

    private val server =
        NostrServer(
            store = store,
            policyBuilder = { FullAuthPolicy(URL) },
        )

    private val builder =
        object : WebsocketBuilder {
            override fun build(
                url: NormalizedRelayUrl,
                out: WebSocketListener,
            ): WebSocket = InProcessWebSocket(server, out)
        }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val client = NostrClient(builder, scope)

    /**
     * How many times the responder was asked to sign — including the re-auth an
     * `auth-required:` CLOSED triggers, which is deduped before it reaches the wire.
     * A count of 2 for one connection is normal, not a bug.
     */
    val signedAuths = AtomicInteger(0)

    private val authenticator =
        if (attachAuthenticator) {
            RelayAuthenticator(
                client = client,
                scope = scope,
                signWithAllLoggedInUsers = { _, template, _ ->
                    if (signDelayMs > 0) delay(signDelayMs)
                    val s = signer
                    if (s == null) {
                        emptyList()
                    } else {
                        signedAuths.incrementAndGet()
                        listOf(s.sign(template))
                    }
                },
            )
        } else {
            null
        }

    /** Stores [count] kind-1 events the relay will only serve to an authenticated caller. */
    suspend fun preload(count: Int) {
        val author = NostrSignerInternal(KeyPair())
        repeat(count) { i ->
            store.insert(
                author.sign(
                    Event.build(
                        kind = 1,
                        content = "gated-$i",
                        createdAt = TimeUtils.now() - i,
                    ) {
                        // A tag, so the event is not degenerate for the store's indexes.
                        add(ETag.assemble("0".repeat(64), null, null))
                    },
                ),
            )
        }
    }

    override fun close() {
        authenticator?.destroy()
        client.close()
        server.close()
        scope.cancel()
    }

    companion object {
        /** Loopback so the production [RelayUrlNormalizer] accepts a plain `ws://`. */
        val URL: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7781/")
    }
}
