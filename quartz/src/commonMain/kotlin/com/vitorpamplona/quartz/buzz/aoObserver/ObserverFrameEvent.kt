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
package com.vitorpamplona.quartz.buzz.aoObserver

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.aoObserver.tags.FrameTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz Agent Observer Frame (NIP-AO, `kind:24200`): a transient, owner-scoped,
 * encrypted agent telemetry or control message. Ephemeral — relays route it on
 * its cleartext tags and never store it.
 *
 * Tags are all cleartext so a relay can route without reading ACP internals:
 * - `p` — the recipient ([recipientPubKey]); the owner for telemetry, the agent for control.
 * - `agent` — the agent whose observer stream this frame belongs to ([agentPubKey]).
 * - `frame` — the direction ([frame]): `telemetry` (agent-to-owner) or `control` (owner-to-agent).
 *
 * The `content` is a NIP-44 v2 ciphertext between the two parties. Because the
 * conversation key is symmetric either party can [decryptTelemetry] /
 * [decryptControl]. Ground truth: `buzz-core/src/observer.rs`,
 * `buzz-sdk/src/builders.rs` (`build_agent_observer_frame`), and the relay
 * routing in `buzz-relay/src/handlers/event.rs` (`agent_observer_route`).
 */
@Immutable
class ObserverFrameEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    override fun isContentEncoded() = true

    /** The recipient pubkey — the `p` tag (owner for telemetry, agent for control). */
    fun recipientPubKey() = tags.observerRecipient()

    /** The agent this observer stream belongs to — the `agent` tag. */
    fun agentPubKey() = tags.observerAgent()

    /** The cleartext frame direction — the `frame` tag (`telemetry` or `control`). */
    fun frame() = tags.observerFrame()

    /**
     * Resolves the NIP-44 counterparty for [signer]: if the signer authored the
     * event, it is the `p` recipient; otherwise it is the author. Either party
     * to the frame holds the same symmetric conversation key.
     */
    private fun counterparty(signer: NostrSigner): HexKey? = if (signer.pubKey == pubKey) recipientPubKey() else pubKey

    /**
     * Decrypts and parses a `frame:telemetry` body. [signer] must belong to the
     * agent (author) or the owner (`p` recipient). Throws on a missing
     * counterparty, decryption failure, or malformed payload.
     */
    suspend fun decryptTelemetry(signer: NostrSigner): ObserverTelemetryPayload {
        val counterparty = counterparty(signer)
        requireNotNull(counterparty) { "Observer frame is missing the recipient (p) tag" }
        val json = signer.decrypt(content, counterparty)
        return ObserverTelemetryPayload.decodeFromJson(json)
    }

    suspend fun decryptTelemetryOrNull(signer: NostrSigner): ObserverTelemetryPayload? =
        try {
            decryptTelemetry(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    /**
     * Decrypts and parses a `frame:control` body. [signer] must belong to the
     * owner (author) or the agent (`p` recipient). Throws on a missing
     * counterparty, decryption failure, or malformed payload.
     */
    suspend fun decryptControl(signer: NostrSigner): ObserverControlPayload {
        val counterparty = counterparty(signer)
        requireNotNull(counterparty) { "Observer frame is missing the recipient (p) tag" }
        val json = signer.decrypt(content, counterparty)
        return ObserverControlPayload.decodeFromJson(json)
    }

    suspend fun decryptControlOrNull(signer: NostrSigner): ObserverControlPayload? =
        try {
            decryptControl(signer)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    companion object {
        const val KIND = 24200

        /**
         * Builds and signs an agent-to-owner telemetry frame: [signer] is the
         * agent, the payload is NIP-44-encrypted to [ownerPubKey], the `p` tag is
         * the owner, and the `frame` tag is [FrameTag.TELEMETRY].
         */
        suspend fun createTelemetry(
            payload: ObserverTelemetryPayload,
            ownerPubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ObserverFrameEvent {
            val ciphertext = signer.nip44Encrypt(payload.encodeToJson(), ownerPubKey)
            return signer.sign(build(ciphertext, ownerPubKey, signer.pubKey, FrameTag.TELEMETRY, createdAt))
        }

        /**
         * Builds and signs an owner-to-agent control frame: [signer] is the owner,
         * the payload is NIP-44-encrypted to [agentPubKey], the `p` tag is the
         * agent, and the `frame` tag is [FrameTag.CONTROL].
         */
        suspend fun createControl(
            payload: ObserverControlPayload,
            agentPubKey: HexKey,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): ObserverFrameEvent {
            val ciphertext = signer.nip44Encrypt(payload.encodeToJson(), agentPubKey)
            return signer.sign(build(ciphertext, agentPubKey, agentPubKey, FrameTag.CONTROL, createdAt))
        }

        fun build(
            ciphertext: String,
            recipientPubKey: HexKey,
            agentPubKey: HexKey,
            frame: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ObserverFrameEvent>.() -> Unit = {},
        ) = eventTemplate<ObserverFrameEvent>(KIND, ciphertext, createdAt) {
            recipient(recipientPubKey)
            agent(agentPubKey)
            frame(frame)
            initializer()
        }
    }
}
