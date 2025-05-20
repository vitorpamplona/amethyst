/**
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
package com.vitorpamplona.quartz.nip73ExternalIds.urls

import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.utils.ensure
import com.vitorpamplona.quartz.utils.toStringNoFragment
import org.czeal.rfc3986.URIReference

class UrlId(
    val url: String,
    val hint: String? = null,
) : ExternalId {
    override fun toScope() = toScope(url)

    override fun toKind() = toKind(url)

    override fun hint() = hint

    companion object {
        const val KIND = "web"
        const val PREFIX1 = "https://"
        const val PREFIX2 = "http://"

        fun toScope(url: String) = URIReference.parse(url).normalize().toStringNoFragment()

        fun toKind(url: String) = KIND

        fun match(
            encoded: String,
            value: String,
        ): Boolean {
            ensure(encoded.startsWith(PREFIX1) || encoded.startsWith(PREFIX2)) { return false }
            return encoded == value
        }

        fun parse(encoded: String): String? {
            ensure(encoded.startsWith(PREFIX1) || encoded.startsWith(PREFIX2)) { return null }
            return encoded
        }
    }
}
