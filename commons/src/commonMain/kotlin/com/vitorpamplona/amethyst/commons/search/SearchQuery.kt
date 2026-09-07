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
package com.vitorpamplona.amethyst.commons.search

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One NIP-73 external scope a `site:`/`isbn:`/`geo:`/`isan:`/`doi:`/`podcast:*` token names. */
@Immutable
data class ExternalScope(
    val field: String,
    val value: String,
) {
    /** The spellings of this scope worth asking an `#i`/`#I` filter for, canonical first. */
    fun ids(): List<String> = ScopeIds.scopeIds(field, value)

    /** The token this scope is written as inside the field. */
    fun token(): String = "$field:$value"
}

/**
 * What the relay is asked, from what the reader typed. Every field here becomes a NIP-01 filter
 * field rather than a NIP-50 extension — see [SearchFilterBuilder] — so a query composes with a
 * relay's own ranking instead of competing with it. [text] is what is left over for NIP-50 once
 * every token has been lifted out.
 */
@Immutable
data class SearchQuery(
    val text: String = "",
    val authors: ImmutableList<String> = persistentListOf(),
    val authorNames: ImmutableList<String> = persistentListOf(),
    val kinds: ImmutableList<Int> = persistentListOf(),
    val since: Long? = null,
    val until: Long? = null,
    val hashtags: ImmutableList<String> = persistentListOf(),
    val excludeTerms: ImmutableList<String> = persistentListOf(),
    val language: String? = null,
    val domain: String? = null,
    val orTerms: ImmutableList<String> = persistentListOf(),
    val pseudoKinds: ImmutableList<String> = persistentListOf(),
    /** `to:<npub>` — people the event mentions, asked with `#p`. */
    val mentions: ImmutableList<String> = persistentListOf(),
    /** `to:<note|nevent>` — events the event cites, asked with `#e`. */
    val cites: ImmutableList<String> = persistentListOf(),
    /** `to:<naddr>` — addressable events the event cites, asked with `#a`. */
    val addrs: ImmutableList<String> = persistentListOf(),
    /** `label:<mark>` — NIP-32 marks, asked with `#l` on kind 1985. */
    val labels: ImmutableList<String> = persistentListOf(),
    /** NIP-73 external scopes, asked with `#i`/`#I` on NIP-22 comments. */
    val scopes: ImmutableList<ExternalScope> = persistentListOf(),
    /** `group:<id>` — NIP-29 groups, asked with `#h`. */
    val groups: ImmutableList<String> = persistentListOf(),
) {
    val isEmpty
        get() =
            text.isBlank() &&
                authors.isEmpty() &&
                authorNames.isEmpty() &&
                kinds.isEmpty() &&
                since == null &&
                until == null &&
                hashtags.isEmpty() &&
                orTerms.isEmpty() &&
                excludeTerms.isEmpty() &&
                pseudoKinds.isEmpty() &&
                language == null &&
                domain == null &&
                mentions.isEmpty() &&
                cites.isEmpty() &&
                addrs.isEmpty() &&
                labels.isEmpty() &&
                scopes.isEmpty() &&
                groups.isEmpty()

    companion object {
        val EMPTY = SearchQuery()
    }
}
