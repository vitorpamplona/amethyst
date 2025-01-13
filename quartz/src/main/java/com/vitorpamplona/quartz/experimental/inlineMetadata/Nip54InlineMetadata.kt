/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.inlineMetadata

import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

class Nip54InlineMetadata {
    fun createUrl(header: IMetaTag): String =
        createUrl(
            header.url,
            header.properties,
        )

    fun createUrl(
        url: String,
        tags: Map<String, String>,
    ): String {
        val extension =
            tags
                .mapNotNull {
                    if (it.key != "url") {
                        "${it.key}=${URLEncoder.encode(it.value, "utf-8")}"
                    } else {
                        null
                    }
                }.joinToString("&")

        return if (url.contains("#")) {
            "$url&$extension"
        } else {
            "$url#$extension"
        }
    }

    fun parse(url: String): Map<String, String> =
        try {
            fragments(URI(url))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyMap()
        }

    private fun fragments(uri: URI): Map<String, String> {
        if (uri.rawFragment == null) return emptyMap()
        return uri.rawFragment.split('&').associate { keyValuePair ->
            val parts = keyValuePair.split('=')
            val name = parts.firstOrNull() ?: ""
            val value = parts.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
            Pair(name, value)
        }
    }
}
