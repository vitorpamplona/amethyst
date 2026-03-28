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
package com.vitorpamplona.quartz.nip96FileStorage.info

actual fun makeAbsoluteIfRelativeUrl(
    baseUrl: String,
    potentiallyRelativeUrl: String,
): String =
    try {
        if (potentiallyRelativeUrl.contains("://")) {
            potentiallyRelativeUrl
        } else {
            val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            if (potentiallyRelativeUrl.startsWith("/")) {
                // Absolute path - combine with scheme + host from base
                val schemeEnd = base.indexOf("://")
                if (schemeEnd >= 0) {
                    val hostEnd = base.indexOf('/', schemeEnd + 3)
                    if (hostEnd >= 0) {
                        base.substring(0, hostEnd) + potentiallyRelativeUrl
                    } else {
                        base + potentiallyRelativeUrl.removePrefix("/")
                    }
                } else {
                    potentiallyRelativeUrl
                }
            } else {
                // Relative path
                base + potentiallyRelativeUrl
            }
        }
    } catch (e: Exception) {
        potentiallyRelativeUrl
    }
