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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.vitorpamplona.quartz.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip17Dm.NostrCipher
import java.io.File

class EncryptFilesResult(
    val uri: Uri,
    val contentType: String?,
    val originalHash: String,
    val encryptedHash: String,
    val size: Long?,
)

class EncryptFiles {
    fun encryptFile(
        context: Context,
        inputFile: Uri,
        cipher: NostrCipher,
    ): EncryptFilesResult {
        val resolver = context.contentResolver

        val encryptedFile = File.createTempFile("EncryptFiles", ".encrypted", context.getCacheDir())

        resolver.openInputStream(inputFile)!!.use { inputStream ->
            val bytes = inputStream.readBytes()
            val originalHash = CryptoUtils.sha256(bytes).toHexKey()
            val encrypted = cipher.encrypt(bytes)
            val encryptedHash = CryptoUtils.sha256(encrypted).toHexKey()

            encryptedFile.outputStream().use { outputStream ->
                outputStream.write(encrypted)
            }

            return EncryptFilesResult(
                encryptedFile.toUri(),
                "application/octet-stream",
                originalHash,
                encryptedHash,
                encrypted.size.toLong(),
            )
        }
    }
}
