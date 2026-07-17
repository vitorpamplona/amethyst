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
import com.vitorpamplona.quartz.utils.Rfc3986

/**
 * Endpoint helpers for the Blossom HTTP API (BUD-01 … BUD-12). Centralizes the
 * protocol's URL shapes and well-known header names so every transport — the
 * commons JVM `BlossomClient`, the Android uploader, the CLI — builds them the
 * same way instead of re-deriving `<server>/upload` and `X-Reason` by hand.
 */
object BlossomServerUrl {
    /** BUD-01 upload endpoint path: `PUT <server>/upload`. */
    const val UPLOAD_PATH = "/upload"

    /** BUD-04 mirror endpoint path: `PUT <server>/mirror`. */
    const val MIRROR_PATH = "/mirror"

    /** BUD-05 media-optimization endpoint path: `PUT <server>/media`. */
    const val MEDIA_PATH = "/media"

    /** BUD-02 list endpoint path prefix: `GET <server>/list/<pubkey>`. */
    const val LIST_PATH = "/list/"

    /** BUD-09 report endpoint path: `PUT <server>/report`. */
    const val REPORT_PATH = "/report"

    /**
     * Header a Blossom server SHOULD set with a human-readable failure reason on
     * a non-2xx response (BUD-01).
     */
    const val REASON_HEADER = "X-Reason"

    /** BUD-06 preflight request headers for `HEAD /upload` and `HEAD /media`. */
    const val X_SHA_256_HEADER = "X-SHA-256"
    const val X_CONTENT_TYPE_HEADER = "X-Content-Type"
    const val X_CONTENT_LENGTH_HEADER = "X-Content-Length"

    /** BUD-07 payment headers carried on a `402 Payment Required` response. */
    const val X_CASHU_HEADER = "X-Cashu"
    const val X_LIGHTNING_HEADER = "X-Lightning"

    /** `<server>/upload`, collapsing any trailing slash on [serverBaseUrl]. */
    fun upload(serverBaseUrl: String): String = serverBaseUrl.removeSuffix("/") + UPLOAD_PATH

    /** BUD-04 `<server>/mirror`, collapsing any trailing slash on [serverBaseUrl]. */
    fun mirror(serverBaseUrl: String): String = serverBaseUrl.removeSuffix("/") + MIRROR_PATH

    /** BUD-05 `<server>/media`, collapsing any trailing slash on [serverBaseUrl]. */
    fun media(serverBaseUrl: String): String = serverBaseUrl.removeSuffix("/") + MEDIA_PATH

    /** BUD-02 `<server>/list/<pubkey>`, collapsing any trailing slash on [serverBaseUrl]. */
    fun list(
        serverBaseUrl: String,
        pubkey: HexKey,
    ): String = serverBaseUrl.removeSuffix("/") + LIST_PATH + pubkey

    /** BUD-09 `<server>/report`, collapsing any trailing slash on [serverBaseUrl]. */
    fun report(serverBaseUrl: String): String = serverBaseUrl.removeSuffix("/") + REPORT_PATH

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

    /**
     * The lowercase bare domain of [serverBaseUrl], as required by the BUD-11
     * `server` authorization tag ("lowercase domain name only", no scheme/port).
     * Falls back to a best-effort strip when the URL can't be parsed.
     */
    fun domain(serverBaseUrl: String): String =
        try {
            Rfc3986.host(serverBaseUrl).substringBefore(":").lowercase()
        } catch (_: Exception) {
            serverBaseUrl
                .substringAfter("://")
                .substringBefore("/")
                .substringBefore(":")
                .lowercase()
        }
}
