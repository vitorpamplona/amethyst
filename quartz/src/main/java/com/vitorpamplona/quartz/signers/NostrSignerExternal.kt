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

import android.util.Log
import com.goterl.lazysodium.BuildConfig
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent

class NostrSignerExternal(
    pubKey: HexKey,
    val launcher: ExternalSignerLauncher,
) : NostrSigner(pubKey) {
    override fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        val id = Event.generateId(pubKey, createdAt, kind, tags, content).toHexKey()

        val event =
            Event(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )

        launcher.openSigner(event) { signature ->
            if (signature.startsWith("{")) {
                val localEvent = Event.fromJson(signature)
                (
                    EventFactory.create(
                        localEvent.id,
                        localEvent.pubKey,
                        localEvent.createdAt,
                        localEvent.kind,
                        localEvent.tags,
                        localEvent.content,
                        localEvent.sig,
                    ) as? T?
                )
                    ?.let { onReady(it) }
            } else {
                (
                    EventFactory.create(
                        event.id,
                        event.pubKey,
                        event.createdAt,
                        event.kind,
                        event.tags,
                        event.content,
                        signature.split("-")[0],
                    ) as? T?
                )
                    ?.let { onReady(it) }
            }
        }
    }

    override fun nip04Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("NostrExternalSigner", "Encrypt NIP04 Event: $decryptedContent")
        }

        launcher.encrypt(
            decryptedContent,
            toPublicKey,
            SignerType.NIP04_ENCRYPT,
            onReady,
        )
    }

    override fun nip04Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("NostrExternalSigner", "Decrypt NIP04 Event: $encryptedContent")
        }

        launcher.decrypt(
            encryptedContent,
            fromPublicKey,
            SignerType.NIP04_DECRYPT,
            onReady,
        )
    }

    override fun nip44Encrypt(
        decryptedContent: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("NostrExternalSigner", "Encrypt NIP44 Event: $decryptedContent")
        }

        launcher.encrypt(
            decryptedContent,
            toPublicKey,
            SignerType.NIP44_ENCRYPT,
            onReady,
        )
    }

    override fun nip44Decrypt(
        encryptedContent: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("NostrExternalSigner", "Decrypt NIP44 Event: $encryptedContent")
        }

        launcher.decrypt(
            encryptedContent,
            fromPublicKey,
            SignerType.NIP44_DECRYPT,
            onReady,
        )
    }

    override fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    ) {
        launcher.decryptZapEvent(event) { jsonEvent ->
            try {
                (Event.fromJson(jsonEvent) as? LnZapPrivateEvent)?.let { onReady(it) }
            } catch (e: Exception) {
                Log.e("NostrExternalSigner", "Unable to parse returned decrypted Zap: $jsonEvent")
            }
        }
    }
}
