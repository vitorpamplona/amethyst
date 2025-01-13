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
package com.vitorpamplona.quartz.nip01Core.signers

import com.vitorpamplona.quartz.nip01Core.EventFactory
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip04Dm.Nip04
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

abstract class NostrSigner(
    val pubKey: HexKey,
) {
    abstract fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    )

    abstract fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    )

    abstract fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    )

    abstract fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    )

    abstract fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    )

    abstract fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    )

    fun decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (Nip04.isNIP04(encryptedContent)) {
            nip04Decrypt(encryptedContent, fromPublicKey, onReady)
        } else {
            nip44Decrypt(encryptedContent, fromPublicKey, onReady)
        }
    }

    fun <T : Event> assembleRumor(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        val id = Event.generateId(pubKey, createdAt, kind, tags, content)

        onReady(
            EventFactory.create(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            ) as T,
        )
    }
}