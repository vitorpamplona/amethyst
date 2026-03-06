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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser.Companion.noProtocolUrlValidator
import com.vitorpamplona.quartz.utils.urldetector.Url
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector

class Urls(
    val withScheme: Set<String> = emptySet(),
    val withoutScheme: Set<String> = emptySet(),
    val emails: Set<String> = emptySet(),
)

class UrlParser {
    fun Char.isAsciiLetter(): Boolean = (this in 'a'..'z' || this in 'A'..'Z')

    fun Url.isValidTopLevelDomain(): Boolean {
        /*
        According to the TLD Applicant Guidebook published June 2012, ICANN does not allow numbers in TLDs.
         */
        val startOfTopDomain = host.lastIndexOf('.') + 1
        return if (startOfTopDomain < host.length) {
            val topLevelDomain = host.substring(startOfTopDomain)
            topLevelDomain.isNotEmpty() && topLevelDomain[0].isAsciiLetter()
        } else {
            false
        }
    }

    fun Url.wroteWithSchema(): Boolean = originalUrl.startsWith(scheme)

    fun Url.isEmail(): Boolean = originalUrl.contains('@') && path == "/" && query.isEmpty() && fragment.isEmpty()

    fun Char.isValidLastHostnameChar(): Boolean = (this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9')

    fun Url.isValidLastHostnameChar(): Boolean = host[host.length - 1].isValidLastHostnameChar()

    fun Url.endsWithHost(): Boolean = originalUrl.endsWith(host)

    val notAHostNameChar = "[^a-zA-Z0-9.-]".toRegex()

    fun parseValidUrls(content: String): Urls {
        val urls = UrlDetector(content).detect()

        val completeUrls = mutableSetOf<String>()
        val urlsWithoutScheme = mutableSetOf<String>()
        val emails = mutableSetOf<String>()

        println("AABBBCC parseValidUrls ${urls.size}")

        urls.forEach {
            println("AABBBCC Testing ${it.originalUrl}")
            if (it.isValidTopLevelDomain()) {
                if (it.wroteWithSchema()) {
                    if (it.isValidLastHostnameChar()) {
                        completeUrls.add(it.originalUrl)
                    } else if (it.endsWithHost()) {
                        val match = notAHostNameChar.find(it.host)
                        if (match != null) {
                            completeUrls.add(it.originalUrl.substring(0, (it.originalUrl.length - it.host.length) + match.range.first))
                        } else {
                            completeUrls.add(it.originalUrl)
                        }
                    }
                } else {
                    // emails are understood as urls from the detector.
                    if (it.isEmail()) {
                        Patterns.EMAIL_ADDRESS.findAll(it.originalUrl).forEach {
                            emails.add(it.value)
                        }
                    } else {
                        noProtocolUrlValidator.findAll(it.originalUrl).forEach { components ->
                            val url = components.groups[1]?.value
                            if (url != null) {
                                urlsWithoutScheme.add(url)
                            }
                        }
                    }
                }
            }
        }

        return Urls(
            withScheme = completeUrls,
            withoutScheme = urlsWithoutScheme,
            emails = emails,
        )
    }
}
