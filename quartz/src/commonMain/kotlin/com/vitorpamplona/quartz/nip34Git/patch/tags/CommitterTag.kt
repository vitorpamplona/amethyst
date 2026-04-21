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
package com.vitorpamplona.quartz.nip34Git.patch.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-34 patch `committer` tag: `["committer", name, email, unix-time, tz-offset-minutes]`.
 */
data class Committer(
    val name: String?,
    val email: String?,
    val timestamp: String?,
    val timezoneInMinutes: String?,
)

class CommitterTag {
    companion object {
        const val TAG_NAME = "committer"

        fun parse(tag: Array<String>): Committer? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return Committer(
                tag.getOrNull(1),
                tag.getOrNull(2),
                tag.getOrNull(3),
                tag.getOrNull(4),
            )
        }

        fun assemble(
            name: String?,
            email: String?,
            timestamp: String?,
            timezoneInMinutes: String?,
        ) = arrayOf(
            TAG_NAME,
            name ?: "",
            email ?: "",
            timestamp ?: "",
            timezoneInMinutes ?: "",
        )
    }
}
