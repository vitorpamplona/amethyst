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
package com.vitorpamplona.quartz.nipB7Blossom

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Endpoint helpers for the Blossom HTTP API (BUD-01 / BUD-02). Centralizes the
 * protocol's URL shapes and well-known header names so every transport — the
 * commons JVM `BlossomClient`, the Android uploader, the CLI — builds them the
 * same way instead of re-deriving `<server>/upload` and `X-Reason` by hand.
 */
object BlossomServerUrl {
    /** BUD-01 upload endpoint path: `PUT <server>/upload`. */
    const val UPLOAD_PATH = "/upload"

    /**
     * Header a Blossom server SHOULD set with a human-readable failure reason on
     * a non-2xx response (BUD-01).
     */
    const val REASON_HEADER = "X-Reason"

    /** `<server>/upload`, collapsing any trailing slash on [serverBaseUrl]. */
    fun upload(serverBaseUrl: String): String = serverBaseUrl.removeSuffix("/") + UPLOAD_PATH

    /**
     * BUD-01 blob endpoint `<server>/<sha256>[.<ext>]`, used for GET and DELETE.
     * A blank [extension] omits the suffix.
     */
    fun blob(
        serverBaseUrl: String,
        hash: HexKey,
        extension: String = "",
    ): String {
        val suffix = if (extension.isBlank()) "" else ".$extension"
        return serverBaseUrl.removeSuffix("/") + "/" + hash + suffix
    }
}
