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
package com.vitorpamplona.amethyst.commons.preview

import com.vitorpamplona.amethyst.commons.platformcompat.PlatformCharset
import com.vitorpamplona.amethyst.commons.platformcompat.decodeBytes

object HtmlCharsetParser {
    val ATTRIBUTE_VALUE_CHARSET = "charset"
    val ATTRIBUTE_VALUE_HTTP_EQUIV = "http-equiv"
    val CONTENT = "content"

    private val RE_CONTENT_TYPE_CHARSET = Regex("""charset=([^;]+)""")

    fun detectCharset(bodyBytes: ByteArray): PlatformCharset {
        // try to detect charset from meta tags parsed from first 1024 bytes of body
        val firstPart = decodeBytes(bodyBytes, 0, minOf(bodyBytes.size, 1024), PlatformCharset.forName("utf-8"))
        val metaTags = MetaTagsParser.parse(firstPart)
        metaTags.forEach { meta ->
            val charsetAttr = meta.attr(ATTRIBUTE_VALUE_CHARSET)
            if (charsetAttr.isNotEmpty()) {
                runCatching { PlatformCharset.forName(charsetAttr) }.getOrNull()?.let {
                    return it
                }
            }
            if (meta.attr(ATTRIBUTE_VALUE_HTTP_EQUIV).lowercase() == "content-type") {
                RE_CONTENT_TYPE_CHARSET
                    .find(meta.attr(CONTENT))
                    ?.let {
                        runCatching { PlatformCharset.forName(it.groupValues[1]) }.getOrNull()
                    }?.let {
                        return it
                    }
            }
        }
        // defaults to UTF-8
        return PlatformCharset.forName("utf-8")
    }
}
