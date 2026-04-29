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

class StatusTag {
    /**
     * Canonical status codes match the deployed nostrnests reference:
     * `live` for an in-progress room, `ended` for a closed one,
     * `planned` for a scheduled future room. The earlier EGG-01 draft
     * used `open` / `closed`; those values are accepted on read for
     * back-compat but never emitted.
     *
     * `private` is a forward-looking value for invite-only rooms; it
     * is not yet emitted by the deployed reference but is reserved on
     * the spec side so the auth sidecar's `not_invited` error has a
     * room status to anchor against.
     */
    enum class STATUS(
        val code: String,
    ) {
        /** Scheduled in the future — host hasn't started the room yet. Pairs with a `["starts", <unix>]` tag. */
        PLANNED("planned"),

        /** Live, in-progress room (formerly `open` in the EGG-01 draft). */
        LIVE("live"),

        /** Reserved for invite-only rooms; not yet emitted by deployed clients. */
        PRIVATE("private"),

        /** Room is over (formerly `closed` in the EGG-01 draft). */
        ENDED("ended"),
        ;

        fun toTagArray() = assemble(this)

        companion object {
            fun parse(code: String): STATUS? =
                when (code) {
                    PLANNED.code -> PLANNED

                    LIVE.code -> LIVE

                    PRIVATE.code -> PRIVATE

                    ENDED.code -> ENDED

                    // Earlier EGG-01 draft used "open" / "closed". Accept
                    // on read for back-compat; we always emit the deployed
                    // nostrnests codes ("live" / "ended").
                    "open" -> LIVE

                    "closed" -> ENDED

                    else -> null
                }
        }
    }

    companion object {
        const val TAG_NAME = "status"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parseEnum(tag: Array<String>): STATUS? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return STATUS.parse(tag[1])
        }

        fun assemble(status: STATUS) = arrayOf(TAG_NAME, status.code)
    }
}
