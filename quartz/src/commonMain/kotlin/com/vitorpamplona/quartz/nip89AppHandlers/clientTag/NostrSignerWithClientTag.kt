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
    val disabled: () -> Boolean = { false },
) : NostrSigner(inner.pubKey) {
    constructor(
        inner: NostrSigner,
        clientName: String,
    ) : this(inner, ClientTag.assemble(clientName))

    constructor(
        inner: NostrSigner,
        clientName: String,
        disabled: () -> Boolean,
    ) : this(inner, ClientTag.assemble(clientName), disabled)

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
    ): T =
        if (disabled()) {
            inner.sign(createdAt, kind, tags, content)
        } else {
            inner.sign(createdAt, kind, appendClientTag(tags), content)
        }

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

    override suspend fun signPsbt(psbtHex: String): String = inner.signPsbt(psbtHex)

    override fun hasForegroundSupport(): Boolean = inner.hasForegroundSupport()

    /**
     * The exact tag set [sign] will forward to the inner signer. Callers that
     * transform the template before signing (e.g. NIP-13 mining, which commits
     * the tags into the hashed id) must mine over this final shape, otherwise
     * the client tag appended at sign time would invalidate the nonce.
     */
    fun prepareTags(tags: Array<Array<String>>): Array<Array<String>> = if (disabled()) tags else appendClientTag(tags)

    private fun appendClientTag(tags: Array<Array<String>>): Array<Array<String>> {
        // Don't add if a client tag already exists
        if (tags.any { it.size >= 2 && it[0] == ClientTag.TAG_NAME }) return tags

        return tags + arrayOf(clientTag)
    }
}

/**
 * The same signer with the NIP-89 client-tag decorator peeled off, or the receiver unchanged when it
 * carries no such decorator.
 *
 * The client tag says "this app composed this event", so it belongs only on templates this app
 * authored. When we sign on someone else's behalf — a napplet, an nSite, a web app over NIP-07, a
 * client using us as a NIP-46 bunker — the template is theirs, and appending a tag rewrites the very
 * bytes they are about to have hashed into an id. NIP-07 callers routinely re-check the returned
 * event against the template they submitted (block/buzz compares `JSON.stringify(tags)` outright)
 * and reject the result as an invalid signature when it does not match.
 *
 * Any decoration under this one (metering, NIP-13 mining) is preserved, as is [NostrSigner.pubKey].
 */
fun NostrSigner.withoutClientTag(): NostrSigner = if (this is NostrSignerWithClientTag) inner else this
