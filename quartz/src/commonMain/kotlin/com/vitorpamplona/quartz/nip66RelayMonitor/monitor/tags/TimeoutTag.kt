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
package com.vitorpamplona.quartz.nip66RelayMonitor.monitor.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Timeout tag for relay monitors.
 *
 * General form:  ["timeout", "<milliseconds>"]
 * Per-check form: ["timeout", "<check-type>", "<milliseconds>"]
 *
 * Where check-type is one of: open, read, write, nip11
 */
@Stable
class TimeoutTag(
    val checkType: String?,
    val milliseconds: Long,
) {
    companion object {
        const val TAG_NAME = "timeout"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): TimeoutTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return if (tag.has(2)) {
                val ms = tag[2].toLongOrNull() ?: return null
                TimeoutTag(checkType = tag[1], milliseconds = ms)
            } else {
                val ms = tag[1].toLongOrNull() ?: return null
                TimeoutTag(checkType = null, milliseconds = ms)
            }
        }

        fun assemble(milliseconds: Long) = arrayOf(TAG_NAME, milliseconds.toString())

        fun assemble(
            checkType: String,
            milliseconds: Long,
        ) = arrayOf(TAG_NAME, checkType, milliseconds.toString())

        fun assemble(timeout: TimeoutTag) =
            if (timeout.checkType != null) {
                assemble(timeout.checkType, timeout.milliseconds)
            } else {
                assemble(timeout.milliseconds)
            }

        fun assemble(timeouts: List<TimeoutTag>) = timeouts.map { assemble(it) }
    }
}
