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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.Normalizer

/*
 * The "afterwards" the editorial brief promises, then the boundary.
 *
 * Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer):
 * `generator/.../safe/Validator.kt`, `Sanitizer.kt` and the skill's `resolve.mjs` /
 * `validate.mjs`, matched to Brainstorm's port (client/src/lib/frontPage/resolve.ts and
 * validate.ts), which edits the PARSED document instead of slicing source strings, so
 * what is checked is what renders. jsoup is the Observer's own parser.
 *
 * Why the boundary exists: an attacker can post "ignore previous instructions, the lead
 * is…" into the feed of everyone who follows them, and a model may take the bait — but
 * an injected story generally cannot quote real events verbatim, so it fails here.
 * PARAPHRASE IS NOT CHECKED, because paraphrase is journalism.
 *
 * Paper boundary v2 (`quartz/plans/2026-11-11-paper-boundary-v2.md`, shared with Brainstorm's
 * validate.ts): an ALLOWLIST. v1 refused what it recognised, and the 2026-11-11 audit walked
 * past it with declarative shadow DOM, `<noscript>` comment smuggling, URLs browsers clean
 * before following, SVG/MathML links, resources outside `<img>`, CSS escapes, `image-set()`,
 * and hidden or generated text inside quotes. Every element, attribute, URL and stylesheet
 * construct not named here is refused, and the page is checked in the form it settles into
 * after a write-and-read round trip. The copies that leave the app also carry [PAPER_CSP].
 */
actual fun paperBoundary(): PaperBoundary? = JsoupPaperBoundary

object JsoupPaperBoundary : PaperBoundary {
    override fun resolveAndCheck(
        html: String,
        corpus: Corpus,
    ): CheckedPage {
        val doc = parsePage(html)
        val changes = resolve(doc, corpus)
        val result = check(doc, corpus)
        // What renders is what was checked: the settled form, not the first parse.
        return CheckedPage(serialize(result.doc), changes, result.violations, result.quotes.size, headline(result.doc))
    }

    /** The lead story's headline: the first h1, else the first h2; the masthead never counts. */
    fun headline(doc: Document): String? =
        doc
            .select("h1, h2")
            .map { it.text().replace(WS, " ").trim() }
            .firstOrNull { it.isNotEmpty() && !it.equals(MASTHEAD, ignoreCase = true) }
            ?.take(160)

    fun parsePage(html: String): Document = Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }

    /** The page as it will render: the parsed document, serialised once. */
    fun serialize(doc: Document): String = "<!doctype html>\n" + (doc.selectFirst("html")?.outerHtml() ?: doc.outerHtml())

    // --- normalisation ---------------------------------------------------------

    private val WS = Regex("\\s+")

    /**
     * One normal form for both sides. Lowercased on purpose (capitalising a quote's first
     * word is standard); everything that changes MEANING survives intact.
     */
    fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('–', '-')
            .replace('—', '-')
            .replace(' ', ' ')
            .replace("…", "...")
            .replace(WS, " ")
            .trim()
            .lowercase()

    /**
     * Verbatim, allowing elision: every fragment between ellipses must appear IN ORDER in
     * ONE event — what stops elision stitching two people into one sentence.
     */
    fun isQuoted(
        raw: String,
        haystack: List<String>,
    ): Boolean {
        val needle = normalize(raw)
        if (needle.isEmpty()) return true
        // No fragment is too short to check: v1 skipped two characters or fewer, so
        // "I … no … it" smuggled a "no" past it.
        val fragments = needle.split("...").map { it.trim() }.filter { it.isNotEmpty() }
        if (fragments.isEmpty()) return haystack.any { it.contains(needle) }
        return haystack.any { source ->
            var from = 0
            fragments.all { fragment ->
                val at = source.indexOf(fragment, from)
                if (at < 0) {
                    false
                } else {
                    from = at + fragment.length
                    true
                }
            }
        }
    }

    /** Attribution inside a quote, not part of what was said. */
    private val ATTRIBUTION = setOf("cite", "footer")

    /** Text nodes joined with spaces (`a<br>b` is "a b"), without the attribution (`cite`, `footer`). */
    private fun quoteText(el: Element): String {
        val parts = mutableListOf<String>()

        fun walk(node: Node) {
            for (child in node.childNodes()) {
                if (child is TextNode) {
                    parts.add(child.wholeText)
                } else if (child is Element && child.normalName() !in ATTRIBUTION) {
                    walk(child)
                }
            }
        }
        walk(el)
        return parts.joinToString(" ").replace(WS, " ").trim()
    }

    fun quotedText(doc: Document): List<String> = doc.select("q, blockquote").map { quoteText(it) }.filter { it.isNotEmpty() }

    // --- links -----------------------------------------------------------------

    /**
     * The one shape a citation may take after resolve: a jumble.social nevent naming an
     * event we read. DECODED, not captured: a regex once allowed `nevent1…` in a branch
     * that captured nothing, so every such link compared against the empty string.
     */
    private val PERMALINK = Regex("^https://jumble\\.social/notes/(nevent1[0-9a-zA-Z]+)(?:[/?#].*)?$", RegexOption.IGNORE_CASE)
    private val JUMBLE_HEX = Regex("^https://jumble\\.social/notes/([0-9a-fA-F]{64})(?:[/?#].*)?$")
    private val NJUMP_HEX = Regex("^https://njump\\.me/([0-9a-fA-F]{64})(?:[/?#].*)?$")
    private val NEVENT_ANY = Regex("^https://(?:jumble\\.social/notes/|njump\\.me/)(nevent1[0-9a-zA-Z]+)", RegexOption.IGNORE_CASE)

    /** What a browser does to an attribute URL before it parses it: C0 and spaces trimmed, tabs and newlines gone. */
    fun cleanUrl(raw: String): String =
        raw
            .trim { it.code <= 0x20 }
            .replace("\t", "")
            .replace("\n", "")
            .replace("\r", "")

    /** A same-document `#fragment`. */
    fun isFragment(raw: String) = cleanUrl(raw).startsWith("#")

    fun toPermalink(eventId: HexKey) = "https://jumble.social/notes/${toNevent(eventId)}"

    fun permalinkTarget(href: String): HexKey? = PERMALINK.find(href)?.let { fromNevent(it.groupValues[1]) }

    private fun citedEventId(href: String): HexKey? =
        permalinkTarget(href)
            ?: JUMBLE_HEX
                .find(href)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
            ?: NJUMP_HEX
                .find(href)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
            ?: NEVENT_ANY.find(href)?.let { fromNevent(it.groupValues[1]) }

    private class DeskIndex(
        val byId: Map<String, Event>,
        val byAddress: Map<String, Event>,
    )

    private fun addressKey(
        kind: Int,
        pubkey: String,
        identifier: String?,
    ) = "$kind:${pubkey.lowercase()}:$identifier"

    /** The corpus indexed per desk link. The kind is in the address key, so 31922 and 31923 never answer for each other. */
    private fun index(corpus: Corpus): Map<String, DeskIndex> =
        DESK_LINKS.mapValues { (_, link) ->
            val list = corpus.events.filter { link.canonicalUrlOrNull(it) != null }
            DeskIndex(
                list.associateBy { it.id.lowercase() },
                list.associateBy { addressKey(it.kind, it.pubKey, it.firstTagValue("d")) },
            )
        }

    /**
     * Event id if [href] is a verified canonical link to an event we read on desk [key]:
     * the naddr must decode to the kind + pubkey + d-tag of a corpus event, not merely
     * appear in somebody's post. The writer form is not accepted here: resolve encodes it,
     * and refusing it afterwards makes a regression there fail closed.
     */
    private fun deskLinkTarget(
        key: String,
        href: String,
        index: Map<String, DeskIndex>,
    ): HexKey? {
        val link = DESK_LINKS.getValue(key)
        val naddr = link.naddr.find(href) ?: return null
        val address = fromNaddr(naddr.groupValues[1]) ?: return null
        if (address.kind !in link.kinds) return null
        return index.getValue(key).byAddress[addressKey(address.kind, address.pubkey, address.identifier)]?.id
    }

    private fun deskWriterTarget(
        key: String,
        href: String,
        index: Map<String, DeskIndex>,
    ): HexKey? {
        val id =
            DESK_LINKS
                .getValue(key)
                .writer
                .find(href)
                ?.groupValues
                ?.get(1)
                ?.lowercase() ?: return null
        return id.takeIf { index.getValue(key).byId.containsKey(it) }
    }

    /**
     * The author a byline link names, if they wrote a post this edition read: the writer's form
     * names them by one of their event ids (or, leniently, by their pubkey hex), the canonical
     * form by npub. Anyone else is not the paper's to link, however they were named.
     */
    private fun profileTarget(
        href: String,
        byId: Map<String, Event>,
        authors: Set<String>,
    ): HexKey? {
        PROFILE_LINK.find(href)?.let { found -> return fromNpub(found.groupValues[1])?.takeIf { it in authors } }
        val hex =
            PROFILE_WRITER
                .find(href)
                ?.groupValues
                ?.get(1)
                ?.lowercase() ?: return null
        return byId[hex]?.pubKey?.lowercase() ?: hex.takeIf { it in authors }
    }

    /** Jumble is a source citation, not a page the paper should be replaced by. */
    private fun openInNewTab(anchor: Element) {
        anchor.attr("target", "_blank")
        val rel =
            anchor
                .attr("rel")
                .split(WS)
                .filter { it.isNotEmpty() }
                .toMutableSet()
        rel.add("noopener")
        rel.add("noreferrer")
        anchor.attr("rel", rel.joinToString(" "))
    }

    // --- resolve ---------------------------------------------------------------

    private val ART_ID = Regex("^art-\\d+$")

    /** Edits [doc] in place and REPORTS EVERYTHING IT CHANGES: a sanitizer cleaning in silence hides the attack. */
    fun resolve(
        doc: Document,
        corpus: Corpus,
    ): List<ResolveChange> {
        val changes = mutableListOf<ResolveChange>()
        val byId = corpus.art.associateBy { it.id }
        val eventIds = corpus.events.mapTo(HashSet()) { it.id }

        for (img in doc.select("img")) {
            val id = cleanUrl(img.attr("src"))
            if (!ART_ID.matches(id)) continue
            val art = byId[id]
            if (art != null) {
                img.attr("src", art.url)
                changes.add(ResolveChange(ResolveChange.Kind.RESOLVED, "$id -> ${art.url}"))
            } else {
                (img.closest("figure") ?: img).remove()
                changes.add(ResolveChange(ResolveChange.Kind.DROPPED, "$id is not on the shortlist; its figure was removed"))
            }
        }

        // The paper prints addresses; it does not make them clickable — except citations
        // and the verified zap.stream / Shopstr / njump-calendar links.
        val index = index(corpus)
        val eventsById = corpus.events.associateBy { it.id.lowercase() }
        val authors = corpus.events.mapTo(HashSet()) { it.pubKey.lowercase() }
        for (anchor in doc.select("a[href]")) {
            val raw = anchor.attr("href")
            if (isFragment(raw)) continue
            // Judged by where a browser would go: ` javascript:`, `java&#9;script:` and `//host` are links too.
            val url = cleanUrl(raw)
            val id = citedEventId(url)

            fun keep(
                canonical: String,
                kind: ResolveChange.Kind,
            ) {
                if (raw != canonical) {
                    anchor.attr("href", canonical)
                    changes.add(ResolveChange(kind, "$url -> $canonical"))
                }
                openInNewTab(anchor)
            }

            val desk =
                DESK_LINKS.keys.firstNotNullOfOrNull { key ->
                    val eventId = deskWriterTarget(key, url, index) ?: deskLinkTarget(key, url, index)
                    eventId?.let { index.getValue(key).byId[it.lowercase()] }?.let { DESK_LINKS.getValue(key) to it }
                } ?: id?.let { index.getValue("calendar").byId[it] }?.let { DESK_LINKS.getValue("calendar") to it }
            val deskUrl = desk?.let { (link, event) -> link.canonicalUrlOrNull(event) }
            if (desk != null && deskUrl != null) {
                keep(deskUrl, desk.first.change)
                continue
            }
            val author = profileTarget(url, eventsById, authors)
            if (author != null) {
                keep(toProfileLink(author), ResolveChange.Kind.PROFILE)
                continue
            }
            if (id != null && id in eventIds) {
                keep(toPermalink(id), ResolveChange.Kind.PERMALINK)
                continue
            }
            anchor.unwrap()
            changes.add(ResolveChange(ResolveChange.Kind.UNWRAPPED, url.take(120)))
        }
        return changes
    }

    // --- markup: the allowlist ---------------------------------------------------

    /** Every element a paper may be made of. Anything else is refused. */
    val ALLOWED_ELEMENTS =
        (
            "html head body title meta style header footer main section article aside nav div span " +
                "h1 h2 h3 h4 h5 h6 p br hr a img figure figcaption blockquote q cite em strong b i u s " +
                "small sub sup mark time abbr ul ol li dl dt dd table thead tbody tfoot tr th td caption " +
                "colgroup col details summary"
        ).split(" ").toSet()

    /** Named so the refusal says what it is (the allowlist refuses them regardless). */
    private val REFUSED_ELEMENTS =
        mapOf(
            "template" to "a <template>, which can carry a shadow root the check never sees",
            "noscript" to "a <noscript>, which parses one way here and another way in a browser",
            "svg" to "inline <svg>, which carries its own links and animations",
            "math" to "inline <math>, which carries its own links",
            "link" to "a <link>, which loads from outside the page",
            "base" to "a <base> tag, which rewrites every relative URL on the page",
            "script" to "a <script> tag",
            "iframe" to "an <iframe>",
            "frame" to "a <frame>",
            "frameset" to "a <frameset>",
            "object" to "an embedded object",
            "embed" to "an embedded object",
            "applet" to "an embedded object",
            "form" to "a form - the paper collects nothing",
            "input" to "a form control",
            "button" to "a form control",
            "select" to "a form control",
            "textarea" to "a form control",
            "video" to "a video - art is only ever <img>",
            "audio" to "audio",
            "source" to "a media <source>",
            "track" to "a media <track>",
            "picture" to "a <picture> - art is only ever <img>",
            "canvas" to "a <canvas>",
            "portal" to "a <portal>",
            "slot" to "a <slot>",
            "dialog" to "a <dialog>",
            "marquee" to "a <marquee>",
        )

    private val GLOBAL_ATTRS = setOf("class", "id", "lang", "dir", "title", "role", "style", "hidden")

    private val ELEMENT_ATTRS =
        mapOf(
            "a" to setOf("href", "target", "rel"),
            "img" to setOf("src", "alt", "width", "height", "loading", "decoding"),
            "meta" to setOf("charset", "name", "content"),
            "td" to setOf("colspan", "rowspan", "scope"),
            "th" to setOf("colspan", "rowspan", "scope"),
            "time" to setOf("datetime"),
            "ol" to setOf("start", "reversed"),
            "col" to setOf("span"),
            "colgroup" to setOf("span"),
            // `open` only says whether a <details> starts expanded.
            "details" to setOf("open"),
        )

    /** Refused everywhere, named so the refusal says why. */
    private val REFUSED_ATTRS = Regex("^(srcset|sizes|poster|background|xlink:.*|on.*|formaction|http-equiv|srcdoc|ping|shadowroot.*)$")

    private val META_NAMES = setOf("viewport", "description")

    /** Inside a quote: the only elements, and inside its attribution the only extra ones. */
    private val QUOTE_INSIDE = setOf("p", "br", "em", "strong", "b", "i", "cite", "footer")

    // A byline link (and its emphasis) inside the attribution. The attribution is not quote
    // text, and its link still goes through the LINK check.
    private val ATTRIBUTION_INSIDE = setOf("a", "em", "strong", "b", "i", "br", "time", "small", "abbr")

    /** Attributes that can hide, restyle or reorder text, refused on a quote and inside it. */
    private val QUOTE_REFUSED_ATTRS = setOf("style", "class", "hidden", "aria-hidden", "dir")

    private fun excerptOf(el: Element) = el.outerHtml().take(80)

    private fun attributeViolations(
        el: Element,
        inQuote: Boolean,
        out: MutableList<Violation>,
    ) {
        val name = el.normalName()
        for (attr in el.attributes()) {
            val key = attr.key.lowercase()

            fun flag(detail: String) = out.add(Violation(Violation.Kind.MARKUP, detail, excerptOf(el)))
            if (inQuote && key in QUOTE_REFUSED_ATTRS) {
                flag("$key= on quoted text, which could hide or change what it shows")
                continue
            }
            if (REFUSED_ATTRS.matches(key)) {
                flag(if (key.startsWith("on")) "the page contains an inline event handler ($key=)" else "$key= is not allowed on a paper")
                continue
            }
            if (key in GLOBAL_ATTRS || key.startsWith("aria-")) continue
            if (ELEMENT_ATTRS[name]?.contains(key) != true) {
                flag("$key= is not allowed on <$name>")
                continue
            }
            if (name == "a" && key == "target" && attr.value != "_blank") {
                flag("target=\"${attr.value.take(20)}\" - a link may only open in a new tab")
            }
        }
        if (name == "meta") {
            val metaName = el.attr("name")
            val ok =
                if (el.hasAttr("charset")) {
                    !el.hasAttr("name") && !el.hasAttr("content")
                } else {
                    el.hasAttr("name") && metaName.lowercase() in META_NAMES && el.hasAttr("content")
                }
            if (!ok) out.add(Violation(Violation.Kind.MARKUP, "a <meta> other than charset, viewport or description", excerptOf(el)))
        }
    }

    private fun elementViolations(
        doc: Document,
        out: MutableList<Violation>,
    ) {
        for (el in doc.allElements) {
            if (el is Document) continue
            val name = el.normalName()
            if (name !in ALLOWED_ELEMENTS) {
                out.add(Violation(Violation.Kind.MARKUP, "the page contains ${REFUSED_ELEMENTS[name] ?: "a <$name>, which a paper may not use"}", excerptOf(el)))
                continue
            }
            val quote = el.closest("q, blockquote")
            if (quote != null && quote !== el) {
                val attribution = el.parent()?.closest("cite, footer")
                val inAttribution = attribution != null && attribution.closest("q, blockquote") === quote && attribution !== quote
                val allowed = name in QUOTE_INSIDE || (inAttribution && name in ATTRIBUTION_INSIDE)
                if (!allowed) {
                    out.add(Violation(Violation.Kind.MARKUP, "a <$name> inside a quote - quotes hold only p, br, em, strong, b, i, cite and footer", excerptOf(el)))
                }
            }
            attributeViolations(el, quote != null, out)
        }
    }

    // --- markup: urls ---------------------------------------------------------------

    private fun urlViolations(
        doc: Document,
        corpus: Corpus,
        allowedImages: Set<String>,
        index: Map<String, DeskIndex>,
        out: MutableList<Violation>,
    ) {
        for (img in doc.select("img")) {
            if (!img.hasAttr("src")) continue
            val src = img.attr("src")
            if (cleanUrl(src) !in allowedImages) out.add(Violation(Violation.Kind.IMAGE, "image source is not from the shortlist", src.take(120)))
        }

        val eventIds = corpus.events.mapTo(HashSet()) { it.id.lowercase() }
        val calendarIds = index.getValue("calendar").byId
        val authors = corpus.events.mapTo(HashSet()) { it.pubKey.lowercase() }
        for (anchor in doc.select("a[href]")) {
            val raw = anchor.attr("href")
            if (isFragment(raw)) continue
            val href = cleanUrl(raw)
            val id = permalinkTarget(href)
            // A jumble nevent naming a CALENDAR listing is refused: it freezes one revision of
            // an event whose nature is to be replaced. Resolve rewrites these to njump naddrs.
            if (id != null && id in eventIds && !calendarIds.containsKey(id)) continue
            if (DESK_LINKS.keys.any { deskLinkTarget(it, href, index) != null }) continue
            // Bylines: only the canonical npub form, only for someone this edition read.
            if (PROFILE_LINK.find(href)?.let { fromNpub(it.groupValues[1]) }?.let { it in authors } == true) continue
            // Presence in the corpus is evidence of NOTHING: the corpus is written by the
            // attacker too, so allowlisting its URLs once allowlisted a phishing link. Every
            // pattern above is anchored at `https://<host>/`, so `//host`, backslashes and
            // other schemes never match.
            out.add(
                Violation(
                    Violation.Kind.LINK,
                    "only source citations, author bylines, verified zap.stream watch links, Shopstr listing links, and njump calendar links may be links",
                    raw.take(120),
                ),
            )
        }
    }

    // --- markup: css ------------------------------------------------------------------

    /** Constructs that fetch, script or escape the check, refused in every stylesheet. */
    private val CSS_REFUSED =
        listOf(
            Triple(Regex("\\\\"), Violation.Kind.MARKUP, "a backslash in CSS - escapes hide what a rule says"),
            Triple(Regex("@import", RegexOption.IGNORE_CASE), Violation.Kind.MARKUP, "the stylesheet imports another"),
            Triple(Regex("@font-face", RegexOption.IGNORE_CASE), Violation.Kind.MARKUP, "a web font - the paper loads nothing but its art"),
            Triple(Regex("(?:^|[^-\\w])(?:-webkit-)?image-set\\s*\\(", RegexOption.IGNORE_CASE), Violation.Kind.IMAGE, "a CSS image-set() - art is only ever <img>"),
            Triple(Regex("(?:^|[^-\\w])(?:url|src|image|cross-fade|element)\\s*\\(", RegexOption.IGNORE_CASE), Violation.Kind.IMAGE, "a CSS url() - art is only ever <img>"),
            Triple(Regex("expression\\s*\\(", RegexOption.IGNORE_CASE), Violation.Kind.MARKUP, "a CSS expression()"),
            Triple(Regex("(?:^|[^-\\w])behavior\\s*:", RegexOption.IGNORE_CASE), Violation.Kind.MARKUP, "a CSS behavior"),
            Triple(Regex("-moz-binding", RegexOption.IGNORE_CASE), Violation.Kind.MARKUP, "a CSS -moz-binding"),
        )

    private val AT_RULES = setOf("media", "supports", "keyframes", "-webkit-keyframes", "layer", "container")

    private class CssRule(
        val selector: String,
        val body: String,
    )

    private class CssBlock(
        val prelude: String,
        val body: String,
    )

    private class Declaration(
        val prop: String,
        val value: String,
    )

    private val COMMENT = Regex("/\\*[\\s\\S]*?(\\*/|$)")

    private fun stripComments(css: String) = css.replace(COMMENT, " ")

    /** End of the quoted string starting at [at] (no backslashes: those are refused). */
    private fun skipString(
        css: String,
        at: Int,
    ): Int {
        val end = css.indexOf(css[at], at + 1)
        return if (end < 0) css.length else end + 1
    }

    /** Top-level blocks: `prelude { body }`. Strings may hold braces. */
    private fun blocks(css: String): List<CssBlock> {
        val out = mutableListOf<CssBlock>()
        var start = 0
        var i = 0
        while (i < css.length) {
            val ch = css[i]
            if (ch == '"' || ch == '\'') {
                i = skipString(css, i)
            } else if (ch == '{') {
                var depth = 1
                var j = i + 1
                while (j < css.length && depth > 0) {
                    val c = css[j]
                    if (c == '"' || c == '\'') {
                        j = skipString(css, j)
                        continue
                    }
                    if (c == '{') {
                        depth++
                    } else if (c == '}') {
                        depth--
                    }
                    j++
                }
                out.add(CssBlock(css.substring(start, i).trim(), css.substring(i + 1, if (depth == 0) j - 1 else j)))
                i = j
                start = j
            } else {
                if (ch == ';' || ch == '}') start = i + 1
                i++
            }
        }
        return out
    }

    private val AT_NAME = Regex("^@([-\\w]+)")

    /** Every style rule, at any depth of @media / @supports / @layer / @container. */
    private fun styleRules(
        css: String,
        refusedAt: (String) -> Unit,
    ): List<CssRule> {
        val out = mutableListOf<CssRule>()

        fun visit(text: String) {
            for (block in blocks(text)) {
                if (block.prelude.startsWith("@")) {
                    val name = (AT_NAME.find(block.prelude)?.groupValues?.get(1) ?: "").lowercase()
                    if (name !in AT_RULES) {
                        refusedAt(name)
                    } else if (!name.endsWith("keyframes")) {
                        visit(block.body)
                    }
                } else {
                    out.add(CssRule(block.prelude, block.body))
                }
            }
        }
        val clean = stripComments(css)
        visit(clean)
        // Statement at-rules (`@namespace …;`, `@charset`) have no block for [visit] to see.
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            if (ch == '"' || ch == '\'') {
                i = skipString(clean, i)
                continue
            }
            if (ch == '@') {
                val name = (AT_NAME.find(clean.substring(i))?.groupValues?.get(1) ?: "").lowercase()
                if (name !in AT_RULES) refusedAt(name)
            }
            i++
        }
        return out
    }

    private val IMPORTANT = Regex("!important\\s*$", RegexOption.IGNORE_CASE)

    /** `prop: value` pairs of a declaration block. */
    private fun declarations(body: String): List<Declaration> {
        val out = mutableListOf<Declaration>()
        var start = 0

        fun push(end: Int) {
            val part = body.substring(start, end)
            val colon = part.indexOf(':')
            if (colon > 0) out.add(Declaration(part.substring(0, colon).trim().lowercase(), part.substring(colon + 1).replace(IMPORTANT, "").trim()))
        }
        var i = 0
        while (i < body.length) {
            val ch = body[i]
            if (ch == '"' || ch == '\'') {
                i = skipString(body, i)
            } else {
                if (ch == ';') {
                    push(i)
                    start = i + 1
                }
                i++
            }
        }
        push(body.length)
        return out
    }

    private fun squash(text: String) = text.replace(WS, " ").trim()

    private fun ruleKey(rule: CssRule) = squash(rule.selector) + "{" + declarations(rule.body).joinToString(";") { it.prop + ":" + squash(it.value) } + "}"

    /** The house stylesheet's own rules: trusted as given, wherever the page repeats them. */
    private val houseRules: Set<String> by lazy { styleRules(HOUSE_CSS) {}.map(::ruleKey).toSet() }

    private fun isHouseRule(rule: CssRule) = ruleKey(rule) in houseRules

    /** Quote marks only: a `quotes:` string that is words would print them around a quote. */
    private val QUOTE_MARKS = Regex("^[\\s\"'“”‘’«»‹›„‚‛‟「」『』〝〞]*$")
    private val CSS_STRING = Regex("\"([^\"]*)\"|'([^']*)'")
    private val NO_CONTENT = Regex("^(none|normal|\"\"|'')$", RegexOption.IGNORE_CASE)

    private fun declarationViolation(
        prop: String,
        value: String,
    ): String? {
        if (prop == "content" && !NO_CONTENT.matches(value.trim())) return "CSS generated content - every word on the page is in its markup"
        if (prop == "quotes") {
            for (match in CSS_STRING.findAll(value)) {
                val text = match.groups[1]?.value ?: match.groups[2]?.value ?: ""
                if (!QUOTE_MARKS.matches(text)) return "CSS quotes: that are words, not quote marks"
            }
        }
        return null
    }

    /** On text inside a quote, only what changes how it looks, never whether it shows. */
    private val INSIDE_SAFE =
        setOf(
            "font-weight",
            "font-style",
            "font-family",
            "font-variant",
            "font-feature-settings",
            "font-kerning",
            "font-size",
            "text-decoration",
            "text-decoration-line",
            "text-decoration-style",
            "text-decoration-color",
            "text-decoration-thickness",
            "text-underline-offset",
            "text-transform",
            "line-height",
            "margin",
            "margin-top",
            "margin-bottom",
            "margin-left",
            "margin-right",
            "padding",
            "padding-top",
            "padding-bottom",
            "padding-left",
            "padding-right",
            "border",
            "border-top",
            "border-bottom",
            "border-left",
            "border-right",
            "border-color",
            "border-style",
            "border-width",
            "text-align",
            "hyphens",
            "quotes",
            "display",
        )

    /** On the quote itself, what could show part of it and hide the rest. */
    private val QUOTE_UNSAFE =
        Regex(
            "^(content|overflow(-[xy])?|text-overflow|(-webkit-)?line-clamp|clip|clip-path|(-webkit-)?mask(-.*)?|height|max-height|block-size|max-block-size|text-indent|animation(-.*)?|-webkit-text-fill-color|-webkit-text-stroke(-.*)?)$",
        )

    private val KEYWORD = Regex("^[a-z-]+$")
    private val LENGTH = Regex("^(\\d*\\.?\\d+)(px|pt|em|rem|ch|ex|%)$")

    private fun tooSmall(value: String): Boolean {
        val v = value.trim().lowercase()
        if (KEYWORD.matches(v)) return false // small, x-small, larger…
        val m = LENGTH.matchEntire(v) ?: return true // calc(), var(), vw: can't be read, so not allowed
        val n = m.groupValues[1].toDouble()
        return when (m.groupValues[2]) {
            "px", "pt" -> n < 8
            "%" -> n < 50
            else -> n < 0.5
        }
    }

    private val VAR = Regex("var\\(", RegexOption.IGNORE_CASE)
    private val NONE_WORD = Regex("\\bnone\\b", RegexOption.IGNORE_CASE)
    private val NEGATIVE = Regex("-\\s*\\d|calc\\(", RegexOption.IGNORE_CASE)

    private fun insideUnsafe(
        prop: String,
        value: String,
    ): Boolean {
        if (prop.startsWith("--")) return false
        if (prop !in INSIDE_SAFE) return true
        if (VAR.containsMatchIn(value)) return true
        if (prop == "display" && NONE_WORD.containsMatchIn(value)) return true
        if (prop == "font-size" && tooSmall(value)) return true
        if ((prop.startsWith("margin") || prop.startsWith("padding")) && NEGATIVE.containsMatchIn(value)) return true
        return false
    }

    /** Pseudo-elements and dynamic states don't change WHICH element a rule reaches. */
    private val PSEUDO_ELEMENT = Regex("::?(before|after|first-line|first-letter|marker|selection|placeholder|backdrop)\\b", RegexOption.IGNORE_CASE)
    private val DYNAMIC = Regex(":(hover|focus|focus-within|focus-visible|active|visited|link|target)\\b", RegexOption.IGNORE_CASE)

    private fun splitSelectors(selector: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in selector.indices) {
            when (selector[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' ->
                    if (depth == 0) {
                        out.add(selector.substring(start, i))
                        start = i + 1
                    }
            }
        }
        out.add(selector.substring(start))
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * A rule the house didn't write that reaches quoted text and could hide part of it
     * (`blockquote em { display: none }`), or show part of a quote and not the rest.
     */
    private fun quoteRuleViolation(
        doc: Document,
        rule: CssRule,
        hasQuotes: Boolean,
    ): String? {
        if (!hasQuotes || isHouseRule(rule)) return null
        val decls = declarations(rule.body)
        for (selector in splitSelectors(rule.selector)) {
            val pseudo = PSEUDO_ELEMENT.containsMatchIn(selector)
            val plain =
                selector
                    .replace(PSEUDO_ELEMENT, "")
                    .replace(DYNAMIC, "")
                    .trim()
                    .ifEmpty { "*" }
            var reachesQuote = false
            var reachesInside = false
            try {
                for (el in doc.select(plain)) {
                    val quote = el.closest("q, blockquote") ?: continue
                    if (quote === el) reachesQuote = true else reachesInside = true
                }
            } catch (e: Exception) {
                // A selector this parser can't read may still match in a browser: assume it does.
                reachesQuote = true
                reachesInside = true
            }
            if (reachesInside || (reachesQuote && pseudo)) {
                decls.firstOrNull { insideUnsafe(it.prop, it.value) }?.let { return "${squash(selector)} { ${it.prop}: … } reaches quoted text and could hide part of it" }
            } else if (reachesQuote) {
                decls.firstOrNull { QUOTE_UNSAFE.matches(it.prop) }?.let { return "${squash(selector)} { ${it.prop}: … } could show part of a quote and hide the rest" }
            }
        }
        return null
    }

    private fun cssViolations(
        doc: Document,
        out: MutableList<Violation>,
    ) {
        val hasQuotes = doc.select("q, blockquote").isNotEmpty()

        fun refuseConstructs(
            css: String,
            excerpt: String,
        ) {
            for ((pattern, kind, detail) in CSS_REFUSED) {
                if (pattern.containsMatchIn(css)) out.add(Violation(kind, detail, excerpt))
            }
        }
        for (block in doc.select("style")) {
            val css = block.data()
            refuseConstructs(css, css.take(80))
            val rules = styleRules(css) { name -> out.add(Violation(Violation.Kind.MARKUP, "a CSS @$name rule", "@$name")) }
            for (rule in rules) {
                for (decl in declarations(rule.body)) {
                    declarationViolation(decl.prop, decl.value)?.let {
                        out.add(Violation(Violation.Kind.MARKUP, it, "${squash(rule.selector)} { ${decl.prop}: ${decl.value} }".take(120)))
                    }
                }
                quoteRuleViolation(doc, rule, hasQuotes)?.let { out.add(Violation(Violation.Kind.MARKUP, it, squash(rule.selector).take(80))) }
            }
        }
        for (el in doc.select("[style]")) {
            val css = el.attr("style")
            refuseConstructs(css, css.take(80))
            for (decl in declarations(stripComments(css))) {
                declarationViolation(decl.prop, decl.value)?.let { out.add(Violation(Violation.Kind.MARKUP, it, excerptOf(el))) }
            }
        }
    }

    /** Every markup, URL and stylesheet refusal for one parsed page. */
    fun markupViolations(
        doc: Document,
        corpus: Corpus,
    ): List<Violation> {
        val out = mutableListOf<Violation>()
        elementViolations(doc, out)
        cssViolations(doc, out)
        urlViolations(doc, corpus, corpus.art.mapTo(HashSet()) { it.url }, index(corpus), out)
        return out
    }

    // --- fixpoint -----------------------------------------------------------------------

    /**
     * The page as every later reader will parse it. Markup that changes when it is written out
     * and read back (mutation XSS) is checked in the form it settles into; one that never
     * settles is refused.
     */
    fun settle(
        doc: Document,
        rounds: Int = 3,
    ): Pair<Document, Boolean> {
        var current = doc
        var text = serialize(current)
        repeat(rounds) {
            val next = parsePage(text)
            val nextText = serialize(next)
            current = next
            if (nextText == text) return current to true
            text = nextText
        }
        return current to false
    }

    class CheckResult(
        val violations: List<Violation>,
        val quotes: List<String>,
        /** The document that was checked: the page as it settles after a round trip. */
        val doc: Document,
    )

    /** Everything the boundary has to say about one page. Pure, so a test can feed it an adversarial page. */
    fun check(
        page: Document,
        corpus: Corpus,
    ): CheckResult {
        val (doc, stable) = settle(page)
        // The DESKS only, never the control run: it is not in the digest the writer reads,
        // and a quote must not verify against something the edition never had.
        val haystack = corpus.events.map { normalize(it.content) }

        val violations = mutableListOf<Violation>()
        if (!stable) violations.add(Violation(Violation.Kind.MARKUP, "the markup changes every time it is read back", serialize(doc).take(80)))
        violations.addAll(markupViolations(doc, corpus))

        val quotes = quotedText(doc)
        for (quote in quotes) {
            if (!isQuoted(quote, haystack)) violations.add(Violation(Violation.Kind.QUOTE, "not found verbatim in any source event", quote.take(160)))
        }
        return CheckResult(violations, quotes, doc)
    }
}
