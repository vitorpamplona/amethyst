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
package com.vitorpamplona.amethyst.service.ots

import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.ots.ICalendar
import com.vitorpamplona.quartz.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.ots.Timestamp
import com.vitorpamplona.quartz.ots.exceptions.CommitmentNotFoundException
import com.vitorpamplona.quartz.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.ots.exceptions.ExceededSizeException
import com.vitorpamplona.quartz.ots.exceptions.UrlException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Class representing remote calendar server interface.
 */
class OkHttpCalendar(
    val url: String,
    val forceProxy: Boolean,
) : ICalendar {
    /**
     * Submitting a digest to remote calendar. Returns a com.eternitywall.ots.Timestamp committing to that digest.
     *
     * @param digest The digest hash to send.
     * @return the Timestamp received from the calendar.
     * @throws ExceededSizeException if response is too big.
     * @throws UrlException          if url is not reachable.
     * @throws DeserializationException    if the data is corrupt
     */
    @Throws(ExceededSizeException::class, UrlException::class, DeserializationException::class)
    override fun submit(digest: ByteArray): Timestamp {
        try {
            val client = HttpClientManager.getHttpClient(forceProxy)
            val url = "$url/digest"

            val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
            val requestBody = digest.toRequestBody(mediaType)

            val request =
                okhttp3.Request
                    .Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.opentimestamps.v1")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .url(url)
                    .post(requestBody)
                    .build()

            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    val ctx = StreamDeserializationContext(it.body.bytes())
                    return Timestamp.deserialize(ctx, digest)
                } else {
                    throw UrlException("Failed to open $url")
                }
            }
        } catch (e: ExceededSizeException) {
            throw e
        } catch (e: DeserializationException) {
            throw e
        } catch (e: Exception) {
            throw UrlException(e.message)
        }
    }

    /**
     * Get a timestamp for a given commitment.
     *
     * @param commitment The digest hash to send.
     * @return the Timestamp from the calendar server (with blockchain information if already written).
     * @throws ExceededSizeException       if response is too big.
     * @throws UrlException                if url is not reachable.
     * @throws CommitmentNotFoundException if commit is not found.
     * @throws DeserializationException    if the data is corrupt
     */
    @Throws(
        DeserializationException::class,
        ExceededSizeException::class,
        CommitmentNotFoundException::class,
        UrlException::class,
    )
    override fun getTimestamp(commitment: ByteArray): Timestamp {
        try {
            val client = HttpClientManager.getHttpClient(forceProxy)
            val url = url + "/timestamp/" + Hex.encode(commitment)

            val request =
                okhttp3.Request
                    .Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.opentimestamps.v1")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .url(url)
                    .get()
                    .build()

            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    val ctx = StreamDeserializationContext(it.body.bytes())
                    return Timestamp.deserialize(ctx, commitment)
                } else {
                    throw CommitmentNotFoundException("Calendar response a status code != 200: " + it.code)
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
}
