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
package com.vitorpamplona.quartz.nip66RelayMonitor.discovery.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** Round-trip time measurement types */
enum class RttType(
    val tagName: String,
) {
    OPEN("rtt-open"),
    READ("rtt-read"),
    WRITE("rtt-write"),
}

/**
 * Round-trip time tags measured in milliseconds.
 *
 * ["rtt-open", "<milliseconds>"]
 * ["rtt-read", "<milliseconds>"]
 * ["rtt-write", "<milliseconds>"]
 */
@Stable
class RttTag(
    val type: RttType,
    val milliseconds: Long,
) {
    companion object {
        fun isTag(tag: Array<String>) = tag.has(1) && RttType.entries.any { it.tagName == tag[0] }

        fun parse(tag: Array<String>): RttTag? {
            ensure(tag.has(1)) { return null }
            val type = RttType.entries.find { it.tagName == tag[0] } ?: return null
            val ms = tag[1].toLongOrNull() ?: return null
            return RttTag(type, ms)
        }

        fun assemble(
            type: RttType,
            milliseconds: Long,
        ) = arrayOf(type.tagName, milliseconds.toString())

        fun assemble(rtt: RttTag) = assemble(rtt.type, rtt.milliseconds)
    }
}
