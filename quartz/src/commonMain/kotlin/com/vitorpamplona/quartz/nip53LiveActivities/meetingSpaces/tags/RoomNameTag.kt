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
 * Room display name on a NIP-53 / nests `kind:30312` event. Matches
 * the deployed nostrnests reference, which writes `["title", name]`
 * AND filters its lobby on the `title` tag's presence — kind-30312
 * events without a `title` are dropped from "Live Now". The early
 * EGG-01 draft called this `room`; we accept that name on read for
 * older events but always emit `title`.
 */
class RoomNameTag {
    companion object Companion {
        const val TAG_NAME = "title"

        /**
         * Earlier EGG-01 draft name for the room display name.
         * Accepted on read for back-compat; we always emit the
         * canonical [TAG_NAME].
         */
        const val LEGACY_TAG_NAME = "room"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME || tag[0] == LEGACY_TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(title: String) = arrayOf(TAG_NAME, title)
    }
}
