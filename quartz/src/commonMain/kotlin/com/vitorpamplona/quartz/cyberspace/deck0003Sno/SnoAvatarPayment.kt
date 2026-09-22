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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import androidx.compose.runtime.Immutable

/**
 * Why an avatar may or may not be drawn (`CYBERSPACE_V2.md` §8.10).
 *
 * The fields and the [reason] vocabulary deliberately mirror
 * `verify_avatar_work` in the reference implementations, so the two can be
 * diffed directly rather than compared by eye.
 */
@Immutable
data class SnoAvatarPayment(
    /** Whether a client may draw this avatar. */
    val ok: Boolean,
    /** The leading zero bits this shape owes, or 0 when the shape is unreadable. */
    val required: Int,
    /** The target the publisher committed to in its `nonce` tag, if it wrote one. */
    val committed: Int?,
    /** The leading zero bits the event id actually carries. */
    val zeros: Int,
    val reason: Reason,
) {
    enum class Reason(
        val code: String,
    ) {
        OK("ok"),

        /**
         * Empty content, which §8.10 defines as the default avatar: it owes no
         * work and there is nothing to draw but the client's own default.
         *
         * The reference implementations answer `not-an-avatar` here, because
         * their check begins by parsing the content and empty is not JSON. The
         * two agree on what happens — the default gets drawn — and differ only
         * in what they call it.
         */
        DEFAULT_AVATAR("default-avatar"),

        /** Not kind 11333, or content that cannot be read as a payload. */
        NOT_AN_AVATAR("not-an-avatar"),

        /** No `nonce` tag, so nothing was committed to before mining (NIP-13). */
        NO_NONCE("no-nonce"),

        /** The committed target does not cover the work the shape owes. */
        UNDER_COMMITTED("under-committed"),

        /** The id does not carry the zeros its own commitment promised. */
        UNPAID("unpaid"),
    }
}
