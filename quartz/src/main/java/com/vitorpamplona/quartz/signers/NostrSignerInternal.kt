/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.signers

import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent

class NostrSignerInternal(
    val keyPair: KeyPair,
) : NostrSigner(keyPair.pubKey.toHexKey()) {
    val signerSync = NostrSignerSync(keyPair)

    override fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        signerSync.sign<T>(createdAt, kind, tags, content)?.let { onReady(it) }
    }

    override fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        signerSync.nip04Encrypt(decryptedContent, toPublicKey)?.let { onReady(it) }
    }

    override fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        signerSync.nip04Decrypt(encryptedContent, fromPublicKey)?.let { onReady(it) }
    }

    override fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        signerSync.nip44Encrypt(decryptedContent, toPublicKey)?.let { onReady(it) }
    }

    override fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        signerSync.nip44Decrypt(encryptedContent, fromPublicKey)?.let { onReady(it) }
    }

    override fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    ) {
        signerSync.decryptZapEvent(event)?.let { onReady(it) }
    }
}
