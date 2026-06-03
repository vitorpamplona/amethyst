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

/**
 * Parsed representation of a NIP-50 `search` filter string.
 *
 * NIP-50 defines the [com.vitorpamplona.quartz.nip01Core.relay.filters.Filter.search]
 * field as "a string describing a query in a human-readable form", optionally
 * carrying `key:value` extension tokens such as `domain:example.com` or
 * `language:en`. This class splits that raw string into the free-text [terms]
 * and the recognized [extensions], giving relays (and search redirectors) a
 * typed view of the query instead of forcing each one to re-parse the string.
 *
 * Example:
 * ```
 * val q = SearchQuery.parse("best nostr apps domain:example.com language:en")
 * q.terms      // "best nostr apps"
 * q.domain     // "example.com"
 * q.language   // "en"
 * ```
 *
 * ## Tokenization
 *
 * The string is split on whitespace. A token is treated as an extension when:
 * - it contains a `:`,
 * - the part before the `:` is a non-empty run of lowercase ASCII letters
 *   (`a`–`z`), and
 * - the part after the `:` is non-empty and does not start with `//` (so URLs
 *   like `https://example.com` stay in [terms]).
 *
 * Everything else is free text. Per NIP-50 unknown extensions are kept (so they
 * can be forwarded to a backend) — relays "SHOULD ignore extensions they don't
 * support", which this models by simply not having a typed accessor for them;
 * they remain readable through [extensions] / [extension].
 *
 * Extension keys are matched case-sensitively against the lowercase forms
 * documented by NIP-50. Duplicate keys keep the last occurrence.
 */
class SearchQuery(
    /** The human-readable search terms with all extension tokens removed. */
    val terms: String,
    /**
     * All recognized `key:value` extension tokens, in the order they appeared.
     * Known keys: [INCLUDE], [DOMAIN], [LANGUAGE], [SENTIMENT], [NSFW].
     */
    val extensions: Map<String, String>,
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

    /** Returns the raw value of an arbitrary extension key (including unknown ones), or null. */
    fun extension(key: String): String? = extensions[key]

    /** Returns true when there are no free-text terms (the query is extensions-only or empty). */
    fun isTermsEmpty(): Boolean = terms.isEmpty()

    /**
     * Re-assembles a canonical NIP-50 search string: the free-text [terms]
     * followed by each `key:value` extension. Useful for a redirector that
     * normalizes the incoming query before forwarding it to a backend.
     */
    fun toSearchString(): String =
        buildString {
            append(terms)
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

        /** Empty query — no terms and no extensions. */
        val EMPTY = SearchQuery("", emptyMap())

        /**
         * Parses a raw NIP-50 [search] string into a [SearchQuery]. A null or
         * blank input yields [EMPTY].
         */
        fun parse(search: String?): SearchQuery {
            if (search.isNullOrBlank()) return EMPTY

            val extensions = LinkedHashMap<String, String>()
            val terms = StringBuilder()

            for (token in search.trim().split(WHITESPACE)) {
                val colon = token.indexOf(':')
                if (colon > 0 && colon < token.length - 1) {
                    val key = token.substring(0, colon)
                    val value = token.substring(colon + 1)
                    if (isExtensionKey(key) && !value.startsWith("//")) {
                        extensions[key] = value
                        continue
                    }
                }
                if (terms.isNotEmpty()) terms.append(' ')
                terms.append(token)
            }

            return SearchQuery(terms.toString(), extensions)
        }

        private fun isExtensionKey(key: String): Boolean = key.isNotEmpty() && key.all { it in 'a'..'z' }
    }
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
