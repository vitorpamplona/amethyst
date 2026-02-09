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
package com.vitorpamplona.quartz.utils

import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem

actual class UriParser actual constructor(
    uri: String,
) {
    private val nsUrlComponents: NSURLComponents? = NSURLComponents(string = uri)

    actual fun scheme(): String? = nsUrlComponents?.scheme

    actual fun host(): String? = nsUrlComponents?.host

    actual fun port(): Int? {
        // The NSNumber?.intValue is a way to handle a nullable port and convert it
        // from a platform-specific number type to a Kotlin Int.
        return nsUrlComponents?.port?.intValue
    }

    actual fun path(): String? = nsUrlComponents?.path

    actual fun queryParameterNames(): Set<String> {
        val queryItems = nsUrlComponents?.queryItems ?: return emptySet()
        return queryItems.mapNotNull { (it as? NSURLQueryItem)?.name }.toSet()
    }

    actual fun getQueryParameter(param: String): String? {
        val queryItems = nsUrlComponents?.queryItems ?: return null
        return (queryItems.firstOrNull { (it as? NSURLQueryItem)?.name == param } as? NSURLQueryItem)?.value
    }

    val fragments: Map<String, String> by lazy {
        nsUrlComponents?.fragment()?.ifBlank { null }?.let { keyValuePair ->
            keyValuePair.split('&').associate { paramValue ->
                val parts = paramValue.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    parts[0] to "" // Handle parameters without a value, e.g., "param&other=value"
                }
            }
        } ?: emptyMap()
    }

    actual fun fragments(): Map<String, String> = fragments
}
