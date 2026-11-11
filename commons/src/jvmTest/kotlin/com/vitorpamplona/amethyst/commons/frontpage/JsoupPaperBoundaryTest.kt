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
    fun artIsOnlyEverAnImg() {
        assertTrue(Violation.Kind.MARKUP in kinds("<img src=\"art-1\" srcset=\"https://evil.example/x.png 2x\" alt=\"x\">"))
        assertEquals(listOf(Violation.Kind.IMAGE), kinds("<div style=\"background:url(https://evil.example/x.png)\">x</div>"))
        // v2: not even shortlist art goes through CSS.
        assertEquals(listOf(Violation.Kind.IMAGE), kinds("<div style=\"background:url('https://img.example/sun.jpg')\">x</div>"))
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

    // --- paper boundary v2: every bypass the 2026-11-11 audit found ---------------

    private fun markup(body: String) = kinds(body).filter { it == Violation.Kind.MARKUP }

    @Test
    fun declarativeShadowDomIsRefused() {
        assertTrue(markup("<div><template shadowrootmode=\"open\"><img src=x onerror=alert(1)></template></div>").isNotEmpty())
    }

    @Test
    fun noscriptCommentSmugglingIsRefused() {
        assertTrue(markup("<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\"></p></noscript>").isNotEmpty())
    }

    @Test
    fun svgAndMathLinksAndAnimationsAreRefused() {
        assertTrue(markup("<svg><a href=\"javascript:alert(1)\"><text>x</text></a></svg>").isNotEmpty())
        assertTrue(markup("<svg><set attributeName=\"href\" to=\"javascript:alert(1)\"/></svg>").isNotEmpty())
        assertTrue(markup("<svg><image href=\"https://evil.example/x.png\"/></svg>").isNotEmpty())
        assertTrue(markup("<math><mi href=\"javascript:alert(1)\">x</mi></math>").isNotEmpty())
    }

    @Test
    fun outsideResourcesBeyondImgAreRefused() {
        assertTrue(markup("<link rel=\"stylesheet\" href=\"https://evil.example/x.css\">").isNotEmpty())
        assertTrue(markup("<video poster=\"https://evil.example/x.png\"></video>").isNotEmpty())
        assertTrue(markup("<table background=\"https://evil.example/x.png\"><tr><td>x</td></tr></table>").isNotEmpty())
        assertTrue(markup("<base href=\"https://evil.example/\">").isNotEmpty())
        assertTrue(markup("<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">").isNotEmpty())
        // The model may not write its own CSP either: the app adds it.
        assertTrue(markup("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src *\">").isNotEmpty())
    }

    @Test
    fun urlsAreJudgedAsABrowserCleansThem() {
        for (href in listOf(" javascript:alert(1)", "java\tscript:alert(1)", "//evil.example/x", "https:\\\\evil.example", "\u0001javascript:alert(1)", "data:text/html,x")) {
            val doc = JsoupPaperBoundary.parsePage(page("<a href=\"$href\">x</a>"))
            assertEquals(listOf(Violation.Kind.LINK), JsoupPaperBoundary.check(doc, corpus).violations.map { it.kind }, href)
            // Resolve unwraps every one of them.
            assertFalse(run("<a href=\"$href\">x</a>").html.contains("href="), href)
        }
        // A same-document fragment is fine.
        assertEquals(emptyList(), kinds("<a href=\"#lead\">x</a><h2 id=\"lead\">Lead</h2>"))
        // A shortlist picture behind stray whitespace is still that picture; anything else is not.
        assertEquals(emptyList(), kinds("<img src=\" https://img.example/sun.jpg \" alt=\"x\">"))
    }

    @Test
    fun cssEscapesAndOtherFetchersAreRefused() {
        assertTrue(kinds("<style>@\\69mport 'https://evil.example/x.css';</style>").isNotEmpty())
        assertTrue(kinds("<p style=\"background:\\75rl(https://evil.example/x.png)\">x</p>").isNotEmpty())
        assertTrue(Violation.Kind.IMAGE in kinds("<style>p{background:image-set('https://evil.example/x.png' 1x)}</style>"))
        assertTrue(Violation.Kind.IMAGE in kinds("<style>p{background:-webkit-image-set(\"x.png\" 1x)}</style>"))
        assertTrue(Violation.Kind.IMAGE in kinds("<style>p{background:cross-fade(a, b)}</style>"))
        assertTrue(markup("<style>@font-face{font-family:x}</style>").isNotEmpty())
        assertTrue(markup("<style>@namespace x 'y';</style>").isNotEmpty())
        assertEquals(emptyList(), markup("<style>@media (max-width: 600px) { p { margin: 0 } }</style>"))
    }

    @Test
    fun generatedTextIsRefused() {
        assertTrue(markup("<style>h1::after{content:'Bob resigned'}</style><h1>x</h1>").isNotEmpty())
        assertTrue(markup("<style>q{quotes:'Bob said ' ''}</style><q>The relay answered in three seconds flat</q>").isNotEmpty())
        assertEquals(emptyList(), markup("<style>q{quotes:'\u201c' '\u201d'}</style><q>The relay answered in three seconds flat</q>"))
    }

    @Test
    fun hiddenTextInsideAQuoteIsRefused() {
        // "never" hidden from the reader but counted by the check would let a quote say the opposite.
        assertTrue(markup("<q>The relay <span hidden>never</span> answered</q>").isNotEmpty())
        assertTrue(markup("<q>The relay answered in three seconds flat<em style=\"display:none\">!</em></q>").isNotEmpty())
        assertTrue(markup("<q aria-hidden=\"true\">The relay answered in three seconds flat</q>").isNotEmpty())
        assertTrue(markup("<q dir=\"rtl\">The relay answered in three seconds flat</q>").isNotEmpty())
        assertTrue(markup("<style>blockquote em{display:none}</style><blockquote>The relay answered <em>in</em> three seconds flat</blockquote>").isNotEmpty())
        assertTrue(markup("<style>.x{font-size:0}</style><q>The relay answered in three seconds flat</q><q><em class=\"x\">y</em></q>").isNotEmpty())
        assertTrue(markup("<style>q{height:1px;overflow:hidden}</style><q>The relay answered in three seconds flat</q>").isNotEmpty())
        // Emphasis that only changes how it looks is fine.
        assertEquals(emptyList(), kinds("<style>q em{font-style:italic}</style><q>The relay answered in <em>three seconds</em> flat</q>"))
    }

    @Test
    fun aBylineMaySitInTheQuotesAttribution() {
        val checked = run("<blockquote><p>The relay answered in three seconds flat</p><footer><a href=\"https://jumble.social/users/${id(1)}\">Alice</a></footer></blockquote>")
        assertEquals(emptyList(), checked.violations.map { it.kind })
        // But not elsewhere inside the quote.
        assertTrue(markup("<blockquote><span>The relay answered in three seconds flat</span></blockquote>").isNotEmpty())
    }

    @Test
    fun everyElidedFragmentCountsHoweverShort() {
        // v1 skipped fragments of two characters or fewer: "no" slipped in.
        assertEquals(listOf(Violation.Kind.QUOTE), kinds("<q>the relay answered … no … went quiet</q>"))
    }

    @Test
    fun detailsMayStartOpenAndUnknownAttributesAreRefused() {
        assertEquals(emptyList(), kinds("<details open><summary>More</summary><p>x</p></details>"))
        assertTrue(markup("<p data-x=\"1\">x</p>").isNotEmpty())
        assertTrue(markup("<a href=\"#x\" target=\"_top\">x</a>").isNotEmpty())
        assertTrue(markup("<div ping=\"https://evil.example\">x</div>").isNotEmpty())
    }

    @Test
    fun anUnencodableDeskAddressNeverThrows() {
        val longD = event(9, 30311, tags = arrayOf(arrayOf("d", "x".repeat(300)), arrayOf("status", "live")))
        val big = Fixtures.corpus(mapOf("notes" to listOf(note), "live" to listOf(longD)))
        val checked = JsoupPaperBoundary.resolveAndCheck(page("<a href=\"https://zap.stream/stream/${id(9)}\">Watch</a>"), big)
        assertEquals(emptyList(), checked.violations.map { it.kind })
        assertFalse(checked.html.contains("zap.stream"))
        assertEquals(null, DESK_LINKS.getValue("live").canonicalUrlOrNull(longD))
    }

    @Test
    fun theCheckedPageIsTheSettledOne() {
        // A page that changes when read back is checked in the form it settles into.
        val checked = run("<p>x</p>")
        assertEquals(checked.html, JsoupPaperBoundary.serialize(JsoupPaperBoundary.parsePage(checked.html)))
    }
}
