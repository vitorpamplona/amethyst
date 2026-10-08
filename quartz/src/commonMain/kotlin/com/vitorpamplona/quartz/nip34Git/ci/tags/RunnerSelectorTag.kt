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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** One accepted runner selector within a family: `act:ubuntu-latest` is family `act`, selector `ubuntu-latest`. */
@Immutable
data class CiRunnerSelector(
    val family: String,
    val selector: String,
) {
    fun toValue() = "$family:$selector"
}

/**
 * Nostr CI `R` tag: `["R", "<family>:<selector>"]`, split at its **first** `:`. Both halves must be
 * non-empty; values are ASCII-case-folded, so both come back lowercased.
 */
class RunnerSelectorTag {
    companion object {
        const val TAG_NAME = "R"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiRunnerSelector? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val colon = tag[1].indexOf(':')
            ensure(colon > 0 && colon < tag[1].length - 1) { return null }
            return CiRunnerSelector(tag[1].substring(0, colon).lowercase(), tag[1].substring(colon + 1).lowercase())
        }

        fun assemble(selector: CiRunnerSelector) = arrayOf(TAG_NAME, selector.toValue())
    }
}
