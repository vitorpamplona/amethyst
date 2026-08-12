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

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A NIP-42 responder's view of where each relay's authentication stands.
 *
 * Implemented by [RelayAuthenticator], which registers itself on its client
 * ([INostrClient.addAuthResponder]) so code that only holds the client — the fetch
 * accessories, say — can ask two questions it otherwise could not:
 *
 *  1. *Is anyone answering AUTH at all?* Waiting out an `auth-required:` refusal is
 *     only ever correct when something can actually answer the challenge; with no
 *     responder attached the wait is pure dead time.
 *  2. *Has this relay's challenge resolved?* See [awaitAuthOutcome] — that is what
 *     lets a refused fetch end on the AUTH's verdict instead of on a timeout.
 */
interface IAuthStatus {
    fun hasFinishedAuthentication(relay: NormalizedRelayUrl): Boolean

    /**
     * Per-relay AUTH state. Default: a constant empty map, so an implementation that
     * only tracks [hasFinishedAuthentication] still compiles and simply reports every
     * relay as [RelayAuthSnapshot.Phase.IDLE].
     */
    val authStateFlow: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>
        get() = NO_AUTH_STATE

    /** Where [relay]'s NIP-42 exchange stands right now. */
    fun authSnapshot(relay: NormalizedRelayUrl): RelayAuthSnapshot = authStateFlow.value[relay] ?: RelayAuthSnapshot.IDLE

    companion object {
        /**
         * Shared so the default [authStateFlow] getter allocates nothing — it is read on
         * every fetch that touches an auth-gated relay.
         */
        val NO_AUTH_STATE: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>> = MutableStateFlow(persistentMapOf<NormalizedRelayUrl, RelayAuthSnapshot>()).asStateFlow()
    }
}

object EmptyIAuthStatus : IAuthStatus {
    override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = true
}

class RelayAuthenticator(
    val client: INostrClient,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /**
     * Signs the auth template for every currently-logged-in account and returns the signed events.
     * The [relay] parameter allows callers to check per-relay auth policy before signing.
     * Returns an empty list to skip authentication for this relay.
     *
     * [interactive] is true for a fresh relay AUTH challenge (the signer MAY surface a user
     * prompt for an undecided relay) and false for an automatic re-auth triggered by an
     * `auth-required:` CLOSED (the signer must NOT prompt — it may only re-attach identities
     * that are already approved, such as ledger-ALLOW accounts and derived stream keys).
     */
    val signWithAllLoggedInUsers: suspend (relay: NormalizedRelayUrl, EventTemplate<RelayAuthEvent>, interactive: Boolean) -> List<RelayAuthEvent>,
) : IAuthStatus {
    // Connection callbacks fire on the per-relay OkHttp dispatcher thread, so
    // this state is mutated concurrently — LargeCache wraps a platform-tuned
    // concurrent map (ConcurrentSkipListMap on jvmAndroid, CacheMap on Apple).
    //
    // This stays mutable because RelayAuthStatus carries an LruCache that has
    // to be addressable from the dispatcher thread. The Compose-observable
    // view of the same data is published on [authStateFlow] below, sourced
    // from RelayAuthStatus.snapshot().
    private val authStatus = LargeCache<NormalizedRelayUrl, RelayAuthStatus>()

    private val _authStateFlow = MutableStateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>(persistentMapOf())

    /**
     * Per-relay AUTH state as an immutable Compose-stable snapshot map.
     *
     * Downstream consumers (UI banner, retry queue, indexer-fan-out gate)
     * subscribe to this flow instead of polling [authStatus] directly.
     * Identity changes on every mutation, so [kotlinx.coroutines.flow.distinctUntilChanged]
     * downstream and Compose `@Immutable` skipping both work correctly.
     */
    override val authStateFlow: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>> = _authStateFlow.asStateFlow()

    private fun publishSnapshot(relayUrl: NormalizedRelayUrl) {
        val status = authStatus.get(relayUrl)
        _authStateFlow.update { current ->
            if (status == null) current.removing(relayUrl) else current.putting(relayUrl, status.snapshot())
        }
    }

    private val clientListener =
        object : RelayConnectionListener {
            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                when (msg) {
                    is AuthMessage -> authenticate(relay, msg.challenge, interactive = true)
                    is OkMessage -> checkAuthResults(relay, msg)
                    is ClosedMessage -> reauthenticateIfAuthRequired(relay, msg)
                }
            }

            override fun onConnecting(relay: IRelayClient) {
                authStatus.put(relay.url, RelayAuthStatus())
                publishSnapshot(relay.url)
            }

            override fun onDisconnected(relay: IRelayClient) {
                authStatus.remove(relay.url)
                publishSnapshot(relay.url)
            }
        }

    private fun authenticate(
        relay: IRelayClient,
        challenge: String,
        interactive: Boolean,
    ) {
        val status = authStatus.get(relay.url)
        // Store the challenge so a later `auth-required:` CLOSED can reuse it (NIP-42).
        status?.rememberChallenge(challenge)
        // Open the SIGNING window here — synchronously, before the coroutine is launched
        // and before this callback returns. The `auth-required:` CLOSED that lands us here
        // was already delivered to subscription listeners (NostrClient dispatches those
        // ahead of connection listeners), so a fetch reacting to it looks at this state
        // right after we return. Marking inside the launch would leave a scheduling-width
        // hole in which the relay reads as "nobody is answering" and the fetch gives up on
        // an AUTH that was about to be signed.
        status?.signingStarted()
        publishSnapshot(relay.url)
        scope.launch {
            // Relay auth is automatic and not user-initiated. Signing can fail in
            // benign, expected ways — e.g. an external NIP-55 signer prompt that the
            // user ignores surfaces as SignerExceptions.TimedOutException. Those must
            // never escape this fire-and-forget launch: the host's scope may not carry
            // a CoroutineExceptionHandler (viewModelScope, rememberCoroutineScope, …),
            // so an uncaught throwable here crashes the whole app. Swallow + log them.
            try {
                val ev = RelayAuthEvent.build(relay.url, challenge)
                signWithAllLoggedInUsers(relay.url, ev, interactive).forEach { authEvent ->
                    // only send replies to new challenges to avoid infinite loop:
                    if (authStatus.get(relay.url)?.saveAuthSubmission(authEvent) == true) {
                        relay.sendIfConnected(AuthCmd(authEvent))
                        publishSnapshot(relay.url)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerExceptions) {
                Log.d("RelayAuthenticator") { "Could not sign auth for ${relay.url}: ${e.message}" }
            } catch (e: Exception) {
                Log.w("RelayAuthenticator", "Failed to authenticate with ${relay.url}", e)
            } finally {
                // Closes the SIGNING window on EVERY exit — an AUTH sent, an empty list
                // (the responder declining this relay), a signer timeout, a throw, or
                // cancellation. Anyone waiting on the outcome is unblocked by the publish;
                // leaving it open on a decline is what would turn "refused" back into the
                // indefinite silence this whole state exists to end.
                status?.signingFinished()
                publishSnapshot(relay.url)
            }
        }
    }

    /**
     * NIP-42: a relay sends the challenge only in an `AUTH` message, never in a `CLOSED`.
     * When a REQ is refused with an `auth-required:` CLOSED (e.g. a Concord channel-plane
     * REQ mounted after the control plane folded and revealed new stream keys), the relay
     * does NOT re-issue a challenge — the client is expected to reuse the one already stored
     * for the connection. Re-run the sign/send pass with that challenge: [saveAuthSubmission]
     * dedups by (pubkey, challenge), so only identities we haven't AUTHed on this challenge
     * yet (the folded-in keys) are actually sent — a no-op once they all are, so no loop.
     */
    private fun reauthenticateIfAuthRequired(
        relay: IRelayClient,
        msg: ClosedMessage,
    ) {
        if (MachineReadablePrefix.parse(msg.message) != MachineReadablePrefix.AUTH_REQUIRED) return
        val status = authStatus.get(relay.url) ?: return
        // Coalesce the burst: a relay refuses EVERY currently-open sub with its own `auth-required`
        // CLOSED, so a single missing identity yields many CLOSEDs at once. Re-signing on each would
        // re-hit an external (NIP-55) signer for every ledger-ALLOW account. Skip while an AUTH is
        // still in flight — the OK of the one we already sent runs [checkAuthResults] → syncFilters,
        // which re-drives the refused REQ; if it's still refused, that fresh CLOSED re-auths then.
        //
        // BOTH halves of "in flight" have to be checked. [hasFinishedAllAuths] only knows about AUTHs
        // already SENT, and a signature is not instantaneous: the relay challenges at connect and
        // refuses the first REQ before the signature comes back, so during that window the watcher is
        // empty, this guard used to pass, and a second signing pass started for the same challenge —
        // a second user-facing prompt on a NIP-55/NIP-46 signer, and two threads racing
        // [RelayAuthStatus.saveAuthSubmission]'s check-then-put. See
        // RelayAuthenticatorReauthOnClosedTest.aSlowSignatureIsNotSignedTwiceForOneChallenge.
        if (!status.hasFinishedAllAuths() || status.isSigning()) return
        val challenge = status.lastChallenge() ?: return
        authenticate(relay, challenge, interactive = false)
    }

    private fun checkAuthResults(
        relay: IRelayClient,
        msg: OkMessage,
    ) {
        val transitioned = authStatus.get(relay.url)?.checkAuthResults(msg.eventId, msg.success) == true
        // Publish even on failure transitions so the UI can clear "AUTHENTICATING"
        // banners and reflect AUTH_FAILED state.
        publishSnapshot(relay.url)
        // if this is the OK of an auth event, renew all subscriptions and resend all outgoing events.
        if (transitioned) {
            client.syncFilters(relay)
        }
    }

    override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = authStatus.get(relay)?.hasFinishedAllAuths() != false

    init {
        Log.d("RelayAuthenticator", "Init, Subscribe")
        client.addConnectionListener(clientListener)
        // Publishes "AUTH is being answered on this client" so accessories can decide
        // whether waiting out an `auth-required:` refusal is worth anything.
        client.addAuthResponder(this)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayAuthenticator", "Destroy, Unsubscribe")
        client.removeConnectionListener(clientListener)
        client.removeAuthResponder(this)
    }
}
