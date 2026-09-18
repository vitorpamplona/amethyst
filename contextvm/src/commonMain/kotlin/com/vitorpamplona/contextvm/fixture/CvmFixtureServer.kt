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
package com.vitorpamplona.contextvm.fixture

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.contextvm.core.CvmMessageEvent
import com.vitorpamplona.contextvm.crypto.CvmGiftWrap
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.contextvm.mcp.McpParams
import com.vitorpamplona.contextvm.transport.CvmRelayPool
import com.vitorpamplona.contextvm.transport.CvmSubscription
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * An in-memory relay that delivers to whoever is subscribed at publish time.
 *
 * Its most important property is a faithful one: an event published while
 * nobody is subscribed is **dropped**, exactly as a real relay treats an
 * ephemeral kind. A test double that queued it would hide the subscribe-before-
 * publish bug this whole layer is designed to prevent.
 */
class InMemoryRelayPool : CvmRelayPool {
    private data class Listener(
        val pubKey: HexKey,
        val kinds: Set<Kind>,
        val onEvent: (Event) -> Unit,
    )

    private val listeners = mutableListOf<Listener>()

    /** Every event published, in order. For assertions about what went on the wire. */
    val published = mutableListOf<Event>()

    /** Events dropped because nothing was listening for them. */
    val dropped = mutableListOf<Event>()

    override fun subscribe(
        pubKey: HexKey,
        kinds: IntArray,
        onEvent: (Event) -> Unit,
    ): CvmSubscription {
        val listener = Listener(pubKey, kinds.toSet(), onEvent)
        listeners += listener
        return object : CvmSubscription {
            override fun close() {
                listeners -= listener
            }
        }
    }

    override suspend fun publish(event: Event) {
        published += event

        val recipients = event.tags.filter { it.size >= 2 && it[0] == "p" }.map { it[1] }
        val matched =
            listeners.filter { listener ->
                listener.kinds.contains(event.kind) && recipients.contains(listener.pubKey)
            }

        if (matched.isEmpty()) dropped += event
        matched.forEach { it.onEvent(event) }
    }
}

/**
 * How the fixture should misbehave.
 *
 * This is the reason the fixture exists. No real server sends a duplicate
 * `progress`, a stale `pong` nonce or a digest that does not match, yet a client
 * MUST handle all of them correctly — several are outright MUST-fail rules. The
 * only way to test that is a counterparty that can be told to break them.
 */
data class FixtureFaults(
    /** Answer with a JSON-RPC id that does not match the request. */
    val mismatchedResponseId: Boolean = false,
    /** Omit the `e` tag that correlates the response to the request event. */
    val omitCorrelationTag: Boolean = false,
    /** Answer a different request event id entirely. */
    val wrongCorrelationTag: Boolean = false,
    /** Send a response body that is not valid JSON-RPC. */
    val malformedResponse: Boolean = false,
    /** Never answer at all. */
    val silent: Boolean = false,
    /** Send this many junk notifications before the real response. */
    val noisePrefix: Int = 0,
)

/**
 * A ContextVM server that plays the peer role in tests (Tier C).
 *
 * Not hardened for deployment and deliberately so: for a real coordinator,
 * `cordn-rs` already exists. This exists to be wrong on demand.
 */
class CvmFixtureServer(
    private val relays: InMemoryRelayPool,
    private val signer: NostrSigner,
    private val crypto: CvmGiftWrap = CvmGiftWrap(),
    private val faults: FixtureFaults = FixtureFaults(),
    /** CEP-16: inject the caller's pubkey into `_meta` before handling. */
    private val injectClientPubkey: Boolean = false,
    /** Discovery tags sent on the first direct message back, per CEP-35. */
    private val discoveryTags: List<Tag> = emptyList(),
    /** Answers a request, given its params (with `_meta.clientPubkey` if injected). */
    private val handler: suspend (method: String, params: JsonObject?) -> JsonRpcMessage,
) {
    private var subscription: CvmSubscription? = null
    private var sentFirstMessage = false

    /** Params as the handler saw them, for asserting CEP-16 injection. */
    val handledParams = mutableListOf<JsonObject?>()

    /** Starts listening. Call before the client publishes anything. */
    fun start(): CvmSubscription {
        val sub =
            relays.subscribe(
                pubKey = signer.pubKey,
                kinds = intArrayOf(CvmKinds.MESSAGE) + CvmKinds.GIFT_WRAPS,
                onEvent = { event -> pending += event },
            )
        subscription = sub
        return sub
    }

    /** Events received but not yet answered. Drained by [pump]. */
    private val pending = mutableListOf<Event>()

    /**
     * Handles everything received so far.
     *
     * Explicit rather than automatic because the relay callback cannot suspend,
     * and because a test usually wants to control when the answer appears.
     */
    suspend fun pump() {
        val batch = pending.toList()
        pending.clear()
        batch.forEach { handle(it) }
    }

    private suspend fun handle(event: Event) {
        val plain =
            if (CvmKinds.isGiftWrap(event.kind)) {
                try {
                    crypto.unwrap(event, signer)
                } catch (e: IllegalStateException) {
                    return
                }
            } else {
                event
            }

        val message = CvmMessageEvent.fromOrNull(plain) ?: return
        val request = message.message() as? JsonRpcRequest ?: return

        if (faults.silent) return

        val clientPubKey = plain.pubKey
        val params = if (injectClientPubkey) inject(request.params, clientPubKey) else request.params
        handledParams += params

        repeat(faults.noisePrefix) { index ->
            reply(
                JsonRpcNotification("notifications/message", buildJsonObject { put("seq", JsonPrimitive(index)) }),
                clientPubKey,
                plain.id,
            )
        }

        val response = handler(request.method, params)
        reply(response, clientPubKey, plain.id)
    }

    /** Sends [message] back to [clientPubKey], answering [requestEventId]. */
    suspend fun reply(
        message: JsonRpcMessage,
        clientPubKey: HexKey,
        requestEventId: HexKey,
    ) {
        val correlation =
            when {
                faults.omitCorrelationTag -> null
                faults.wrongCorrelationTag -> "f".repeat(64)
                else -> requestEventId
            }

        val tags = if (sentFirstMessage) emptyList() else discoveryTags
        sentFirstMessage = true

        val content =
            if (faults.malformedResponse) {
                MALFORMED
            } else {
                com.vitorpamplona.contextvm.jsonrpc.JsonRpcCodec
                    .encode(faultInjected(message))
            }

        val inner =
            signer.sign<Event>(
                createdAt =
                    com.vitorpamplona.quartz.utils.TimeUtils
                        .now(),
                kind = CvmKinds.MESSAGE,
                tags =
                    buildList {
                        add(arrayOf("p", clientPubKey))
                        correlation?.let { add(arrayOf("e", it)) }
                        addAll(tags)
                    }.toTypedArray(),
                content = content,
            )

        relays.publish(
            if (crypto.shouldEncrypt(peerSupportsEncryption = true)) {
                crypto.wrap(inner, clientPubKey)
            } else {
                inner
            },
        )
    }

    private fun faultInjected(message: JsonRpcMessage): JsonRpcMessage =
        if (faults.mismatchedResponseId && message is com.vitorpamplona.contextvm.jsonrpc.JsonRpcSuccess) {
            message.copy(
                id =
                    com.vitorpamplona.contextvm.jsonrpc.JsonRpcId
                        .Num(999_999),
            )
        } else {
            message
        }

    private fun inject(
        params: JsonObject?,
        clientPubKey: HexKey,
    ): JsonObject =
        buildJsonObject {
            params?.forEach { (key, value) -> if (key != McpParams.META) put(key, value) }
            put(
                McpParams.META,
                buildJsonObject {
                    (params?.get(McpParams.META) as? JsonObject)?.forEach { (key, value) -> put(key, value) }
                    // The client never supplies this: a client-supplied value
                    // would be a spoof, which is exactly why CEP-16 has the
                    // server derive it from the event signature.
                    put(McpParams.CLIENT_PUBKEY, JsonPrimitive(clientPubKey))
                },
            )
        }

    fun stop() {
        subscription?.close()
        subscription = null
    }

    private companion object {
        const val MALFORMED = """{"not":"json-rpc"}"""
    }
}
