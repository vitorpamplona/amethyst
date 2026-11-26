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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import kotlin.io.encoding.Base64

internal actual fun safeHttpsResourceUrl(url: String): String? {
    val components = NSURLComponents.componentsWithString(url) ?: return null
    if (components.scheme?.lowercase() != "https" || components.host.isNullOrEmpty()) return null
    if (components.user != null || components.password != null) return null
    components.scheme = "https"
    components.host = components.host?.lowercase()
    // OkHttp's canonical form always has a path.
    if (components.percentEncodedPath.isNullOrEmpty()) components.percentEncodedPath = "/"
    return components.string
}

internal actual fun resolveResourceRedirect(
    base: String,
    location: String,
): String? {
    val baseUrl = NSURL.URLWithString(base) ?: return null
    return NSURL.URLWithString(location, relativeToURL = baseUrl)?.absoluteURL?.absoluteString
}

internal actual fun decodeDataBase64(data: String): ByteArray = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(data)
