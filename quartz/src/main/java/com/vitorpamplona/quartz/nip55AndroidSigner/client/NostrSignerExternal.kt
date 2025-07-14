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
package com.vitorpamplona.quartz.nip55AndroidSigner.client

import android.content.ContentResolver
import android.content.Intent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers.BackgroundRequestHandler
import com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers.ForegroundRequestHandler
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

class NostrSignerExternal(
    pubKey: HexKey,
    packageName: String,
    contentResolver: ContentResolver,
) : NostrSigner(pubKey),
    IActivityLauncher {
    override fun isWriteable(): Boolean = true

    val backgroundQuery = BackgroundRequestHandler(pubKey, packageName, contentResolver)
    val foregroundQuery = ForegroundRequestHandler(pubKey, packageName)

    override fun registerForegroundLauncher(launcher: ((Intent) -> Unit)) {
        this.foregroundQuery.launcher.registerForegroundLauncher(launcher)
    }

    override fun unregisterForegroundLauncher(launcher: ((Intent) -> Unit)) {
        this.foregroundQuery.launcher.unregisterForegroundLauncher(launcher)
    }

    override fun newResponse(data: Intent) {
        this.foregroundQuery.launcher.newResponse(data)
    }

    override fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
        onReady: (T) -> Unit,
    ) {
        val unsignedEvent =
            Event(
                id = EventHasher.hashId(pubKey, createdAt, kind, tags, content),
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )

        val newOnReady: (Event) -> Unit = { result ->
            (result as? T)?.let(onReady)
        }

        if (!backgroundQuery.sign(unsignedEvent, newOnReady)) {
            foregroundQuery.sign(unsignedEvent, newOnReady)
        }
    }

    override fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (!backgroundQuery.nip04Encrypt(plaintext, toPublicKey, onReady)) {
            foregroundQuery.nip04Encrypt(plaintext, toPublicKey, onReady)
        }
    }

    override fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (!backgroundQuery.nip04Decrypt(ciphertext, fromPublicKey, onReady)) {
            foregroundQuery.nip04Decrypt(ciphertext, fromPublicKey, onReady)
        }
    }

    override fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (!backgroundQuery.nip44Encrypt(plaintext, toPublicKey, onReady)) {
            foregroundQuery.nip44Encrypt(plaintext, toPublicKey, onReady)
        }
    }

    override fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
        onReady: (String) -> Unit,
    ) {
        if (!backgroundQuery.nip44Decrypt(ciphertext, fromPublicKey, onReady)) {
            foregroundQuery.nip44Decrypt(ciphertext, fromPublicKey, onReady)
        }
    }

    override fun deriveKey(
        nonce: HexKey,
        onReady: (HexKey) -> Unit,
    ) {
        if (!backgroundQuery.deriveKey(nonce, onReady)) {
            foregroundQuery.deriveKey(nonce, onReady)
        }
    }

    override fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    ) {
        if (!backgroundQuery.decryptZapEvent(event, onReady)) {
            foregroundQuery.decryptZapEvent(event, onReady)
        }
    }
}
