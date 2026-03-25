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
package com.vitorpamplona.quartz.nip89AppHandlers.clientTag

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

/**
 * A [NostrSigner] decorator that automatically appends a NIP-89 client tag
 * to every event before delegating signing to the wrapped [inner] signer.
 *
 * This ensures all events produced by the application carry the client
 * identification tag without requiring each call site to add it manually.
 *
 * Works with any signer implementation: internal, external (NIP-55/Amber),
 * or remote (NIP-46/Bunker).
 */
class NostrSignerWithClientTag(
    val inner: NostrSigner,
    val clientTag: Array<String>,
) : NostrSigner(inner.pubKey) {
    constructor(
        inner: NostrSigner,
        clientName: String,
    ) : this(inner, ClientTag.assemble(clientName))

    constructor(
        inner: NostrSigner,
        clientName: String,
        addressId: String?,
        relayHint: NormalizedRelayUrl?,
    ) : this(inner, ClientTag.assemble(clientName, addressId, relayHint))

    override fun isWriteable(): Boolean = inner.isWriteable()

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T = inner.sign(createdAt, kind, appendClientTag(tags), content)

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = inner.nip04Encrypt(plaintext, toPublicKey)

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = inner.nip04Decrypt(ciphertext, fromPublicKey)

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = inner.nip44Encrypt(plaintext, toPublicKey)

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = inner.nip44Decrypt(ciphertext, fromPublicKey)

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = inner.decryptZapEvent(event)

    override suspend fun deriveKey(nonce: HexKey): HexKey = inner.deriveKey(nonce)

    override fun hasForegroundSupport(): Boolean = inner.hasForegroundSupport()

    private fun appendClientTag(tags: Array<Array<String>>): Array<Array<String>> {
        // Don't add if a client tag already exists
        if (tags.any { it.size >= 2 && it[0] == ClientTag.TAG_NAME }) return tags

        return tags + arrayOf(clientTag)
    }
}
