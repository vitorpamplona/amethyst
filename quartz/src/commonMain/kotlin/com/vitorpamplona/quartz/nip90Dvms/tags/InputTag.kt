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
package com.vitorpamplona.quartz.nip90Dvms.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

@Stable
class InputTag(
    val value: String,
    val type: String,
    val relay: String? = null,
    val marker: String? = null,
) {
    companion object {
        const val TAG_NAME = "i"
        const val TYPE_EVENT = "event"
        const val TYPE_JOB = "job"

        fun parse(tag: Array<String>): InputTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val relay = if (tag.has(3) && tag[3].isNotBlank()) tag[3] else null
            val marker = if (tag.has(4) && tag[4].isNotBlank()) tag[4] else null
            return InputTag(tag[1], tag[2], relay, marker)
        }

        /**
         * The event id of an `event` input (`["i", <id>, "event", <relay>]`) or a `job` input
         * (the request whose output this job chains on): both are event pointers.
         */
        fun parseEventId(tag: Array<String>): HexKey? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex64(tag[1])) { return null }
            ensure(tag[2] == TYPE_EVENT || tag[2] == TYPE_JOB) { return null }
            return tag[1]
        }

        /** [parseEventId] with the relay hint NIP-90 puts in slot 3. */
        fun parseEventAsHint(tag: Array<String>): EventIdHint? {
            ensure(tag.has(3)) { return null }
            ensure(tag[3].isNotEmpty()) { return null }
            val id = parseEventId(tag) ?: return null
            val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[3]) ?: return null
            return EventIdHint(id, relay)
        }

        fun assembleUrl(url: String) = arrayOf(TAG_NAME, url, "url")

        fun assembleText(text: String) = arrayOf(TAG_NAME, text, "text")

        fun assembleEvent(eventId: String) = arrayOf(TAG_NAME, eventId, "event")

        fun assembleJob(jobId: String) = arrayOf(TAG_NAME, jobId, "job")

        fun assemblePrompt(prompt: String) = arrayOf(TAG_NAME, prompt, "prompt")
    }
}
