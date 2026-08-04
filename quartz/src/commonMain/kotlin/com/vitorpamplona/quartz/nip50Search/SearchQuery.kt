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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Parsed representation of a NIP-50 `search` filter string.
 *
 * NIP-50 defines the [com.vitorpamplona.quartz.nip01Core.relay.filters.Filter.search]
 * field as "a string describing a query in a human-readable form", optionally
 * carrying `key:value` extension tokens such as `domain:example.com` or
 * `language:en`. This class splits that raw string into the free-text [terms],
 * the Google-style term syntax ([phrases], [notPhrases], [notTerms]), and the
 * recognized [extensions], giving relays (and search redirectors) a typed view
 * of the query instead of forcing each one to re-parse the string.
 *
 * Example:
 * ```
 * val q = SearchQuery.parse("best \"nostr apps\" -spam domain:example.com")
 * q.terms      // "best"
 * q.phrases    // ["nostr apps"]
 * q.notTerms   // ["spam"]
 * q.domain     // "example.com"
 * ```
 *
 * ## Parse order (load-bearing)
 *
 * Quoted spans are lifted off the RAW string FIRST, then the residual is
 * tokenized for extensions, then `-word` exclusions split off. The order
 * matters twice over: the extension pass is quote-blind, so a span ending in
 * an extension-shaped token (`"pizza sort:rank" -spam`) would otherwise lose
 * its closing quote and the unclosed quote would swallow the rest of the
 * query; and lifting first lets quotes protect extension-shaped tokens —
 * `"include:spam"` is the phrase [include, spam], not an extension.
 *
 * ## Term syntax
 *
 * - A `"quoted span"` is an exact-phrase requirement ([phrases]); `-"…"` is a
 *   phrase exclusion ([notPhrases]). A quote opens a span only at a token
 *   boundary — mid-token quotes stay ordinary characters. An unclosed span
 *   runs to the end of the string. Empty spans are dropped, but a positive
 *   phrase keeps content a text index may not hold ("⚡"): it is an
 *   unsatisfiable requirement the backend turns into provably-no-match —
 *   dropping it here would silently flip that into match-all.
 * - A leading `-` on a 2+ character token makes it an exclusion ([notTerms],
 *   all leading dashes stripped); a lone `-` stays an ordinary term. There is
 *   no `-extension` syntax: extension keys are strictly `a`–`z`, so
 *   `-include:spam` fails the key test and becomes the excluded literal.
 *
 * ## Extension tokenization
 *
 * The residual is split on whitespace. A token is treated as an extension when
 * it contains a `:`, the part before the `:` is a non-empty run of lowercase
 * ASCII letters (`a`–`z`), and the part after is non-empty and does not start
 * with `//` (so URLs like `https://example.com` stay in [terms]). Per NIP-50
 * unknown extensions are kept (readable through [extensions] / [extension]) so
 * they can be forwarded to a backend; relays "SHOULD ignore extensions they
 * don't support". Extension keys are matched case-sensitively against the
 * lowercase forms documented by NIP-50. Duplicate keys keep the last
 * occurrence.
 */
class SearchQuery(
    /** The loose human-readable search terms: extensions, phrases, and exclusions all removed. */
    val terms: String,
    /**
     * All recognized `key:value` extension tokens, in the order they appeared.
     * Known keys: [INCLUDE], [DOMAIN], [LANGUAGE], [SENTIMENT], [NSFW].
     */
    val extensions: Map<String, String>,
    /** Exact-phrase requirements (`"nostr apps"`), quotes removed, in order. */
    val phrases: List<String> = emptyList(),
    /** Exact-phrase exclusions (`-"nostr apps"`), quotes removed, in order. */
    val notPhrases: List<String> = emptyList(),
    /** Single-word exclusions (`-spam`), dashes removed, in order. */
    val notTerms: List<String> = emptyList(),
) {
    /** `true` when the query carries the `include:spam` token (NIP-50: disable spam filtering). */
    val includeSpam: Boolean
        get() = extensions[INCLUDE] == SPAM

    /** The `domain:<nip05-domain>` value, or null when not present. */
    val domain: String?
        get() = extensions[DOMAIN]

    /** The `language:<ISO-639-1>` value, or null when not present. */
    val language: String?
        get() = extensions[LANGUAGE]

    /** The parsed `sentiment:<negative|neutral|positive>` value, or null when absent/unrecognized. */
    val sentiment: Sentiment?
        get() = extensions[SENTIMENT]?.let(Sentiment::parse)

    /** The parsed `nsfw:<true|false>` value, or null when not present. See [nsfwIncluded]. */
    val nsfw: Boolean?
        get() = extensions[NSFW]?.toBooleanStrictOrNull()

    /**
     * Whether nsfw events should be included, applying NIP-50's documented
     * default of `true` when the `nsfw` token is absent.
     */
    val nsfwIncluded: Boolean
        get() = nsfw ?: true

    /**
     * Whether the query REQUIRES any text — loose terms or phrases. Exclusions
     * alone don't count: an exclusions-only query is plain recall minus the
     * excluded words, not a ranked text search.
     */
    val hasText: Boolean
        get() = terms.isNotEmpty() || phrases.isNotEmpty()

    /** Returns the raw value of an arbitrary extension key (including unknown ones), or null. */
    fun extension(key: String): String? = extensions[key]

    /** Returns true when there are no loose free-text terms. Phrases don't count — see [hasText] for "any required text". */
    fun isTermsEmpty(): Boolean = terms.isEmpty()

    /**
     * Re-assembles a canonical NIP-50 search string: the free-text [terms],
     * then each `"phrase"`, `-exclusion`, `-"phrase exclusion"`, and
     * `key:value` extension. Canonical, not order-preserving. Useful for a
     * redirector that normalizes the incoming query before forwarding it.
     */
    fun toSearchString(): String =
        buildString {
            append(terms)
            for (phrase in phrases) {
                if (isNotEmpty()) append(' ')
                append('"').append(phrase).append('"')
            }
            for (word in notTerms) {
                if (isNotEmpty()) append(' ')
                append('-').append(word)
            }
            for (phrase in notPhrases) {
                if (isNotEmpty()) append(' ')
                append("-\"").append(phrase).append('"')
            }
            for ((key, value) in extensions) {
                if (isNotEmpty()) append(' ')
                append(key).append(':').append(value)
            }
        }

    companion object {
        /** NIP-50 extension key `include` (only documented value is [SPAM]). */
        const val INCLUDE = "include"

        /** Documented value for the [INCLUDE] key. */
        const val SPAM = "spam"

        /** NIP-50 extension key `domain`. */
        const val DOMAIN = "domain"

        /** NIP-50 extension key `language`. */
        const val LANGUAGE = "language"

        /** NIP-50 extension key `sentiment`. */
        const val SENTIMENT = "sentiment"

        /** NIP-50 extension key `nsfw`. */
        const val NSFW = "nsfw"

        private val WHITESPACE = Regex("\\s+")

        /** Empty query — no terms, no syntax, no extensions. */
        val EMPTY = SearchQuery("", emptyMap())

        /**
         * Parses a raw NIP-50 [search] string into a [SearchQuery]. A null or
         * blank input yields [EMPTY]. See the class KDoc for the grammar and
         * why the quote pass runs before the extension pass.
         */
        fun parse(search: String?): SearchQuery {
            if (search.isNullOrBlank()) return EMPTY

            val quoted = liftQuotedSpans(search)
            val extensions = LinkedHashMap<String, String>()
            val terms = StringBuilder()
            val notTerms = ArrayList<String>()

            for (token in quoted.residual.trim().split(WHITESPACE)) {
                if (token.isEmpty()) continue
                val colon = token.indexOf(':')
                if (colon > 0 && colon < token.length - 1) {
                    val key = token.substring(0, colon)
                    val value = token.substring(colon + 1)
                    if (isExtensionKey(key) && !value.startsWith("//")) {
                        extensions[key] = value
                        continue
                    }
                }
                if (token.length > 1 && token[0] == '-') {
                    notTerms += token.trimStart('-')
                    continue
                }
                if (terms.isNotEmpty()) terms.append(' ')
                terms.append(token)
            }

            return SearchQuery(terms.toString(), extensions, quoted.phrases, quoted.notPhrases, notTerms)
        }

        private fun isExtensionKey(key: String): Boolean = key.isNotEmpty() && key.all { it in 'a'..'z' }

        /** The quoted spans lifted off the raw text, plus the residual for the extension and `-word` passes. */
        private class QuotedSpans(
            val phrases: List<String>,
            val notPhrases: List<String>,
            val residual: String,
        )

        /** Stage one, over the RAW string: lift every `"…"` / `-"…"` span. See the class KDoc for the rules. */
        private fun liftQuotedSpans(text: String): QuotedSpans {
            val phrases = ArrayList<String>()
            val notPhrases = ArrayList<String>()
            val residual = StringBuilder()
            var i = 0
            var boundary = true
            while (i < text.length) {
                val c = text[i]
                val neg = c == '-' && i + 1 < text.length && text[i + 1] == '"'
                if (boundary && (c == '"' || neg)) {
                    val start = i + if (neg) 2 else 1
                    val close = text.indexOf('"', start)
                    val end = if (close < 0) text.length else close
                    val span = text.substring(start, end).trim()
                    i = if (close < 0) text.length else close + 1
                    if (span.isNotEmpty()) {
                        if (neg) notPhrases += span else phrases += span
                    }
                    // The lifted span's place stays a token boundary for what follows.
                    residual.append(' ')
                } else {
                    residual.append(c)
                    boundary = c.isWhitespace()
                    i++
                }
            }
            return QuotedSpans(phrases, notPhrases, residual.toString())
        }

        /**
         * Returns [search] with every `key:value` extension token removed,
         * leaving the free-text query (terms, phrases, and exclusions,
         * re-assembled as in [toSearchString]).
         *
         * Backends that hand the search string to an engine with its own
         * query syntax — e.g. SQLite FTS, where `:` is column-filter
         * syntax and `include:spam` raises "no such column" — must call
         * this (or apply the extensions themselves) before querying.
         *
         * A string with no extension tokens is returned unchanged. An
         * extensions-only query collapses to `""`, which event stores
         * treat as "no search constraint" — the NIP-50 behaviour for
         * relays that don't support an extension is to ignore it, not to
         * return nothing.
         */
        fun stripExtensions(search: String?): String? {
            if (search.isNullOrBlank()) return search
            val parsed = parse(search)
            if (parsed.extensions.isEmpty()) return search
            return SearchQuery(parsed.terms, emptyMap(), parsed.phrases, parsed.notPhrases, parsed.notTerms).toSearchString()
        }
    }
}

/**
 * Returns a copy of this filter whose `search` string has all NIP-50
 * extension tokens removed (see [SearchQuery.stripExtensions]), or this
 * same instance when there is nothing to strip.
 */
fun Filter.strippingSearchExtensions(): Filter {
    val stripped = SearchQuery.stripExtensions(search)
    return if (stripped == search) this else copy(search = stripped)
}

/**
 * Applies [strippingSearchExtensions] to every filter, returning this
 * same list when no filter carried extension tokens.
 *
 * This runs on every REQ/COUNT/snapshot, and the overwhelming majority
 * carry no `search` term at all, so the no-search case must not allocate:
 * bail before building any list when nothing could be stripped.
 */
fun List<Filter>.strippingSearchExtensions(): List<Filter> {
    var hasSearch = false
    for (i in indices) {
        if (!this[i].search.isNullOrEmpty()) {
            hasSearch = true
            break
        }
    }
    if (!hasSearch) return this
    var changed = false
    val out =
        map {
            val stripped = it.strippingSearchExtensions()
            if (stripped !== it) changed = true
            stripped
        }
    return if (changed) out else this
}

/** NIP-50 `sentiment:` extension values. */
enum class Sentiment(
    val code: String,
) {
    NEGATIVE("negative"),
    NEUTRAL("neutral"),
    POSITIVE("positive"),
    ;

    companion object {
        fun parse(value: String): Sentiment? = entries.firstOrNull { it.code == value }
    }
}
