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

/** Why a declared job output carries no value. */
enum class CiOutputOmissionReason(
    val code: String,
) {
    /** Execution produced no value. */
    MISSING("missing"),

    /** Publishing it would exceed the 8 KiB per-value or 64 KiB per-result limit. */
    OVERSIZED("oversized"),

    /** The execution backend could not reduce it to a value. */
    UNRESOLVED("unresolved"),
    ;

    companion object {
        fun parse(code: String): CiOutputOmissionReason? =
            when (code) {
                MISSING.code -> MISSING
                OVERSIZED.code -> OVERSIZED
                UNRESOLVED.code -> UNRESOLVED
                else -> null
            }
    }
}

/**
 * A declared output without a value. [reason] is null for a reason this version does not know; the
 * output is unavailable either way, and a consumer MUST NOT substitute an empty string.
 */
@Immutable
data class CiOmittedOutput(
    val name: String,
    val reason: CiOutputOmissionReason?,
)

/** Nostr CI `output-omitted` tag: `["output-omitted", "<name>", "<missing|oversized|unresolved>"]`. */
class OutputOmittedTag {
    companion object {
        const val TAG_NAME = "output-omitted"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): CiOmittedOutput? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return CiOmittedOutput(tag[1], tag.getOrNull(2)?.let { CiOutputOmissionReason.parse(it) })
        }

        fun assemble(
            name: String,
            reason: CiOutputOmissionReason,
        ) = arrayOf(TAG_NAME, name, reason.code)
    }
}
