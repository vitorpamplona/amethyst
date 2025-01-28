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
package com.vitorpamplona.amethyst.service.okhttp

import android.util.Log
import com.vitorpamplona.quartz.nip17Dm.AESGCM
import com.vitorpamplona.quartz.nip17Dm.NostrCipher
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class EncryptedBlobInterceptor(
    val cache: EncryptionKeyCache,
) : Interceptor {
    fun Response.decrypt(cipher: NostrCipher): Response {
        val body = peekBody(Long.MAX_VALUE)
        val decryptedBytes = cipher.decrypt(body.bytes())
        val newBody = decryptedBytes.toResponseBody(body.contentType())
        return newBuilder().body(newBody).build()
    }

    fun Response.decryptOrNull(cipher: NostrCipher): Response? =
        try {
            decrypt(cipher)
        } catch (e: Exception) {
            Log.w("EncryptedBlobInterceptor", "Failed to decrypt", e)
            null
        }

    private fun Response.decryptOrNullWithErrorCorrection(cipher: NostrCipher): Response? {
        return decryptOrNull(cipher) ?: return if (cipher is AESGCM) {
            decryptOrNull(cipher.copyUsingUTF8Nonce())
        } else {
            null
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val cipher = cache.get(request.url.toString()) ?: return response

        if (response.isSuccessful) {
            return response.decryptOrNullWithErrorCorrection(cipher) ?: response
        } else {
            // Log redirections to be able to use the cipher.
            response.header("Location")?.let {
                cache.add(it, cipher)
            }
        }
        return response
    }
}
