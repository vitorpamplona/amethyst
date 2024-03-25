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
package com.vitorpamplona.amethyst.service.previews

import com.vitorpamplona.amethyst.service.HttpClientManager
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import okio.Options
import java.nio.charset.Charset

private const val ATTRIBUTE_VALUE_PROPERTY = "property"
private const val ATTRIBUTE_VALUE_NAME = "name"
private const val ATTRIBUTE_VALUE_ITEMPROP = "itemprop"
private const val ATTRIBUTE_VALUE_CHARSET = "charset"
private const val ATTRIBUTE_VALUE_HTTP_EQUIV = "http-equiv"

// for <meta itemprop=... to get title
private val META_X_TITLE =
    arrayOf(
        "og:title",
        "twitter:title",
        "title",
    )

// for <meta itemprop=... to get description
private val META_X_DESCRIPTION =
    arrayOf(
        "og:description",
        "twitter:description",
        "description",
    )

// for <meta itemprop=... to get image
private val META_X_IMAGE =
    arrayOf(
        "og:image",
        "twitter:image",
        "image",
    )

private const val CONTENT = "content"

suspend fun getDocument(
    url: String,
    timeOut: Int = 30000,
): UrlInfoItem =
    withContext(Dispatchers.IO) {
        val request: Request = Request.Builder().url(url).get().build()
        HttpClientManager.getHttpClient().newCall(request).execute().use {
            checkNotInMainThread()
            if (it.isSuccessful) {
                val mimeType =
                    it.headers["Content-Type"]?.toMediaType()
                        ?: throw IllegalArgumentException(
                            "Website returned unknown mimetype: ${it.headers["Content-Type"]}",
                        )
                if (mimeType.type == "text" && mimeType.subtype == "html") {
                    parseHtml(url, it.body, mimeType)
                } else if (mimeType.type == "image") {
                    UrlInfoItem(url, image = url, mimeType = mimeType)
                } else if (mimeType.type == "video") {
                    UrlInfoItem(url, image = url, mimeType = mimeType)
                } else {
                    throw IllegalArgumentException(
                        "Website returned unknown encoding for previews: $mimeType",
                    )
                }
            } else {
                throw IllegalArgumentException("Website returned: " + it.code)
            }
        }
    }

suspend fun parseHtml(
    url: String,
    body: ResponseBody,
    type: MediaType,
): UrlInfoItem =
    withContext(Dispatchers.IO) {
        val source = body.source()

        // sniff charset from Content-Type header or BOM
        val sniffedCharset = type.charset() ?: source.readBomAsCharset()
        if (sniffedCharset != null) {
            val metaTags = MetaTagsParser.parse(source.readByteArray().toString(sniffedCharset).headTagContents())
            return@withContext parseUrlInfo(url, metaTags, type)
        }

        // if sniffing was failed, detect charset from content
        val bodyBytes = source.readByteArray()
        val charset = detectCharset(bodyBytes)
        val metaTags = MetaTagsParser.parse(bodyBytes.toString(charset).headTagContents())
        return@withContext parseUrlInfo(url, metaTags, type)
    }

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

private fun BufferedSource.readBomAsCharset(): Charset? {
    return when (select(UNICODE_BOMS)) {
        0 -> Charsets.UTF_8
        1 -> Charsets.UTF_16BE
        2 -> Charsets.UTF_16LE
        3 -> Charsets.UTF_32BE
        4 -> Charsets.UTF_32LE
        -1 -> null
        else -> throw AssertionError()
    }
}

private val RE_CONTENT_TYPE_CHARSET = Regex("""charset=([^;]+)""")

private fun detectCharset(bodyBytes: ByteArray): Charset {
    // try to detect charset from meta tags parsed from first 1024 bytes of body
    val firstPart = String(bodyBytes, 0, 1024, Charset.forName("utf-8"))
    val metaTags = runCatching { MetaTagsParser.parse(firstPart) }.getOrDefault(emptySequence())
    metaTags.forEach { meta ->
        val charsetAttr = meta.attr(ATTRIBUTE_VALUE_CHARSET)
        if (charsetAttr.isNotEmpty()) {
            runCatching { Charset.forName(charsetAttr) }.getOrNull()?.let {
                return it
            }
        }
        if (meta.attr(ATTRIBUTE_VALUE_HTTP_EQUIV).lowercase() == "content-type") {
            RE_CONTENT_TYPE_CHARSET.find(meta.attr(CONTENT))
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

private fun parseUrlInfo(
    url: String,
    metaTags: Sequence<MetaTag>,
    type: MediaType,
): UrlInfoItem {
    var title: String = ""
    var description: String = ""
    var image: String = ""

    metaTags.forEach {
        when (it.attr(ATTRIBUTE_VALUE_PROPERTY)) {
            in META_X_TITLE ->
                if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }

            in META_X_DESCRIPTION ->
                if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }

            in META_X_IMAGE ->
                if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
        }

        when (it.attr(ATTRIBUTE_VALUE_NAME)) {
            in META_X_TITLE ->
                if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }

            in META_X_DESCRIPTION ->
                if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }

            in META_X_IMAGE ->
                if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
        }

        when (it.attr(ATTRIBUTE_VALUE_ITEMPROP)) {
            in META_X_TITLE ->
                if (title.isEmpty()) {
                    title = it.attr(CONTENT)
                }

            in META_X_DESCRIPTION ->
                if (description.isEmpty()) {
                    description = it.attr(CONTENT)
                }

            in META_X_IMAGE ->
                if (image.isEmpty()) {
                    image = it.attr(CONTENT)
                }
        }

        if (title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()) {
            return UrlInfoItem(url, title, description, image, type)
        }
    }
    return UrlInfoItem(url, title, description, image, type)
}

// HTML parsing stuff
private val RE_HEAD = Regex("""<head\s*>(.*?)</head\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun String.headTagContents(): String = RE_HEAD.find(this)?.groupValues?.get(1) ?: ""

private class MetaTag(private val attrs: Map<String, String>) {
    fun attr(name: String): String = attrs[name.lowercase()] ?: ""
}

// map of HTML element attribute name to its value, with additional logics:
// - attribute names are matched in a case-insensitive manner
// - attribute names never duplicate
// - commonly used character references in attribute values are resolved
private class Attrs {
    companion object {
        val RE_CHAR_REF = Regex("""&(\w+)(;?)""")
        val BASE_CHAR_REFS =
            mapOf(
                "amp" to "&",
                "AMP" to "&",
                "quot" to "\"",
                "QUOT" to "\"",
                "lt" to "<",
                "LT" to "<",
                "gt" to ">",
                "GT" to ">",
            )
        val CHAR_REFS =
            mapOf(
                "apos" to "'",
                "equals" to "=",
                "grave" to "`",
                "DiacriticalGrave" to "`",
            )

        fun replaceCharRefs(match: MatchResult): String {
            val bcr = BASE_CHAR_REFS[match.groupValues[1]]
            if (bcr != null) {
                return bcr
            }
            // non-base char refs must be terminated by ';'
            if (match.groupValues[2].isNotEmpty()) {
                val cr = CHAR_REFS[match.groupValues[1]]
                if (cr != null) {
                    return cr
                }
            }
            return match.value
        }
    }

    private val attrs = mutableMapOf<String, String>()

    fun add(attr: Pair<String, String>) {
        val name = attr.first.lowercase()
        if (attrs.containsKey(name)) {
            throw IllegalArgumentException("duplicated attribute name: $name")
        }
        val value = attr.second.replace(RE_CHAR_REF, Attrs::replaceCharRefs)
        attrs += Pair(name, value)
    }

    fun freeze(): Map<String, String> = attrs.toImmutableMap()
}

// parser for parsing a partial HTML document into meta tags
private object MetaTagsParser {
    private val RE_META = Regex("""<meta\s+(.+?)\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val NON_ATTR_NAME_CHARS = setOf(Char(0x0), '"', '\'', '>', '/')
    private val NON_UNQUOTED_ATTR_VALUE_CHARS = setOf('"', '\'', '=', '>', '<', '`')

    fun parse(input: String): Sequence<MetaTag> =
        RE_META.findAll(input).mapNotNull {
            runCatching { MetaTag(parseAttrs(it.groupValues[1])) }.getOrNull()
        }

    private enum class State {
        NAME,
        BEFORE_EQ,
        AFTER_EQ,
        VALUE,
        SPACE,
    }

    private fun parseAttrs(input: String): Map<String, String> {
        val attrs = Attrs()

        var state = State.NAME
        var nameBegin = 0
        var nameEnd = 0
        var valueBegin = 0
        var valueQuote: Char? = null

        input.forEachIndexed { i, c ->
            when (state) {
                State.NAME -> {
                    when {
                        c == '=' -> {
                            nameEnd = i
                            state = State.AFTER_EQ
                        }

                        c.isWhitespace() -> {
                            nameEnd = i
                            state = State.BEFORE_EQ
                        }

                        NON_ATTR_NAME_CHARS.contains(c) || c.isISOControl() || !c.isDefined() -> {
                            throw IllegalArgumentException("meta has invalid attributes part")
                        }
                    }
                }

                State.BEFORE_EQ -> {
                    when {
                        c == '=' -> {
                            state = State.AFTER_EQ
                        }

                        c.isWhitespace() -> {}
                        else -> throw IllegalArgumentException("meta has invalid attributes part")
                    }
                }

                State.AFTER_EQ -> {
                    when {
                        c.isWhitespace() -> {}
                        c == '\'' || c == '"' -> {
                            valueBegin = i + 1
                            valueQuote = c
                            state = State.VALUE
                        }

                        else -> {
                            valueBegin = i
                            valueQuote = null
                            state = State.VALUE
                        }
                    }
                }

                State.VALUE -> {
                    var attr: Pair<String, String>? = null
                    when {
                        valueQuote != null -> {
                            if (c == valueQuote) {
                                attr =
                                    Pair(
                                        input.slice(nameBegin until nameEnd),
                                        input.slice(valueBegin until i),
                                    )
                            }
                        }

                        valueQuote == null -> {
                            when {
                                c.isWhitespace() -> {
                                    attr =
                                        Pair(
                                            input.slice(nameBegin until nameEnd),
                                            input.slice(valueBegin until i),
                                        )
                                }

                                i == input.length - 1 -> {
                                    attr =
                                        Pair(
                                            input.slice(nameBegin until nameEnd),
                                            input.slice(valueBegin..i),
                                        )
                                }

                                NON_UNQUOTED_ATTR_VALUE_CHARS.contains(c) -> {
                                    throw IllegalArgumentException("meta has invalid attributes part")
                                }
                            }
                        }
                    }
                    if (attr != null) {
                        attrs.add(attr)
                        state = State.SPACE
                    }
                }

                State.SPACE -> {
                    if (!c.isWhitespace()) {
                        nameBegin = i
                        state = State.NAME
                    }
                }
            }
        }
        return attrs.freeze()
    }
}
