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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** The state a Workflow Progress (39842) marker reports. */
enum class CiWorkflowStatus(
    val code: String,
) {
    QUEUED("queued"),
    IN_PROGRESS("in_progress"),
    CONCLUDED("concluded"),
    ;

    companion object {
        fun parse(code: String): CiWorkflowStatus? =
            when (code) {
                QUEUED.code -> QUEUED
                IN_PROGRESS.code -> IN_PROGRESS
                CONCLUDED.code -> CONCLUDED
                else -> null
            }
    }
}

/** Nostr CI `status` tag. Unknown values parse to null. */
class WorkflowStatusTag {
    companion object {
        const val TAG_NAME = "status"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): CiWorkflowStatus? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return CiWorkflowStatus.parse(tag[1])
        }

        fun assemble(status: CiWorkflowStatus) = arrayOf(TAG_NAME, status.code)
    }
}
