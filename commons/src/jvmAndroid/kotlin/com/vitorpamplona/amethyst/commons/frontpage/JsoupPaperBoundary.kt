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
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
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
 * Two checks are stricter than the Observer (marked "Beyond the Observer"): `srcset` and
 * CSS `url()` could load a picture from anywhere without passing the IMAGE check.
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
        return CheckedPage(serialize(doc), changes, result.violations, result.quotes.size, headline(doc))
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
        val fragments = needle.split("...").map { it.trim() }.filter { it.length > 2 }
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

    /** Text nodes joined with spaces: `a<br>b` is "a b", not "ab". */
    private fun quoteText(el: Element): String {
        val parts = mutableListOf<String>()
        NodeTraversor.traverse(
            NodeVisitor { node, _ -> if (node is TextNode) parts.add(node.wholeText) },
            el,
        )
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
    private val HTTP = Regex("^https?:", RegexOption.IGNORE_CASE)

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
            val list = corpus.events.filter { it.kind in link.kinds && it.firstTagValue("d") != null }
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
            val id = img.attr("src")
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
            val url = anchor.attr("href")
            if (!HTTP.containsMatchIn(url)) continue
            val id = citedEventId(url)

            fun keep(
                canonical: String,
                kind: ResolveChange.Kind,
            ) {
                if (url != canonical) {
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
            if (desk != null) {
                keep(desk.first.canonicalUrl(desk.second), desk.first.change)
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

    // --- markup ----------------------------------------------------------------

    // Refused rather than stripped, and checked on PARSED elements: matching `on…=` in the
    // raw text once read ordinary prose ("we ran it once=twice") as an attack.
    private val FORBIDDEN_TAGS =
        mapOf(
            "script" to "a <script> tag",
            "iframe" to "an <iframe>",
            "frame" to "a <frame>",
            "object" to "an embedded object",
            "embed" to "an embedded object",
            "applet" to "an embedded object",
            "form" to "a form - the paper collects nothing",
            "input" to "a form control",
            "button" to "a form control",
            "textarea" to "a form control",
            "select" to "a form control",
            "base" to "a <base> tag, which rewrites every relative URL on the page",
        )

    // `<meta>` is not forbidden (charset, viewport); only the forms that redirect or rewrite policy.
    private val FORBIDDEN_META = Regex("^\\s*(refresh|content-security-policy|set-cookie|location)\\s*$", RegexOption.IGNORE_CASE)
    private val FORBIDDEN_SCHEME = Regex("^\\s*(javascript|vbscript|data)\\s*:", RegexOption.IGNORE_CASE)
    private val URL_ATTRS = setOf("href", "src", "action", "formaction", "poster", "background", "srcset", "data", "xlink:href")
    private val CSS_URL = Regex("url\\(\\s*([\"']?)(.*?)\\1\\s*\\)", RegexOption.IGNORE_CASE)
    private val CSS_IMPORT = Regex("@import|url\\s*\\(\\s*[\"']?\\s*(javascript|data)\\s*:", RegexOption.IGNORE_CASE)

    private fun excerptOf(el: Element) = el.outerHtml().take(80)

    private fun markupViolations(
        doc: Document,
        allowedImages: Set<String>,
    ): List<Violation> {
        val out = mutableListOf<Violation>()
        for (el in doc.allElements) {
            val name = el.normalName()
            FORBIDDEN_TAGS[name]?.let { out.add(Violation(Violation.Kind.MARKUP, "the page contains $it", excerptOf(el))) }
            if (name == "meta" && FORBIDDEN_META.containsMatchIn(el.attr("http-equiv"))) {
                out.add(Violation(Violation.Kind.MARKUP, "a <meta http-equiv=\"${el.attr("http-equiv")}\">, which redirects or rewrites policy", excerptOf(el)))
            }
            for (attr in el.attributes()) {
                val key = attr.key.lowercase()
                if (key.startsWith("on")) {
                    out.add(Violation(Violation.Kind.MARKUP, "the page contains an inline event handler ($key=)", excerptOf(el)))
                }
                if (key in URL_ATTRS && FORBIDDEN_SCHEME.containsMatchIn(attr.value)) {
                    out.add(Violation(Violation.Kind.MARKUP, "$key= carries a scripting or inline-document URL", attr.value.take(80)))
                }
                // Beyond the Observer: `srcset` loads a picture the IMAGE check never sees.
                if (key == "srcset") {
                    out.add(Violation(Violation.Kind.IMAGE, "srcset is not allowed - use one shortlist picture", attr.value.take(80)))
                }
            }
            // Beyond the Observer: a `url()` in a style attribute fetches like an <img>.
            if (el.hasAttr("style")) out.addAll(cssUrlViolations(el.attr("style"), allowedImages))
        }
        for (block in doc.select("style")) {
            val css = block.data()
            if (CSS_IMPORT.containsMatchIn(css)) {
                out.add(Violation(Violation.Kind.MARKUP, "the stylesheet imports or embeds something", css.take(80)))
            }
            out.addAll(cssUrlViolations(css, allowedImages))
        }
        return out
    }

    private fun cssUrlViolations(
        css: String,
        allowedImages: Set<String>,
    ): List<Violation> =
        CSS_URL
            .findAll(css)
            .map { it.groupValues[2].trim() }
            .filter { it !in allowedImages }
            .map { Violation(Violation.Kind.IMAGE, "a CSS url() that is not shortlist art", it.take(120)) }
            .toList()

    class CheckResult(
        val violations: List<Violation>,
        val quotes: List<String>,
    )

    /** Everything the boundary has to say about one page. Pure, so a test can feed it an adversarial page. */
    fun check(
        doc: Document,
        corpus: Corpus,
    ): CheckResult {
        // The DESKS only, never the control run: it is not in the digest the writer reads,
        // and a quote must not verify against something the edition never had.
        val events = corpus.events
        val haystack = events.map { normalize(it.content) }
        val eventIds = events.mapTo(HashSet()) { it.id }
        val allowedImages = corpus.art.mapTo(HashSet()) { it.url }
        val index = index(corpus)
        val calendarIds = index.getValue("calendar").byId
        val authors = events.mapTo(HashSet()) { it.pubKey.lowercase() }

        val violations = markupViolations(doc, allowedImages).toMutableList()

        val quotes = quotedText(doc)
        for (quote in quotes) {
            if (!isQuoted(quote, haystack)) violations.add(Violation(Violation.Kind.QUOTE, "not found verbatim in any source event", quote.take(160)))
        }

        for (img in doc.select("img")) {
            if (!img.hasAttr("src")) continue
            val src = img.attr("src")
            if (src !in allowedImages) violations.add(Violation(Violation.Kind.IMAGE, "image source is not from the shortlist", src.take(120)))
        }

        for (anchor in doc.select("a[href], area[href]")) {
            val href = anchor.attr("href")
            if (!HTTP.containsMatchIn(href)) continue
            val id = permalinkTarget(href)
            // A jumble nevent naming a CALENDAR listing is refused: it freezes one revision of
            // an event whose nature is to be replaced. Resolve rewrites these to njump naddrs.
            if (id != null && id in eventIds && !calendarIds.containsKey(id)) continue
            if (DESK_LINKS.keys.any { deskLinkTarget(it, href, index) != null }) continue
            // Bylines: only the canonical npub form, only for someone this edition read.
            if (PROFILE_LINK.find(href)?.let { fromNpub(it.groupValues[1]) }?.let { it in authors } == true) continue
            // Presence in the corpus is evidence of NOTHING: the corpus is written by the
            // attacker too, so allowlisting its URLs once allowlisted a phishing link.
            violations.add(
                Violation(
                    Violation.Kind.LINK,
                    "only source citations, author bylines, verified zap.stream watch links, Shopstr listing links, and njump calendar links may be links",
                    href.take(120),
                ),
            )
        }
        return CheckResult(violations, quotes)
    }
}
