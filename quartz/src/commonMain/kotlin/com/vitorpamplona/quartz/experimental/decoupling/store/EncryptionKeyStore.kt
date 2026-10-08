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

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Where a device keeps the NIP-4E encryption **private** keys it holds, per identity.
 *
 * NIP-4E (draft, nips#1647) decouples encryption from identity: a user's kind 10044 announces a
 * random encryption public key, and only the devices that created it or received it through a
 * 4454/4455 transfer know its secret. Nothing can re-derive that secret, so losing it from this
 * store means asking another device for it again. Implementations must therefore persist it with
 * the same care as an account's private key (commons has one over `SecureKeyStorage`);
 * [InMemoryEncryptionKeyStore] is for tests and one-shot tools.
 *
 * Keys are scoped by [owner], the identity (account) pubkey, so one device can hold the keys of
 * several accounts, and addressed by their own public key, which is what a 10044 `n` names.
 */
interface EncryptionKeyStore {
    /** The private key of [encryptionPubKey] held for [owner], or null when this device does not have it. */
    suspend fun get(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): ByteArray?

    /**
     * Stores [encryptionPrivKey] for [owner] under its own public key, which it derives rather
     * than trusting a caller-supplied one, and returns that public key.
     */
    suspend fun put(
        owner: HexKey,
        encryptionPrivKey: ByteArray,
    ): HexKey

    /** Forgets the key; true when it was there. */
    suspend fun delete(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): Boolean
}
