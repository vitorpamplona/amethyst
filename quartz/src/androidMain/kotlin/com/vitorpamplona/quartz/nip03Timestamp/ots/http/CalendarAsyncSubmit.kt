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

import com.vitorpamplona.quartz.nip03Timestamp.ots.ICalendarAsyncSubmit
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp.Companion.deserialize
import java.net.URL
import java.util.Optional
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * For making async calls to a calendar server
 */
class CalendarAsyncSubmit(
    private val url: String,
    private val digest: ByteArray,
) : ICalendarAsyncSubmit {
    private var queue: BlockingQueue<Optional<Timestamp>> = ArrayBlockingQueue(1)

    fun setQueue(queue: BlockingQueue<Optional<Timestamp>>) {
        this.queue = queue
    }

    @Throws(Exception::class)
    override fun call(): Optional<Timestamp> {
        val headers: MutableMap<String, String> = HashMap()
        headers.put("Accept", "application/vnd.opentimestamps.v1")
        headers.put("User-Agent", "java-opentimestamps")
        headers.put("Content-Type", "application/x-www-form-urlencoded")

        val obj = URL("$url/digest")
        val task = Request(obj)
        task.setData(digest)
        task.setHeaders(headers)
        val response = task.call()

        if (response.isOk) {
            val body = response.bytes
            val ctx = StreamDeserializationContext(body)
            val timestamp = deserialize(ctx, digest)
            val of: Optional<Timestamp> = Optional.of<Timestamp>(timestamp)
            queue.add(of)
            return of
        }

        queue.add(Optional.empty<Timestamp>())

        return Optional.empty<Timestamp>()
    }
}
