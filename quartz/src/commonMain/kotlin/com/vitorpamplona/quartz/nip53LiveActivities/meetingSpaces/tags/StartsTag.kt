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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `["starts", "<unix-seconds>"]` on a kind-30312 audio-room event
 * with [StatusTag.STATUS.PLANNED]. Tells subscribers when the host
 * intends to start the room. nostrnests' room-list uses it to sort
 * upcoming rooms ahead of live ones.
 *
 * Strict numeric parser — non-numeric values return null so a
 * malformed tag can't crash the room-list renderer.
 */
class StartsTag {
    companion object {
        const val TAG_NAME = "starts"

        fun parse(tag: Array<String>): Long? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].toLongOrNull()
        }

        fun assemble(unixSeconds: Long): Array<String> {
            require(unixSeconds >= 0) { "starts: unix seconds must be non-negative, got $unixSeconds" }
            return arrayOf(TAG_NAME, unixSeconds.toString())
        }
    }
}
