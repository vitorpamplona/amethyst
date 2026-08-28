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

import androidx.compose.runtime.Stable

/**
 * Kind-agnostic view of a Trusted List member.
 *
 * Every kind in the family carries its members in the tag its last digit
 * denotes (`p`/`e`/`a`/`i`), all with the same layout:
 * `[<tagName>, <memberValue>, <hint>, <score>]`. A reader that only needs the
 * membership can dispatch on this interface instead of on the kind.
 */
@Stable
interface TrustedListMemberTag {
    /** The pubkey, event id, a-coordinate or external id of this member. */
    val memberValue: String

    /**
     * The computed score the publisher assigned to this member, if any, as a
     * percentage in [MemberTagFields.SCORE_RANGE] (0..100). Null both when the
     * tag carries no score and when it carries one this scale cannot express.
     */
    val score: Int?
}
