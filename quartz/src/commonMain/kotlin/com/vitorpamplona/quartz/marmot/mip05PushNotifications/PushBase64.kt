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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Standard base64 with padding — the encoding push uses for an `EncryptedToken`
 * and for the kind `446` trigger content.
 *
 * Wrapped rather than called directly so that "standard, padded, and it either
 * decodes or the datum is dropped" is stated once. Everything push decodes is
 * advisory: a bad entry is discarded, and nothing about it may reach the
 * validity of the group message that carried it.
 */
@OptIn(ExperimentalEncodingApi::class)
object PushBase64 {
    fun encode(bytes: ByteArray): String = Base64.encode(bytes)

    /** Null when [text] is not valid standard base64. */
    fun decodeOrNull(text: String): ByteArray? =
        try {
            Base64.decode(text)
        } catch (_: Exception) {
            null
        }
}
