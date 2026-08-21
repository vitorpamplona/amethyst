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
 * Comments, declarations and script bodies are not element markup. Scanning them for
 * attribute quotes lets an odd apostrophe -- "we don't" is enough -- leave the scanner
 * inside a phantom quoted value, swallowing every tag up to the next quote character.
 * That is what hid the entire og: block of https://brainstorm.world from link previews.
 */
class MetaTagsParserCommentTest {
    @Test
    fun commentWithUnbalancedApostropheDoesNotSwallowFollowingMetaTags() {
        val input =
            """
            |<html><head>
            |  <!-- Fresh visitors stay LIGHT -- we don't auto-dark a dark-OS visitor.
            |       Honors 'dark' and 'system'. -->
            |  <meta property="og:title" content="Brainstorm">
            |  <meta property="og:image" content="https://example.com/og-image.png">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(2, metaTags.size)
        assertEquals("Brainstorm", metaTags[0].attr("content"))
        assertEquals("https://example.com/og-image.png", metaTags[1].attr("content"))
    }

    @Test
    fun metaTagsInsideCommentsAreNotParsed() {
        val input =
            """
            |<html><head>
            |  <!-- <meta property="og:title" content="Commented Out"> -->
            |  <meta property="og:title" content="Real Title">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(1, metaTags.size)
        assertEquals("Real Title", metaTags[0].attr("content"))
    }

    @Test
    fun scriptBodyDoesNotSwallowFollowingMetaTags() {
        val input =
            """
            |<html><head>
            |  <script>
            |    var t = localStorage.getItem('theme');
            |    for (var i = 0; i < 3; i++) { console.log("<meta property=\"og:title\" content=\"Fake\">"); }
            |  </script>
            |  <meta property="og:title" content="Real Title">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(1, metaTags.size)
        assertEquals("Real Title", metaTags[0].attr("content"))
    }

    @Test
    fun styleBodyDoesNotSwallowFollowingMetaTags() {
        val input =
            """
            |<html><head>
            |  <style>body { font-family: 'Figtree', sans-serif; }</style>
            |  <meta property="og:title" content="Real Title">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(1, metaTags.size)
        assertEquals("Real Title", metaTags[0].attr("content"))
    }

    @Test
    fun doctypeAndProcessingInstructionsAreSkipped() {
        val input =
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN">
            |<html><head>
            |  <meta property="og:title" content="Real Title">
            |</head></html>
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(1, metaTags.size)
        assertEquals("Real Title", metaTags[0].attr("content"))
    }

    @Test
    fun unterminatedCommentEndsTheDocument() {
        val input =
            """
            |<html><head>
            |  <meta property="og:title" content="Real Title">
            |  <!-- truncated download cuts the comment here
            """.trimMargin()

        val metaTags = MetaTagsParser.parse(input).toList()

        assertEquals(1, metaTags.size)
        assertEquals("Real Title", metaTags[0].attr("content"))
    }

    @Test
    fun extractsOpenGraphFromAHeadWithCommentsAndScripts() {
        // Shape of https://brainstorm.world/ (any /p/<npub> route serves the same index.html).
        val input =
            """
            |<!DOCTYPE html>
            |<html lang="en">
            |  <head>
            |    <meta charset="UTF-8" />
            |    <!-- No-flash theme: set class="dark" synchronously before first paint.
            |         Fresh visitors (no stored choice) stay LIGHT -- we don't auto-dark a
            |         dark-OS visitor. When ready, change to: 't === dark || (!t || t === system)'. -->
            |    <script>
            |      (function () {
            |        var t = localStorage.getItem("brainstorm_theme");
            |        if (t === "dark") document.documentElement.classList.add("dark");
            |      })();
            |    </script>
            |    <title>Brainstorm - Web of Trust for Nostr</title>
            |    <meta property="og:title" content="Brainstorm - Your Network. Your Rules." />
            |    <meta property="og:description" content="The decentralized Web of Trust layer for Nostr." />
            |    <meta property="og:image" content="https://brainstorm.nosfabrica.com/og-image.png" />
            |  </head>
            |  <body><div id="root"></div></body>
            |</html>
            """.trimMargin()

        val info = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(input))

        assertEquals("Brainstorm - Your Network. Your Rules.", info.title)
        assertEquals("The decentralized Web of Trust layer for Nostr.", info.description)
        assertEquals("https://brainstorm.nosfabrica.com/og-image.png", info.image)
    }
}
