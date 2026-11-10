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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.BOB
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.event
import com.vitorpamplona.amethyst.commons.frontpage.Fixtures.id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors Brainstorm's validate.test.ts: the cases an injected or careless page tries. */
class JsoupPaperBoundaryTest {
    private val note = event(1, content = "The relay answered in three seconds flat. Then it went quiet for an hour.")
    private val other = event(2, content = "Bring a jacket to the river meetup.", pubkey = BOB)
    private val picture = event(3, 20, content = "Sunset", tags = arrayOf(arrayOf("imeta", "url https://img.example/sun.jpg", "m image/jpeg")))
    private val stream = event(4, 30311, tags = arrayOf(arrayOf("d", "show"), arrayOf("status", "live")))
    private val meetup = event(5, 31923, tags = arrayOf(arrayOf("d", "meetup"), arrayOf("title", "River meetup")))
    private val corpus = Fixtures.corpus(mapOf("notes" to listOf(note, other), "pictures" to listOf(picture), "live" to listOf(stream), "calendar" to listOf(meetup)))

    private fun page(body: String) = "<!doctype html><html><head><title>t</title></head><body>$body</body></html>"

    private fun run(body: String) = JsoupPaperBoundary.resolveAndCheck(page(body), corpus)

    private fun kinds(body: String) = run(body).violations.map { it.kind }

    @Test
    fun verbatimQuotesPassAndInventedOnesDoNot() {
        assertEquals(emptyList(), kinds("<q>The relay answered in three seconds flat</q>"))
        assertEquals(listOf(Violation.Kind.QUOTE), kinds("<blockquote>The relay never answered</blockquote>"))
    }

    @Test
    fun elisionKeepsOrderAndStaysInOneEvent() {
        assertEquals(emptyList(), kinds("<q>the relay answered … went quiet for an hour</q>"))
        assertEquals(listOf(Violation.Kind.QUOTE), kinds("<q>went quiet for an hour … the relay answered</q>"))
        // Fragments from two people are not one sentence.
        assertEquals(listOf(Violation.Kind.QUOTE), kinds("<q>three seconds flat … bring a jacket</q>"))
    }

    @Test
    fun artIdsResolveAndUnknownOnesLoseTheirFigure() {
        val checked = run("<figure><img src=\"art-1\" alt=\"a\"></figure><figure><img src=\"art-9\" alt=\"b\"><figcaption>gone</figcaption></figure>")
        assertTrue(checked.violations.isEmpty())
        assertTrue(checked.html.contains("src=\"https://img.example/sun.jpg\""))
        assertFalse(checked.html.contains("gone"))
        assertTrue(checked.changes.any { it.kind == ResolveChange.Kind.DROPPED && it.noteworthy })
    }

    @Test
    fun aWrittenImageUrlIsRefused() {
        assertEquals(listOf(Violation.Kind.IMAGE), kinds("<img src=\"https://evil.example/x.png\" alt=\"x\">"))
    }

    @Test
    fun srcsetAndCssUrlsMustBeShortlistArt() {
        assertTrue(Violation.Kind.IMAGE in kinds("<img src=\"art-1\" srcset=\"https://evil.example/x.png 2x\" alt=\"x\">"))
        assertEquals(listOf(Violation.Kind.IMAGE), kinds("<div style=\"background:url(https://evil.example/x.png)\">x</div>"))
        assertEquals(emptyList(), kinds("<div style=\"background:url('https://img.example/sun.jpg')\">x</div>"))
    }

    @Test
    fun markupThatRunsOrCollectsIsRefused() {
        assertTrue(kinds("<script>alert(1)</script>").all { it == Violation.Kind.MARKUP })
        assertEquals(listOf(Violation.Kind.MARKUP), kinds("<p onclick=\"x()\">x</p>"))
        assertEquals(listOf(Violation.Kind.MARKUP), kinds("<iframe src=\"https://x.example\"></iframe>"))
        // Prose that merely looks like a handler is not one.
        assertEquals(emptyList(), kinds("<p>we ran it once=twice</p>"))
    }

    @Test
    fun citationsBecomePermalinksAndTheOpenWebIsUnwrapped() {
        val checked = run("<a href=\"https://jumble.social/notes/${id(1)}\">Read</a> <a href=\"https://evil.example/drain\">click</a>")
        assertTrue(checked.violations.isEmpty())
        assertTrue(checked.html.contains("https://jumble.social/notes/${toNevent(id(1))}"))
        assertTrue(checked.html.contains("target=\"_blank\""))
        assertFalse(checked.html.contains("evil.example/drain\""))
        assertTrue(checked.html.contains("click"))
        assertTrue(checked.changes.any { it.kind == ResolveChange.Kind.UNWRAPPED })
    }

    @Test
    fun streamsAndCalendarListingsGetTheirHostsNaddr() {
        val checked = run("<a href=\"https://zap.stream/stream/${id(4)}\">Watch</a> <a href=\"https://jumble.social/notes/${id(5)}\">Meetup</a>")
        assertTrue(checked.violations.isEmpty())
        assertTrue(checked.html.contains("https://zap.stream/naddr1"))
        assertTrue(checked.html.contains("https://njump.me/naddr1"))
    }

    @Test
    fun aBylineLinksItsAuthorByOneOfTheirPosts() {
        // The digest gives event ids, never keys: the writer names Bob by his post, resolve finds Bob.
        val checked = run("<a href=\"https://jumble.social/users/${id(2)}\">Bob</a>")
        assertTrue(checked.violations.isEmpty())
        assertTrue(checked.html.contains("https://jumble.social/users/${npubOf(BOB)}"))
        assertTrue(checked.changes.any { it.kind == ResolveChange.Kind.PROFILE })
    }

    @Test
    fun onlyAuthorsThisEditionReadMayBeLinked() {
        val stranger = "3333333333333333333333333333333333333333333333333333333333333333"
        // A stranger named by key, or by npub, is unwrapped: being named in a post is evidence of nothing.
        val checked = run("<a href=\"https://jumble.social/users/$stranger\">x</a> <a href=\"https://jumble.social/users/${npubOf(stranger)}\">y</a>")
        assertTrue(checked.violations.isEmpty())
        assertFalse(checked.html.contains("jumble.social/users/"))
        assertEquals(2, checked.changes.count { it.kind == ResolveChange.Kind.UNWRAPPED })
        // And the check alone refuses one, should resolve ever let it through.
        val doc = JsoupPaperBoundary.parsePage(page("<a href=\"https://jumble.social/users/${npubOf(stranger)}\">x</a>"))
        assertEquals(listOf(Violation.Kind.LINK), JsoupPaperBoundary.check(doc, corpus).violations.map { it.kind })
    }

    @Test
    fun theCheckAloneRefusesALinkResolveWouldHaveUnwrapped() {
        // If resolve ever regressed, the boundary still fails closed.
        val doc = JsoupPaperBoundary.parsePage(page("<a href=\"https://evil.example/x\">x</a>"))
        assertEquals(listOf(Violation.Kind.LINK), JsoupPaperBoundary.check(doc, corpus).violations.map { it.kind })
    }
}
