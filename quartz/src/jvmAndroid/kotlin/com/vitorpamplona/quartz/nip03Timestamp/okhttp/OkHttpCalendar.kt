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
package com.vitorpamplona.quartz.nip03Timestamp.okhttp

import com.vitorpamplona.quartz.nip03Timestamp.ots.RemoteCalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.CommitmentNotFoundException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.ExceededSizeException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/**
 * Class representing remote calendar server interface.
 */
class OkHttpCalendar(
    val okHttpClient: (url: String) -> OkHttpClient,
) : RemoteCalendar {
    override suspend fun submit(
        url: String,
        digest: ByteArray,
    ): Timestamp {
        val url = "$url/digest"

        val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
        val requestBody = digest.toRequestBody(mediaType)

        val request =
            Request
                .Builder()
                .header("Accept", "application/vnd.opentimestamps.v1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .url(url)
                .post(requestBody)
                .build()

        val client = okHttpClient(url)
        return client.newCall(request).executeAsync().use {
            if (it.isSuccessful) {
                val ctx =
                    StreamDeserializationContext(
                        it.body.bytes(),
                    )
                Timestamp.deserialize(ctx, digest)
            } else {
                throw UrlException(
                    "Failed to open $url",
                )
            }
        }
    }

    override suspend fun getTimestamp(
        url: String,
        commitment: ByteArray,
    ): Timestamp =
        try {
            val url = url + "/timestamp/" + Hex.encode(commitment)

            val request =
                Request
                    .Builder()
                    .header("Accept", "application/vnd.opentimestamps.v1")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .url(url)
                    .get()
                    .build()

            val client = okHttpClient(url)
            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        val ctx = StreamDeserializationContext(response.body.bytes())
                        Timestamp.deserialize(ctx, commitment)
                    } else {
                        throw CommitmentNotFoundException("Calendar response a status code != 200: " + response.code)
                    }
                }
            }
        } catch (e: DeserializationException) {
            throw e
        } catch (e: ExceededSizeException) {
            throw e
        } catch (e: CommitmentNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw UrlException(e.message)
        }
}
