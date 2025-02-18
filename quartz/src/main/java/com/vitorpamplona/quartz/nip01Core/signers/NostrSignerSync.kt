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

import android.util.Log
import com.vitorpamplona.quartz.EventFactory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder

class NostrSignerSync(
    val keyPair: KeyPair,
    val pubKey: HexKey = keyPair.pubKey.toHexKey(),
) {
    fun <T : Event> sign(ev: EventTemplate<T>) = signNormal<T>(ev.createdAt, ev.kind, ev.tags, ev.content)

    fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T? {
        if (keyPair.privKey == null) return null

        return if (isUnsignedPrivateZapEvent(kind, tags)) {
            // this is a private zap
            PrivateZapRequestBuilder().signPrivateZapRequest(createdAt, kind, tags, content, this)
        } else {
            signNormal(createdAt, kind, tags, content)
        }
    }

    fun isUnsignedPrivateZapEvent(
        kind: Int,
        tags: Array<Array<String>>,
    ): Boolean =
        kind == LnZapRequestEvent.KIND &&
            tags.any { t -> t.size > 1 && t[0] == "anon" && t[1].isBlank() }

    fun <T : Event> signNormal(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T? {
        if (keyPair.privKey == null) return null

        val id = EventHasher.hashIdBytes(pubKey, createdAt, kind, tags, content)
        val sig = Nip01.sign(id, keyPair.privKey).toHexKey()

        return EventFactory.create(
            id.toHexKey(),
            pubKey,
            createdAt,
            kind,
            tags,
            content,
            sig,
        ) as T
    }

    fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return Nip04.encrypt(
            decryptedContent,
            keyPair.privKey,
            toPublicKey.hexToByteArray(),
        )
    }

    fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return try {
            Nip04.decrypt(encryptedContent, keyPair.privKey, fromPublicKey.hexToByteArray())
        } catch (e: Exception) {
            Log.w("NIP04Decrypt", "Error decrypting the message ${e.message} on $encryptedContent")
            null
        }
    }

    fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return Nip44
            .encrypt(
                decryptedContent,
                keyPair.privKey,
                toPublicKey.hexToByteArray(),
            ).encodePayload()
    }

    fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
    ): String? {
        if (keyPair.privKey == null) return null

        return Nip44.decrypt(
            payload = encryptedContent,
            privateKey = keyPair.privKey,
            pubKey = fromPublicKey.hexToByteArray(),
        )
    }

    fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent? = PrivateZapRequestBuilder().decryptZapEvent(event, this)
}
