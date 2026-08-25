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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three attributes a preview can be declared under -- `property` (Open Graph), `name`
 * (Twitter cards and plain HTML) and `itemprop` (schema.org) -- and what happens when a page
 * declares the same thing under more than one.
 */
class OpenGraphParserTest {
    private fun extract(html: String) = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(html))

    @Test
    fun readsOpenGraphProperties() {
        val info =
            extract(
                """
                |<head>
                |  <meta property="og:title" content="T">
                |  <meta property="og:description" content="D">
                |  <meta property="og:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun fallsBackToTwitterCardNames() {
        val info =
            extract(
                """
                |<head>
                |  <meta name="twitter:title" content="T">
                |  <meta name="twitter:description" content="D">
                |  <meta name="twitter:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun fallsBackToPlainNames() {
        val info =
            extract(
                """
                |<head>
                |  <meta name="title" content="T">
                |  <meta name="description" content="D">
                |  <meta name="image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun readsSchemaOrgItemprops() {
        val info =
            extract(
                """
                |<head>
                |  <meta itemprop="name" content="ignored, not a title key">
                |  <meta itemprop="title" content="T">
                |  <meta itemprop="description" content="D">
                |  <meta itemprop="image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("T", info.title)
        assertEquals("D", info.description)
        assertEquals("https://example.com/i.png", info.image)
    }

    @Test
    fun takesEachFieldFromWhicheverTagCarriesItFirst() {
        // NOTE: this is document order, not source priority. A page that puts a plain
        // <meta name="description"> above its <meta property="og:description"> -- a very common
        // CMS layout -- has the plain one win, even though og: is the more specific declaration.
        // Pinned as the current behavior; changing it means preferring og: over name: explicitly.
        val info =
            extract(
                """
                |<head>
                |  <meta name="description" content="plain, comes first">
                |  <meta property="og:description" content="og, comes second">
                |  <meta property="og:title" content="T">
                |  <meta property="og:image" content="https://example.com/i.png">
                |</head>
                """.trimMargin(),
            )

        assertEquals("plain, comes first", info.description)
    }

    @Test
    fun missingFieldsComeBackEmptyRatherThanNull() {
        val info = extract("""<head><meta property="og:title" content="T"></head>""")

        assertEquals("T", info.title)
        assertEquals("", info.description)
        assertEquals("", info.image)
    }

    @Test
    fun ignoresAMetaTagWithNoRecognizedKey() {
        val info = extract("""<head><meta name="viewport" content="width=device-width"></head>""")

        assertEquals("", info.title)
        assertEquals("", info.description)
        assertEquals("", info.image)
    }
}
