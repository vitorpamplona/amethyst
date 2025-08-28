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
package com.vitorpamplona.quartz.nip03Timestamp.ots.http

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip03Timestamp.ots.ICalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp.Companion.deserialize
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.CommitmentNotFoundException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.ExceededSizeException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import java.net.URL

/**
 * Class representing remote calendar server interface.
 */
class Calendar(
    val url: String,
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
            val headers: MutableMap<String, String> = HashMap()
            headers.put("Accept", "application/vnd.opentimestamps.v1")
            headers.put("User-Agent", "java-opentimestamps")
            headers.put("Content-Type", "application/x-www-form-urlencoded")

            val obj = URL("$url/digest")
            val task = Request(obj)
            task.setData(digest)
            task.setHeaders(headers)
            val response = task.call()
            val body = response.bytes
            if (body.size > 10000) {
                throw ExceededSizeException("Calendar response exceeded size limit")
            }

            val ctx = StreamDeserializationContext(body)
            return deserialize(ctx, digest)
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
            val headers: MutableMap<String, String> = HashMap()
            headers.put("Accept", "application/vnd.opentimestamps.v1")
            headers.put("User-Agent", "java-opentimestamps")
            headers.put("Content-Type", "application/x-www-form-urlencoded")

            val obj = URL(url + "/timestamp/" + commitment.toHexKey().lowercase())
            val task = Request(obj)
            task.setHeaders(headers)
            val response = task.call()
            val body = response.bytes
            if (body.size > 10000) {
                throw ExceededSizeException("Calendar response exceeded size limit")
            }

            if (!response.isOk) {
                throw CommitmentNotFoundException("com.eternitywall.ots.Calendar response a status code != 200 which is: " + response.status)
            }

            val ctx = StreamDeserializationContext(body)

            return deserialize(ctx, commitment)
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
