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

/*
 * The URL and encoding rules of [NappletResourceFetcher], per platform: OkHttp's `HttpUrl` and
 * `java.util.Base64` on the JVM, exactly as the fetcher always parsed them, and Foundation's on iOS.
 */

/**
 * [url] in canonical form when it is an absolute `https` URL with a host and no user name or
 * password, else null. The canonical form is what goes on the wire.
 */
internal expect fun safeHttpsResourceUrl(url: String): String?

/** [location] (a redirect's `Location`, absolute or relative) resolved against [base], or null. */
internal expect fun resolveResourceRedirect(
    base: String,
    location: String,
): String?

/**
 * The bytes of a `data:` URL's base64 payload, whitespace already removed. Padding is optional; any
 * character outside the alphabet throws.
 */
internal expect fun decodeDataBase64(data: String): ByteArray
