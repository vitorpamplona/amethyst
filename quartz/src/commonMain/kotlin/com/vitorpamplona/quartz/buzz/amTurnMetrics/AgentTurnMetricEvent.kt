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
package com.vitorpamplona.quartz.buzz.amTurnMetrics

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Turn Metric (NIP-AM, `kind:44200`): a durable, encrypted record of one
 * AI-agent turn's token usage and cost, published by the agent to its owner.
 *
 * The `content` is a NIP-44 v2 ciphertext of an [AgentTurnMetricPayload], encrypted
 * between the agent (the event author, also named by the `agent` tag) and the owner
 * (the single `p` tag). Because the NIP-44 conversation key is symmetric, either party
 * can decrypt with [decrypt]. Ground truth: `buzz-core/src/agent_turn_metric.rs`.
 */
@Immutable
class AgentTurnMetricEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The owner (recipient) pubkey — the `p` tag. */
    fun ownerPubKey() = tags.turnMetricOwner()

    /** The publishing agent's pubkey — the `agent` tag (normally equal to [pubKey]). */
    fun agentPubKey() = tags.turnMetricAgent()

    /**
     * Decrypts and parses the metric payload. [signer] must belong to either the agent
     * (this event's author) or the owner. Throws on a missing counterparty, decryption
     * failure, or malformed payload; use [decryptOrNull] to swallow those.
     */
    suspend fun decrypt(signer: NostrSigner): AgentTurnMetricPayload {
        val counterparty = if (signer.pubKey == pubKey) ownerPubKey() else pubKey
        requireNotNull(counterparty) { "Turn metric is missing the owner (p) tag" }
        val json = signer.decrypt(content, counterparty)
        return AgentTurnMetricPayload.decodeFromJson(json)
    }

    suspend fun decryptOrNull(signer: NostrSigner): AgentTurnMetricPayload? =
        try {
            decrypt(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 44200

        /**
         * Builds and signs a turn-metric event: [signer] is the agent; the payload is
         * NIP-44-encrypted to [ownerPubKey]. Fails if the payload violates NIP-AM
         * numeric validity (a negative or non-finite `costUsd`).
         */
        suspend fun create(
            payload: AgentTurnMetricPayload,
            ownerPubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): AgentTurnMetricEvent {
            require(payload.isValid()) { "costUsd must be finite and non-negative" }
            val ciphertext = signer.nip44Encrypt(payload.encodeToJson(), ownerPubKey)
            return signer.sign(build(ciphertext, ownerPubKey, signer.pubKey, createdAt))
        }

        fun build(
            ciphertext: String,
            ownerPubKey: HexKey,
            agentPubKey: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AgentTurnMetricEvent>.() -> Unit = {},
        ) = eventTemplate<AgentTurnMetricEvent>(KIND, ciphertext, createdAt) {
            owner(ownerPubKey)
            agent(agentPubKey)
            initializer()
        }
    }
}
