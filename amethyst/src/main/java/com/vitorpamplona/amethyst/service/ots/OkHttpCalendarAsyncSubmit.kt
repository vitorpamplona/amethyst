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
package com.vitorpamplona.amethyst.service.ots

import com.vitorpamplona.quartz.nip03Timestamp.ots.ICalendarAsyncSubmit
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Optional

/**
 * For making async calls to a calendar server
 */
class OkHttpCalendarAsyncSubmit(
    private val url: String,
    private val digest: ByteArray,
    private val client: OkHttpClient,
) : ICalendarAsyncSubmit {
    override fun call(): Optional<Timestamp> {
        val url = "$url/digest"
        val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
        val requestBody = digest.toRequestBody(mediaType)

        val request =
            okhttp3.Request
                .Builder()
                .header("Accept", "application/vnd.opentimestamps.v1")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .url(url)
                .post(requestBody)
                .build()

        client.newCall(request).execute().use {
            if (it.isSuccessful) {
                val ctx =
                    StreamDeserializationContext(
                        it.body.bytes(),
                    )
                val timestamp = Timestamp.deserialize(ctx, digest)
                return Optional.of(timestamp)
            } else {
                return Optional.empty()
            }
        }
    }
}
