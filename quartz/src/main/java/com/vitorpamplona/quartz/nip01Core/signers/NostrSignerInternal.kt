/**
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
package com.vitorpamplona.quartz.nip01Core.signers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import kotlinx.coroutines.CancellationException

class NostrSignerInternal(
    val keyPair: KeyPair,
) : NostrSigner(keyPair.pubKey.toHexKey()) {
    val signerSync = NostrSignerSync(keyPair)

    override fun isWriteable(): Boolean = keyPair.privKey != null

    inline fun <T> runWrapErrors(action: () -> T): T =
        try {
            action()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is SignerExceptions) throw e
            throw SignerExceptions.CouldNotPerformException("Could not perform the operation", e)
        }

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T =
        runWrapErrors {
            signerSync.sign<T>(createdAt, kind, tags, content)
        }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String =
        runWrapErrors {
            signerSync.nip04Encrypt(plaintext, toPublicKey)
        }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String =
        runWrapErrors {
            signerSync.nip04Decrypt(ciphertext, fromPublicKey)
        }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String =
        runWrapErrors {
            signerSync.nip44Encrypt(plaintext, toPublicKey)
        }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String =
        runWrapErrors {
            signerSync.nip44Decrypt(ciphertext, fromPublicKey)
        }

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent {
        if (!event.isPrivateZap()) throw SignerExceptions.NothingToDecrypt()

        return runWrapErrors {
            signerSync.decryptZapEvent(event)
        }
    }

    override suspend fun deriveKey(nonce: HexKey): HexKey =
        runWrapErrors {
            signerSync.deriveKey(nonce)
        }
}
