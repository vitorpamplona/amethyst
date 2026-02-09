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

import android.content.ContentResolver
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.api.DecryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.DerivationResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.EncryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.PubKeyResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.ZapEventDecryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.DecryptZapQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.DeriveKeyQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.LoginQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.Nip04DecryptQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.Nip04EncryptQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.Nip44DecryptQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.Nip44EncryptQuery
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries.SignQuery
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

class BackgroundRequestHandler(
    loggedInUser: HexKey,
    packageName: String,
    contentResolver: ContentResolver,
) {
    val login = LoginQuery(packageName, contentResolver)
    val sign = SignQuery(loggedInUser, packageName, contentResolver)
    val nip04Encrypt = Nip04EncryptQuery(loggedInUser, packageName, contentResolver)
    val nip04Decrypt = Nip04DecryptQuery(loggedInUser, packageName, contentResolver)
    val nip44Encrypt = Nip44EncryptQuery(loggedInUser, packageName, contentResolver)
    val nip44Decrypt = Nip44DecryptQuery(loggedInUser, packageName, contentResolver)
    val decryptZap = DecryptZapQuery(loggedInUser, packageName, contentResolver)
    val deriveKey = DeriveKeyQuery(loggedInUser, packageName, contentResolver)

    fun login() = login.query() as? SignerResult.RequestAddressed<PubKeyResult>

    fun sign(unsignedEvent: Event) = sign.query(unsignedEvent) as? SignerResult.RequestAddressed<SignResult>

    fun nip04Encrypt(
        plaintext: String,
        toPubKey: HexKey,
    ) = nip04Encrypt.query(plaintext, toPubKey) as? SignerResult.RequestAddressed<EncryptionResult>

    fun nip04Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
    ) = nip04Decrypt.query(ciphertext, fromPubKey) as? SignerResult.RequestAddressed<DecryptionResult>

    fun nip44Encrypt(
        plaintext: String,
        toPubKey: HexKey,
    ) = nip44Encrypt.query(plaintext, toPubKey) as? SignerResult.RequestAddressed<EncryptionResult>

    fun nip44Decrypt(
        ciphertext: String,
        fromPubKey: HexKey,
    ) = nip44Decrypt.query(ciphertext, fromPubKey) as? SignerResult.RequestAddressed<DecryptionResult>

    fun decryptZapEvent(event: LnZapRequestEvent) = decryptZap.query(event) as? SignerResult.RequestAddressed<ZapEventDecryptionResult>

    fun deriveKey(nonce: HexKey) = deriveKey.query(nonce) as? SignerResult.RequestAddressed<DerivationResult>
}
