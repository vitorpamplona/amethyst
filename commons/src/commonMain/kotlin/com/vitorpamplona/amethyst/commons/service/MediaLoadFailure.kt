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
package com.vitorpamplona.amethyst.commons.service

/**
 * Why a piece of note media did not load, in the terms the user acts on: whether trying again
 * can help, and whose answer it was. Every media surface words a failure from this, so a 404, a
 * dead host and a file that isn't media read differently everywhere they appear.
 */
enum class MediaLoadFailure(
    /** Whether the same request may work later, so the UI offers Retry. */
    val retryable: Boolean,
) {
    /** The server says the file isn't there (404, 410), after every mirror was tried. */
    GONE(retryable = false),

    /** The server could not be reached or broke (DNS, connect, TLS, timeout, 5xx). */
    UNREACHABLE(retryable = true),

    /** The server refuses to serve it (401 after the read-auth retry, 403). */
    REFUSED(retryable = false),

    /** The server wants to be paid first (402). */
    PAYMENT_REQUIRED(retryable = false),

    /** The bytes arrived but are not media the app can draw. */
    NOT_MEDIA(retryable = false),

    /** Anything else. */
    UNKNOWN(retryable = true),
    ;

    companion object {
        /** The cause behind a non-2xx HTTP [status]. */
        fun fromHttpStatus(status: Int): MediaLoadFailure =
            when (status) {
                404, 410 -> GONE
                401, 403, 451 -> REFUSED
                402 -> PAYMENT_REQUIRED
                408, 429 -> UNREACHABLE
                in 500..599 -> UNREACHABLE
                else -> UNKNOWN
            }
    }
}
