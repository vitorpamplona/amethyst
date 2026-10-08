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
 * A coordinator's billing statement (`B`, optional).
 *
 * [UNKNOWN] stands for any value this version does not define; the spec forbids reading an unknown
 * policy as free service, so it must never be treated as [NOT_REQUIRED].
 */
enum class CiBillingPolicy(
    val code: String,
) {
    /** No payment is required. */
    NOT_REQUIRED("not-required"),

    /** Payment is arranged outside this protocol. */
    OUT_OF_BAND("out-of-band"),

    /** A value this version does not know. Never treat it as [NOT_REQUIRED]. */
    UNKNOWN(""),
    ;

    companion object {
        fun parse(code: String): CiBillingPolicy =
            when (code) {
                NOT_REQUIRED.code -> NOT_REQUIRED
                OUT_OF_BAND.code -> OUT_OF_BAND
                else -> UNKNOWN
            }
    }
}

/** Nostr CI `B` tag on a Coordinator Advertisement (19843): the billing policy. */
class BillingTag {
    companion object {
        const val TAG_NAME = "B"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): CiBillingPolicy? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return CiBillingPolicy.parse(tag[1])
        }

        fun assemble(policy: CiBillingPolicy) = arrayOf(TAG_NAME, policy.code)
    }
}
