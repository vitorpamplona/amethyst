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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.background.queries

import android.content.ContentResolver
import androidx.core.net.toUri
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.EncryptionResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.utils.getStringByName
import com.vitorpamplona.quartz.nip55AndroidSigner.api.background.utils.query

class Nip44EncryptQuery(
    val loggedInUser: HexKey,
    val packageName: String,
    val contentResolver: ContentResolver,
) {
    val uri = "content://$packageName.${CommandType.NIP44_ENCRYPT}".toUri()

    fun query(
        plaintext: String,
        toPubKey: HexKey,
    ): SignerResult<EncryptionResult> =
        contentResolver.query(
            uri,
            arrayOf(plaintext, toPubKey, loggedInUser),
        ) { cursor ->
            val ciphertext = cursor.getStringByName("result")
            if (!ciphertext.isNullOrBlank()) {
                SignerResult.RequestAddressed.Successful(EncryptionResult(ciphertext))
            } else {
                SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
            }
        }
}
