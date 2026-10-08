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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An [EncryptionKeyStore] that lives as long as the process: for tests, the CLI's one-shot
 * commands and any caller that persists the keys elsewhere. Copies keys in and out so a caller
 * that wipes its array cannot corrupt the stored one.
 */
class InMemoryEncryptionKeyStore : EncryptionKeyStore {
    private val mutex = Mutex()
    private val keys = HashMap<Pair<HexKey, HexKey>, ByteArray>()

    override suspend fun get(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): ByteArray? = mutex.withLock { keys[owner.lowercase() to encryptionPubKey.lowercase()]?.copyOf() }

    override suspend fun put(
        owner: HexKey,
        encryptionPrivKey: ByteArray,
    ): HexKey {
        val pubKey = Nip01Crypto.pubKeyCreate(encryptionPrivKey).toHexKey()
        mutex.withLock { keys[owner.lowercase() to pubKey] = encryptionPrivKey.copyOf() }
        return pubKey
    }

    override suspend fun delete(
        owner: HexKey,
        encryptionPubKey: HexKey,
    ): Boolean = mutex.withLock { keys.remove(owner.lowercase() to encryptionPubKey.lowercase()) != null }
}
