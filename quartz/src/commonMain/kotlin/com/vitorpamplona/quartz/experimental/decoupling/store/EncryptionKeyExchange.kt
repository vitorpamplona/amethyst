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
package com.vitorpamplona.quartz.experimental.decoupling.store

import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate

// The NIP-4E key lifecycle over an [EncryptionKeyStore]: the first device generates the key, every
// later device obtains it through a 4454 request answered by a 4455 transfer, and both ends keep it
// in their store. Publishing and subscribing stay with the caller; these only decide what to sign
// and what to keep.

/**
 * Creates a new random encryption key for [owner], keeps it, and returns its public key — the value
 * to announce with `EncryptionKeyListEvent.build(encryptionPubKey)`. Done once per identity, by the
 * first device (NIP-4E step 2); later devices request the existing key instead of generating one.
 */
suspend fun EncryptionKeyStore.generate(owner: HexKey): HexKey {
    val privKey = KeyPair().privKey ?: error("KeyPair() always generates a private key")
    return put(owner, privKey)
}

/**
 * Keeps the encryption key a 4455 [transfer] carries, after checking it is the one the user's
 * current [keyList] announces. Returns its public key, or null (and keeps nothing) when:
 * - the transfer or the key list is not signed by the same identity;
 * - the identity has no 10044 key to compare against;
 * - the content does not open with [requesterClientPrivKey] (this device's 4454 client key), or
 *   the key inside is not the announced one. NIP-4E: "undecryptable and non-matching responses are
 *   ignored."
 */
suspend fun EncryptionKeyStore.acceptTransfer(
    transfer: EncryptionKeyTransferEvent,
    requesterClientPrivKey: ByteArray,
    keyList: EncryptionKeyListEvent,
): HexKey? {
    if (transfer.pubKey != keyList.pubKey) return null
    val expected = keyList.encryptionKey()?.pubkey ?: return null
    val secret = transfer.decryptAndVerify(requesterClientPrivKey, expected) ?: return null
    return put(keyList.pubKey, secret.hexToByteArray())
}

/**
 * The 4455 answer to another device's 4454 [request], when this device holds the requested key:
 * the key named by the request's `n`, else the identity's current 10044 key. Null when the request
 * is not the identity's own, has no (or contradictory) client key, or the key is not in this store.
 *
 * Authorization is the caller's job and must come first: NIP-4E says a responder "SHOULD require
 * out-of-band authorization before disclosing the encryption private key", e.g. the user comparing
 * [EncryptionKeyRequestEvent.authorizationCode] on both devices. This function only checks shape.
 *
 * [senderClientPrivKey] is this device's own client key; the transfer is encrypted from it to the
 * requester's client key and signed by the identity when the caller signs the template.
 */
suspend fun EncryptionKeyStore.answerRequest(
    request: EncryptionKeyRequestEvent,
    keyList: EncryptionKeyListEvent,
    senderClientPrivKey: ByteArray,
): EventTemplate<EncryptionKeyTransferEvent>? {
    if (request.pubKey != keyList.pubKey) return null
    if (!request.hasConsistentClientKey()) return null
    val requesterClientKey = request.clientKey() ?: return null
    val requested = request.requestedEncryptionKey() ?: keyList.encryptionKey()?.pubkey ?: return null
    val secret = get(keyList.pubKey, requested) ?: return null
    return EncryptionKeyTransferEvent.build(
        encryptionPrivKey = secret.toHexKey(),
        senderClientPrivKey = senderClientPrivKey,
        requesterClientPubKey = requesterClientKey,
        requesterRelayHint = request.responseRelays().firstOrNull(),
        identityPubKey = keyList.pubKey,
    )
}
