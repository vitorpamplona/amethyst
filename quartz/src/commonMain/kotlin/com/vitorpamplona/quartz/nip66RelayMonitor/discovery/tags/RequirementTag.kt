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

/**
 * ["R", "<requirement>"] - access requirement enforced by the relay.
 *
 * A leading "!" negates the requirement, meaning the relay does NOT enforce it.
 * Examples: "auth", "payment", "!payment" (no payment required), "pow", "writes"
 */
@Stable
class RequirementTag(
    val value: String,
    val negated: Boolean,
) {
    companion object {
        const val TAG_NAME = "R"
        private const val NEGATION_PREFIX = "!"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): RequirementTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val raw = tag[1]
            return if (raw.startsWith(NEGATION_PREFIX)) {
                RequirementTag(value = raw.removePrefix(NEGATION_PREFIX), negated = true)
            } else {
                RequirementTag(value = raw, negated = false)
            }
        }

        fun assemble(
            value: String,
            negated: Boolean = false,
        ) = arrayOf(TAG_NAME, if (negated) "$NEGATION_PREFIX$value" else value)

        fun assemble(req: RequirementTag) = assemble(req.value, req.negated)

        fun assemble(requirements: List<RequirementTag>) = requirements.map { assemble(it) }
    }
}
