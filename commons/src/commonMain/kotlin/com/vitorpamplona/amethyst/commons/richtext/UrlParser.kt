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

import com.vitorpamplona.quartz.utils.urldetector.Url
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector

class Urls(
    val withScheme: Set<String> = emptySet(),
    val withoutScheme: Set<String> = emptySet(),
    val emails: Set<String> = emptySet(),
    val bech32s: Set<String> = emptySet(),
    val relayUrls: Set<String> = emptySet(),
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

    fun Url.wroteWithSchema(): Boolean = urlMarker.hasScheme()

    fun Url.isEmail(): Boolean =
        urlMarker.hasUsernamePassword() &&
            !urlMarker.hasQuery() &&
            !urlMarker.hasFragment() &&
            originalUrl.contains('@') &&
            path == "/"

    fun parseValidUrls(content: String): Urls {
        val urls = UrlDetector(content).detect()

        val completeUrls = mutableSetOf<String>()
        val urlsWithoutScheme = mutableSetOf<String>()
        val emails = mutableSetOf<String>()
        val bech32 = mutableSetOf<String>()
        val relays = mutableSetOf<String>()

        urls.forEach {
            if (it.isValidTopLevelDomain()) {
                if (it.wroteWithSchema()) {
                    if (it.originalUrl.startsWith("nostr")) {
                        bech32.add(it.originalUrl)
                    } else if (it.originalUrl.startsWith("ws")) {
                        relays.add(it.originalUrl)
                    } else {
                        completeUrls.add(it.originalUrl)
                    }
                } else {
                    // emails are understood as urls from the detector.
                    if (it.isEmail()) {
                        Patterns.EMAIL_ADDRESS.findAll(it.originalUrl).forEach {
                            emails.add(it.value)
                        }
                    } else {
                        urlsWithoutScheme.add(it.originalUrl)
                    }
                }
            }
        }

        return Urls(
            withScheme = completeUrls,
            withoutScheme = urlsWithoutScheme,
            emails = emails,
            bech32s = bech32,
            relayUrls = relays,
        )
    }
}
