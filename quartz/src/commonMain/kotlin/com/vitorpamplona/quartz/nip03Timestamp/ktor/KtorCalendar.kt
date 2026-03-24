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
package com.vitorpamplona.quartz.nip03Timestamp.ktor

import com.vitorpamplona.quartz.nip03Timestamp.ots.RemoteCalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.CommitmentNotFoundException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.ExceededSizeException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.utils.Hex
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Class representing remote calendar server interface using Ktor.
 */
class KtorCalendar(
    val httpClient: HttpClient,
) : RemoteCalendar {
    override suspend fun submit(
        url: String,
        digest: ByteArray,
    ): Timestamp {
        val requestUrl = "$url/digest"

        val response =
            httpClient.post(requestUrl) {
                header("Accept", "application/vnd.opentimestamps.v1")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(digest)
            }

        if (response.status.isSuccess()) {
            val ctx = StreamDeserializationContext(response.readRawBytes())
            return Timestamp.deserialize(ctx, digest)
        } else {
            throw UrlException("Failed to open $requestUrl")
        }
    }

    override suspend fun getTimestamp(
        url: String,
        commitment: ByteArray,
    ): Timestamp =
        try {
            val requestUrl = url + "/timestamp/" + Hex.encode(commitment)

            val response =
                httpClient.get(requestUrl) {
                    header("Accept", "application/vnd.opentimestamps.v1")
                    contentType(ContentType.Application.FormUrlEncoded)
                }

            if (response.status.isSuccess()) {
                val ctx = StreamDeserializationContext(response.readRawBytes())
                Timestamp.deserialize(ctx, commitment)
            } else {
                throw CommitmentNotFoundException("Calendar response a status code != 200: " + response.status.value)
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
