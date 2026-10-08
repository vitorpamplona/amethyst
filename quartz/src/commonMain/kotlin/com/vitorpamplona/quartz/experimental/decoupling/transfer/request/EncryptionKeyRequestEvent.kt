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
package com.vitorpamplona.quartz.experimental.decoupling.transfer.request

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.client
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-4E (draft, nips#1647) encryption-key **request**, kind 4454.
 *
 * Signed by the user's identity key on a device that does not hold the shared encryption key yet.
 * It announces the device's freshly generated client key in `P` and asks any device that knows the
 * key behind the user's [EncryptionKeyListEvent] (kind 10044) to send it over, NIP-44 encrypted to
 * that client key, in an [EncryptionKeyTransferEvent] (kind 4455). Once the key arrives the
 * requester deletes the request.
 *
 * Tags, following the PsstPsst profile of NIP-4E:
 * - `P`: the requester's client key ([clientKey]); `pubkey` is a legacy alias read as a fallback.
 * - `relay` (repeated): relays the requester listens on for the response ([responseRelays]).
 * - `n` (optional): which encryption public key is wanted; absent means "the current one".
 * - `client` (optional, NIP-89): a device label such as "Flotilla on Android", shown when asking
 *   the user to approve the transfer.
 *
 * Carries no graph edges: the client key and the requested key are key material for the exchange,
 * not references to users or events, and the relays are delivery hints. Nothing is searchable.
 */
@Immutable
class EncryptionKeyRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The client key the encryption key must be encrypted to, or null when the request carries none. */
    fun clientKey() = tags.requesterClientKey()

    /**
     * False when the request carries both `P` and the legacy `pubkey` alias and they differ, which the
     * PsstPsst profile forbids. A responder should ignore such a request rather than guess.
     */
    fun hasConsistentClientKey(): Boolean {
        val alias = tags.requesterClientKeyAlias() ?: return true
        return alias == clientKey()
    }

    fun responseRelays() = tags.responseRelays()

    /** The encryption public key asked for, or null for "whatever the current key is". */
    fun requestedEncryptionKey() = tags.requestedEncryptionKey()

    /** The device label from the NIP-89 `client` tag, if the requester sent one. */
    fun clientName() = tags.client().firstOrNull()?.name

    /**
     * The out-of-band comparison code the PsstPsst profile defines: the first eight hex characters of
     * the requester's client key, uppercased and split as `XXXX XXXX`. A responder SHOULD have the user
     * confirm it matches what the requesting device shows before disclosing the key.
     */
    fun authorizationCode(): String? = clientKey()?.let(::authorizationCodeFor)

    companion object {
        const val KIND = 4454
        const val ALT_DESCRIPTION = "Encryption key request"

        fun authorizationCodeFor(clientPubKey: HexKey): String {
            val prefix = clientPubKey.take(8).uppercase()
            return prefix.take(4) + " " + prefix.drop(4)
        }

        /**
         * A request for the encryption key, to be encrypted to [clientPubKey]. The legacy `pubkey`
         * alias is written next to `P` by default because clients that predate the rename still read
         * only that one.
         */
        fun build(
            clientPubKey: HexKey,
            responseRelays: List<NormalizedRelayUrl> = emptyList(),
            requestedEncryptionKey: HexKey? = null,
            withPubKeyAlias: Boolean = true,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EncryptionKeyRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            clientKey(clientPubKey)
            if (withPubKeyAlias) clientKeyAlias(clientPubKey)
            if (responseRelays.isNotEmpty()) responseRelays(responseRelays)
            requestedEncryptionKey?.let { requestedEncryptionKey(it) }
            initializer()
        }
    }
}
