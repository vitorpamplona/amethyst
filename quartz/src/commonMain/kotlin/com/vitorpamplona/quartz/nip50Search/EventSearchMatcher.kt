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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.fastAny

/**
 * Matches an event against a NIP-50 `search` string, in memory, without an index.
 *
 * Built once per query and reused across every event of a scan: the terms are split and the
 * extensions stripped here, not per event, and `match` allocates nothing at all.
 *
 * ## What it matches
 *
 * Terms are ANDed and each is a case-insensitive substring — of a tag value, or of the event's
 * own indexable fields. Substring rather than token because that is what Amethyst's local search
 * has always done (`content.contains(text, true)`), and switching to tokens would silently stop
 * matching mid-word queries; AND rather than one literal phrase because that is what a relay does
 * with the same string, and the point of routing local search through a `Filter` is that the two
 * stop disagreeing. For a single-word query — the overwhelmingly common case — the two rules are
 * identical anyway.
 *
 * Unsupported NIP-50 extensions are ignored rather than treated as terms, per the spec, so a
 * search that is nothing but extensions matches everything rather than nothing.
 *
 * ## What it does not do
 *
 * No relevance score. A relay ranks bm25-first; this answers only yes or no, and any ordering is
 * the caller's to apply afterwards.
 *
 * ## Thread confinement
 *
 * An instance reuses one visitor across every event it is asked about, which is what keeps the
 * scan allocation-free — a lambda written inline would capture the term and allocate once per
 * event, defeating the whole point. That reuse makes an instance **single-threaded**: build one
 * per scan, do not share it between coroutines.
 */
class EventSearchMatcher(
    search: String?,
    /**
     * Tag names whose values are not worth searching — `p`/`e`/`a` hold hex ids that only ever
     * match by accident, `client` and `alt` hold text the author did not write.
     */
    private val exceptTagNames: Set<String> = DEFAULT_EXCLUDED_TAGS,
) {
    private val terms: Array<String> = splitTerms(SearchQuery.stripExtensions(search))

    /** True when this matcher constrains nothing, so callers can skip the walk entirely. */
    val isEmpty: Boolean get() = terms.isEmpty()

    fun match(event: Event): Boolean {
        if (terms.isEmpty()) return true
        for (i in terms.indices) {
            if (!matchesTerm(event, terms[i])) return false
        }
        return true
    }

    /** Reused for every field of every event; see the thread-confinement note on the class. */
    private val visitor = TermVisitor()

    private fun matchesTerm(
        event: Event,
        term: String,
    ): Boolean {
        if (event.tags.fastAny { it.size > 1 && it[0] !in exceptTagNames && it[1].contains(term, true) }) {
            return true
        }
        if (event !is SearchableEvent) return event.content.contains(term, true)
        return visitor.matches(event, term)
    }

    /**
     * Carries the term into the walk on a field rather than in a closure, so no lambda is
     * allocated per event, and stops the walk at the first field that holds it.
     */
    private class TermVisitor : IndexableFieldVisitor {
        private var term: String = ""
        private var found = false

        fun matches(
            event: SearchableEvent,
            term: String,
        ): Boolean {
            this.term = term
            found = false
            event.forEachIndexableField(this)
            return found
        }

        override fun visit(field: String?): Boolean {
            if (field != null && field.contains(term, true)) {
                found = true
                return false
            }
            return true
        }
    }

    companion object {
        val DEFAULT_EXCLUDED_TAGS = setOf("client", "p", "e", "a", "alt")

        /**
         * The search string as the terms to require: whitespace-separated, except inside double
         * quotes, where the span is one term with the quotes removed.
         *
         * A naive split turned `"hello world"` into `"hello` and `world"` — two terms carrying a
         * quote character, so a phrase search could never match anything. An unterminated quote
         * runs to the end of the string, which is how a lexer reads it and how relays read it.
         */
        fun splitTerms(search: String?): Array<String> {
            val s = search ?: return emptyArray()
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            for (c in s) {
                when {
                    c == '"' -> {
                        // A closing quote ends the term even when empty-adjacent, so `a""b` is `a`, `b`.
                        if (quoted && current.isNotEmpty()) {
                            out.add(current.toString())
                            current.clear()
                        }
                        quoted = !quoted
                    }

                    !quoted && c.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            out.add(current.toString())
                            current.clear()
                        }
                    }

                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
            return out.toTypedArray()
        }
    }
}
