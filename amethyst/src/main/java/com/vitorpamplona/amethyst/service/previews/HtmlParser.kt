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
package com.vitorpamplona.amethyst.service.previews

import com.vitorpamplona.amethyst.commons.preview.MetaTag
import com.vitorpamplona.amethyst.commons.preview.MetaTagsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import okio.Options
import java.nio.charset.Charset

class HtmlParser {
    companion object {
        val ATTRIBUTE_VALUE_CHARSET = "charset"
        val ATTRIBUTE_VALUE_HTTP_EQUIV = "http-equiv"
        val CONTENT = "content"

        // taken from okhttp
        private val UNICODE_BOMS =
            Options.of(
                // UTF-8
                "efbbbf".decodeHex(),
                // UTF-16BE
                "feff".decodeHex(),
                // UTF-16LE
                "fffe".decodeHex(),
                // UTF-32BE
                "0000ffff".decodeHex(),
                // UTF-32LE
                "ffff0000".decodeHex(),
            )

        private val RE_CONTENT_TYPE_CHARSET = Regex("""charset=([^;]+)""")
    }

    suspend fun parseHtml(
        source: BufferedSource,
        type: Charset?,
    ): Sequence<MetaTag> =
        withContext(Dispatchers.IO) {
            // sniff charset from Content-Type header or BOM
            val sniffedCharset = type ?: source.readBomAsCharset()
            if (sniffedCharset != null) {
                val content = source.readByteArray().toString(sniffedCharset)
                return@withContext MetaTagsParser.parse(content)
            }

            // if sniffing was failed, detect charset from content
            val bodyBytes = source.readByteArray()
            val charset = detectCharset(bodyBytes)
            val content = bodyBytes.toString(charset)
            return@withContext MetaTagsParser.parse(content)
        }

    private fun BufferedSource.readBomAsCharset(): Charset? =
        when (select(UNICODE_BOMS)) {
            0 -> Charsets.UTF_8
            1 -> Charsets.UTF_16BE
            2 -> Charsets.UTF_16LE
            3 -> Charsets.UTF_32BE
            4 -> Charsets.UTF_32LE
            -1 -> null
            else -> throw AssertionError()
        }

    private fun detectCharset(bodyBytes: ByteArray): Charset {
        // try to detect charset from meta tags parsed from first 1024 bytes of body
        val firstPart = String(bodyBytes, 0, 1024, Charset.forName("utf-8"))
        val metaTags = MetaTagsParser.parse(firstPart)
        metaTags.forEach { meta ->
            val charsetAttr = meta.attr(ATTRIBUTE_VALUE_CHARSET)
            if (charsetAttr.isNotEmpty()) {
                runCatching { Charset.forName(charsetAttr) }.getOrNull()?.let {
                    return it
                }
            }
            if (meta.attr(ATTRIBUTE_VALUE_HTTP_EQUIV).lowercase() == "content-type") {
                RE_CONTENT_TYPE_CHARSET
                    .find(meta.attr(CONTENT))
                    ?.let {
                        runCatching { Charset.forName(it.groupValues[1]) }.getOrNull()
                    }?.let {
                        return it
                    }
            }
        }
        // defaults to UTF-8
        return Charset.forName("utf-8")
    }
}
