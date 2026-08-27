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
package com.vitorpamplona.amethyst.commons.originless

import kotlinx.serialization.Serializable

/** JSON body from Originless `POST /upload` or `POST /media`. Extra `/media` fields (stripped, anonymized, …) are ignored. */
@Serializable
data class OriginlessUploadResponse(
    val status: String? = null,
    val cid: String? = null,
    val size: Long? = null,
    val type: String? = null,
    val filename: String? = null,
    val pinned: Boolean? = null,
    val error: String? = null,
    val message: String? = null,
) {
    fun requireCid(): String {
        val id = cid?.trim()?.removePrefix("/")?.ifBlank { null }
        if (id.isNullOrBlank()) {
            throw IllegalStateException(errorMessage() ?: "Originless upload did not return a CID")
        }
        return id
    }

    fun isError(): Boolean {
        val statusLower = status?.lowercase()
        return statusLower == "error" || statusLower == "fail" || statusLower == "failed" || !error.isNullOrBlank()
    }

    fun errorMessage(): String? = error?.ifBlank { null } ?: message?.ifBlank { null }
}
