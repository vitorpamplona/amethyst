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
 * A Buzz agent job cancellation (`kind:43005`): a request to cancel an in-flight
 * [JobRequestEvent]. References the request via `e` and optionally scopes to a channel
 * via `h`; `content` is an optional free-text reason.
 *
 * See [JobRequestEvent] for the shared SCHEMA CAVEAT - the 43001-43006 tag layout is
 * modeled, not confirmed against Buzz.
 */
@Immutable
class JobCancelEvent(
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

    /** The optional cancellation reason - the event `content`. */
    fun reason() = content

    companion object {
        const val KIND = 43005

        fun build(
            requestId: HexKey,
            reason: String = "",
            channelId: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<JobCancelEvent>.() -> Unit = {},
        ) = eventTemplate<JobCancelEvent>(KIND, reason, createdAt) {
            jobRequest(requestId)
            channelId?.let { jobChannel(it) }
            initializer()
        }
    }
}
