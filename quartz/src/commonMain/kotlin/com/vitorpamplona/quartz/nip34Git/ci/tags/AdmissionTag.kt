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

/**
 * How a coordinator admits repositories (`M`).
 *
 * [UNKNOWN] stands for any value this version does not define. The spec is explicit that an
 * unknown policy value MUST NOT be interpreted as open, so it is kept apart from [OPEN] (and from
 * an absent tag, which reads as null) rather than collapsed into a guess.
 */
enum class CiAdmissionPolicy(
    val code: String,
) {
    /** Bounded by operator policy. */
    OPERATOR_SELECTED("operator-selected"),

    /** A maintainer's Service Request may start admission. */
    MAINTAINER_REQUEST("maintainer-request"),

    /** Accepts repositories without either restriction. */
    OPEN("open"),

    /** A value this version does not know. Never treat it as [OPEN]. */
    UNKNOWN(""),
    ;

    companion object {
        fun parse(code: String): CiAdmissionPolicy =
            when (code) {
                OPERATOR_SELECTED.code -> OPERATOR_SELECTED
                MAINTAINER_REQUEST.code -> MAINTAINER_REQUEST
                OPEN.code -> OPEN
                else -> UNKNOWN
            }
    }
}

/** Nostr CI `M` tag on a Coordinator Advertisement (19843): the admission policy. */
class AdmissionTag {
    companion object {
        const val TAG_NAME = "M"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): CiAdmissionPolicy? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return CiAdmissionPolicy.parse(tag[1])
        }

        fun assemble(policy: CiAdmissionPolicy) = arrayOf(TAG_NAME, policy.code)
    }
}
