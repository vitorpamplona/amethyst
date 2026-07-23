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
package com.vitorpamplona.quartz.buzz.jobs

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz agent job progress update (`kind:43003`): an in-flight status ping for a
 * [JobRequestEvent]. References the request via `e`, optionally scopes to a channel via
 * `h`, optionally carries a `status` token; `content` is a human-readable progress
 * message.
 *
 * See [JobRequestEvent] for the shared SCHEMA CAVEAT - the 43001-43006 tag layout is
 * modeled, not confirmed against Buzz.
 */
@Immutable
class JobProgressEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The referenced job request id - the `e` tag. */
    fun jobRequest() = tags.jobRequest()

    /** The channel this job is scoped to - the `h` tag. */
    fun channel() = tags.jobChannel()

    /** The optional status token - the `status` tag. */
    fun status() = tags.jobStatus()

    companion object {
        const val KIND = 43003

        fun build(
            requestId: HexKey,
            progress: String,
            channelId: String? = null,
            status: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JobProgressEvent>.() -> Unit = {},
        ) = eventTemplate<JobProgressEvent>(KIND, progress, createdAt) {
            jobRequest(requestId)
            channelId?.let { jobChannel(it) }
            status?.let { jobStatus(it) }
            initializer()
        }
    }
}
