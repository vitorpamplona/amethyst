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
package com.vitorpamplona.quartz.experimental.trustedLists.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

enum class ListStatus(
    val code: String,
) {
    RETRACTED("retracted"),
}

/**
 * Marks the retraction convention: a list that no longer has members, or that
 * is being migrated off a kind, is replaced in place by an empty-membership
 * event carrying `["status", "retracted"]` rather than being deleted.
 */
class StatusTag {
    companion object {
        const val TAG_NAME = "status"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Tag): ListStatus? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return when (tag[1]) {
                ListStatus.RETRACTED.code -> ListStatus.RETRACTED
                else -> null
            }
        }

        fun assemble(status: ListStatus) = arrayOf(TAG_NAME, status.code)
    }
}
