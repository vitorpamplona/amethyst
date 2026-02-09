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
package com.vitorpamplona.quartz.nip55AndroidSigner.client.handlers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.IntentRequestManager
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.DecryptZapRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.DeriveKeyRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip04DecryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip04EncryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip44DecryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.Nip44EncryptRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.requests.SignRequest
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.DecryptZapResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.DeriveKeyResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.Nip04DecryptResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.Nip04EncryptResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.Nip44DecryptResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.Nip44EncryptResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.responses.SignResponse
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

class ForegroundRequestHandler(
    val loggedInUser: HexKey,
    val packageName: String,
    foregroundApprovalTimeout: Long = 30000,
) {
    val launcher = IntentRequestManager(foregroundApprovalTimeout)

    suspend fun sign(unsignedEvent: Event) =
        launcher.launchWaitAndParse(
            requestIntentBuilder = { SignRequest.assemble(unsignedEvent, loggedInUser, packageName) },
            parser = { intent -> SignResponse.parse(intent, unsignedEvent) },
        )

    suspend fun nip04Encrypt(
        plaintext: String,
        toPubKey: HexKey,
    ) = launcher.launchWaitAndParse(
        requestIntentBuilder = { Nip04EncryptRequest.assemble(plaintext, toPubKey, loggedInUser, packageName) },
        parser = Nip04EncryptResponse::parse,
    )

    suspend fun nip04Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
    ) = launcher.launchWaitAndParse(
        requestIntentBuilder = { Nip04DecryptRequest.assemble(ciphertext, fromPubKey, loggedInUser, packageName) },
        parser = Nip04DecryptResponse::parse,
    )

    suspend fun nip44Encrypt(
        plaintext: String,
        toPubKey: HexKey,
    ) = launcher.launchWaitAndParse(
        requestIntentBuilder = { Nip44EncryptRequest.assemble(plaintext, toPubKey, loggedInUser, packageName) },
        parser = Nip44EncryptResponse::parse,
    )

    suspend fun nip44Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
    ) = launcher.launchWaitAndParse(
        requestIntentBuilder = { Nip44DecryptRequest.assemble(ciphertext, fromPubKey, loggedInUser, packageName) },
        parser = Nip44DecryptResponse::parse,
    )

    suspend fun decryptZapEvent(event: LnZapRequestEvent) =
        launcher.launchWaitAndParse(
            requestIntentBuilder = { DecryptZapRequest.assemble(event, loggedInUser, packageName) },
            parser = DecryptZapResponse::parse,
        )

    suspend fun deriveKey(nonce: HexKey) =
        launcher.launchWaitAndParse(
            requestIntentBuilder = { DeriveKeyRequest.assemble(nonce, loggedInUser, packageName) },
            parser = DeriveKeyResponse::parse,
        )
}
