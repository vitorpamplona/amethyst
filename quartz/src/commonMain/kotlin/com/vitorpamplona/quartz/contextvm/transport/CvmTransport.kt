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
package com.vitorpamplona.quartz.contextvm.transport

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.cep35Discovery.SessionDiscovery
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.contextvm.core.CvmMessageEvent
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFailure
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/**
 * The two identities a ContextVM client uses.
 *
 * Splitting them is a privacy measure, not plumbing: the stable identity signs
 * only what must be attributable, while everything else rides a throwaway key so
 * a server cannot link a session's activity to an account. Making the choice an
 * explicit parameter means a caller cannot leak the stable one by omission.
 */
class DualSigner(
    /** The account identity. Used only where a call must be attributable. */
    val stable: NostrSigner,
    /** A per-session throwaway. Used for everything else. */
    val ephemeral: NostrSigner,
) {
    enum class Identity {
        STABLE,
        EPHEMERAL,
    }

    fun signerFor(identity: Identity) =
        when (identity) {
            Identity.STABLE -> stable
            Identity.EPHEMERAL -> ephemeral
        }
}

/** Thrown when a request cannot be completed at the transport layer. */
class CvmTransportException(
    message: String,
) : IllegalStateException(message)

/**
 * Correlates ContextVM requests with their responses over a [CvmRelayPool].
 *
 * [request] subscribes before it publishes, always. Kind 25910 is ephemeral, so
 * a subscription opened afterwards has missed the response permanently and the
 * failure looks exactly like a flaky relay. Making the ordering the transport's
 * job rather than the caller's removes the whole class of bug, which is why
 * there is no public publish/subscribe pair to get wrong.
 *
 * Inbound notifications that are not responses — CEP-22/41 frames, CEP-8 payment
 * notifications — are handed to `onNotification` while the request is still in
 * flight. A notification never resolves a request: CEP-41 is explicit that a
 * stream's `close` does not complete it.
 */
class CvmTransport(
    private val relays: CvmRelayPool,
    private val signers: DualSigner,
    private val serverPubKey: HexKey,
    private val crypto: CvmGiftWrap = CvmGiftWrap(),
    private val discovery: SessionDiscovery = SessionDiscovery(),
    /**
     * What to assume about the peer **until it declares otherwise**.
     *
     * Not a fixed answer: once the peer sends a CEP-35 discovery surface, that
     * surface wins (see [peerEncrypts]). These are only what to believe before
     * the first message arrives, and for a peer that never declares anything.
     *
     * The default is optimistic on purpose. Assuming a peer cannot encrypt
     * would have us send the first request of every session in the clear, which
     * is the one message whose exposure we can still avoid.
     */
    private val assumePeerSupportsEncryption: Boolean = true,
    private val assumePeerSupportsEphemeralWrap: Boolean = true,
) {
    /** The peer's learned discovery baseline, once its first message has arrived. */
    val peer get() = discovery.peer

    /**
     * Whether to encrypt to this peer, by what it has actually told us.
     *
     * A declared surface wins; silence leaves the assumption in place. That
     * split is what makes [com.vitorpamplona.quartz.contextvm.cep04Encryption.EncryptionMode.REQUIRED]
     * mean something: before this, its input was a constant, so its promise to
     * fail loudly rather than downgrade could never fire. Now a peer that
     * declares a surface without `support_encryption` gets a stated refusal
     * instead of a wrap it cannot open and a request that times out with no
     * reason.
     */
    private fun peerEncrypts() = discovery.declaredPeer?.supportsEncryption ?: assumePeerSupportsEncryption

    /** As [peerEncrypts], for CEP-19's ephemeral wrap kind. */
    private fun peerTakesEphemeralWrap() = discovery.declaredPeer?.supportsEphemeralEncryption ?: assumePeerSupportsEphemeralWrap

    private var sentFirstMessage = false

    /**
     * Sends [message] and waits for the correlated response.
     *
     * @param identity which key signs the request. Anything not required to be
     *   attributable should stay [DualSigner.Identity.EPHEMERAL].
     * @param discoveryTags this side's CEP-35 baseline, sent on the session's
     *   first direct message only.
     */
    suspend fun request(
        message: JsonRpcRequest,
        identity: DualSigner.Identity = DualSigner.Identity.EPHEMERAL,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        discoveryTags: List<Tag> = emptyList(),
        onNotification: (JsonRpcNotification) -> Unit = {},
    ): JsonRpcMessage {
        val signer = signers.signerFor(identity)
        val inbound = Channel<Event>(Channel.UNLIMITED)

        // Subscribe first, before anything is published.
        val subscription =
            relays.subscribe(
                pubKey = signer.pubKey,
                // Both wrap kinds plus the bare message: CEP-19's fallback means
                // either wrap may arrive, and an unencrypted peer sends 25910.
                kinds = intArrayOf(CvmKinds.MESSAGE) + CvmKinds.GIFT_WRAPS,
                onEvent = { inbound.trySend(it) },
            )

        try {
            // CEP-35: the baseline rides the first direct message only; after
            // that both sides omit repeated common discovery tags.
            val tags = if (sentFirstMessage) emptyList() else discoveryTags
            val request = CvmMessageEvent.create(message, serverPubKey, signer, extraTags = tags)
            sentFirstMessage = true

            relays.publish(outbound(request))

            return withTimeout(timeoutMs) {
                awaitResponse(inbound, signer, request.id, message.id, onNotification)
            }
        } finally {
            subscription.close()
            inbound.close()
        }
    }

    /** Sends a notification. Nothing is awaited, so no subscription is opened. */
    suspend fun notify(
        message: JsonRpcNotification,
        identity: DualSigner.Identity = DualSigner.Identity.EPHEMERAL,
    ) {
        val signer = signers.signerFor(identity)
        relays.publish(outbound(CvmMessageEvent.create(message, serverPubKey, signer)))
    }

    private suspend fun awaitResponse(
        inbound: Channel<Event>,
        signer: NostrSigner,
        requestEventId: HexKey,
        requestId: JsonRpcId,
        onNotification: (JsonRpcNotification) -> Unit,
    ): JsonRpcMessage {
        for (event in inbound) {
            val plain = decryptOrNull(event, signer) ?: continue
            val wrapped = CvmMessageEvent.fromOrNull(plain) ?: continue

            discovery.observe(wrapped.discoveryTags().toTypedArray())

            val decoded =
                try {
                    wrapped.message()
                } catch (e: IllegalArgumentException) {
                    // A malformed payload from the peer is not our request's
                    // answer; keep waiting rather than failing the call on it.
                    continue
                }

            when (decoded) {
                is JsonRpcNotification -> onNotification(decoded)

                // Correlate on both layers: the `e` tag ties the response to our
                // request event, and the JSON-RPC id ties it to our call. Either
                // alone is weaker -- a peer may omit the tag on a wrap, and ids
                // are only unique within a session.
                is JsonRpcSuccess ->
                    if (matches(wrapped, requestEventId, decoded.id, requestId)) return decoded

                is JsonRpcFailure ->
                    if (decoded.id == null || matches(wrapped, requestEventId, decoded.id, requestId)) return decoded

                else -> Unit
            }
        }
        throw CvmTransportException("subscription closed before a response arrived")
    }

    /**
     * Whether [message] is the answer to our call — from the peer we called.
     *
     * The sender check is the load-bearing one and it is checked first. This
     * client subscribes to everything `p`-tagged to its own key, so **anyone**
     * on the relay can gift-wrap a well-formed JSON-RPC response to us; the
     * wrap's own signature proves only that its throwaway key signed it, and
     * `CvmGiftWrap.unwrap` verifies the INNER signature without knowing who
     * the inner signer ought to be. Without this, correlation rests on an `e`
     * tag a forger simply omits (the check below skips a missing one) and a
     * JSON-RPC id that is small and guessable — so a stranger could answer
     * `kp_take` with their own KeyPackage, or `msg_fetch_many` with a stream
     * of their choosing.
     *
     * `serverPubKey` is the coordinator's identity and the only thing that
     * identifies it (`spec/00.md` §8.5), so it is exactly the right thing to
     * compare against.
     */
    private fun matches(
        message: CvmMessageEvent,
        requestEventId: HexKey,
        responseId: JsonRpcId,
        requestId: JsonRpcId,
    ): Boolean {
        if (message.pubKey != serverPubKey) return false
        val inReplyTo = message.inReplyTo()
        if (inReplyTo != null && inReplyTo != requestEventId) return false
        return responseId == requestId
    }

    private suspend fun decryptOrNull(
        event: Event,
        signer: NostrSigner,
    ): Event? =
        if (CvmKinds.isGiftWrap(event.kind)) {
            try {
                crypto.unwrap(event, signer)
            } catch (e: IllegalStateException) {
                // Not addressed to us, or forged. Ignoring is correct: a relay
                // may deliver wraps we cannot open, and failing the request on
                // one would let anyone disrupt a call.
                null
            }
        } else {
            event
        }

    private suspend fun outbound(inner: Event): Event =
        if (crypto.shouldEncrypt(peerEncrypts())) {
            crypto.wrap(inner, serverPubKey, crypto.negotiatedWrapKind(peerTakesEphemeralWrap()))
        } else {
            inner
        }

    companion object {
        /** Covers signing, the relay round trip and the peer's own work. */
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
