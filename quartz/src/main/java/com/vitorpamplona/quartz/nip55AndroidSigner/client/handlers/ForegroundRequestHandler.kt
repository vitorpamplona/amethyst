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
package com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.IntentRequestDatabase
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.DecryptZapRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.DeriveKeyRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip04DecryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip04EncryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip44DecryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip44EncryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.SignRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.DecryptZapResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.DeriveKeyResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.Nip04DecryptResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.Nip04EncryptResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.Nip44DecryptResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.Nip44EncryptResultProcessor
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.processors.SignResultProcessor
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

class ForegroundRequestHandler(
    val loggedInUser: HexKey,
    val packageName: String,
) {
    val launcher = IntentRequestDatabase()

    fun sign(
        unsignedEvent: Event,
        onReady: (Event) -> Unit,
    ) = launcher.launch(
        SignRequest.assemble(unsignedEvent, loggedInUser, packageName),
        SignResultProcessor(unsignedEvent, onReady),
    )

    fun nip04Encrypt(
        plaintext: String,
        toPubKey: HexKey,
        onReady: (String) -> Unit,
    ) = launcher.launch(
        Nip04EncryptRequest.assemble(plaintext, toPubKey, loggedInUser, packageName),
        Nip04EncryptResultProcessor(onReady),
    )

    fun nip04Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
        onReady: (String) -> Unit,
    ) = launcher.launch(
        Nip04DecryptRequest.assemble(ciphertext, fromPubKey, loggedInUser, packageName),
        Nip04DecryptResultProcessor(onReady),
    )

    fun nip44Encrypt(
        plaintext: String,
        toPubKey: HexKey,
        onReady: (String) -> Unit,
    ) = launcher.launch(
        Nip44EncryptRequest.assemble(plaintext, toPubKey, loggedInUser, packageName),
        Nip44EncryptResultProcessor(onReady),
    )

    fun nip44Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
        onReady: (String) -> Unit,
    ) = launcher.launch(
        Nip44DecryptRequest.assemble(ciphertext, fromPubKey, loggedInUser, packageName),
        Nip44DecryptResultProcessor(onReady),
    )

    fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (LnZapPrivateEvent) -> Unit,
    ) = launcher.launch(
        DecryptZapRequest.assemble(event, loggedInUser, packageName),
        DecryptZapResultProcessor(onReady),
    )

    fun deriveKey(
        nonce: HexKey,
        onReady: (HexKey) -> Unit,
    ) = launcher.launch(
        DeriveKeyRequest.assemble(nonce, loggedInUser, packageName),
        DeriveKeyResultProcessor(onReady),
    )
}
