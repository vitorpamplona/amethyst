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

@Immutable
data class SearchQuery(
    val text: String = "",
    val authors: ImmutableList<String> = persistentListOf(),
    val authorNames: ImmutableList<String> = persistentListOf(),
    val recipients: ImmutableList<String> = persistentListOf(),
    val recipientNames: ImmutableList<String> = persistentListOf(),
    val kinds: ImmutableList<Int> = persistentListOf(),
    val since: Long? = null,
    val until: Long? = null,
    val hashtags: ImmutableList<String> = persistentListOf(),
    val excludeTerms: ImmutableList<String> = persistentListOf(),
    val language: String? = null,
    val domain: String? = null,
    val orTerms: ImmutableList<String> = persistentListOf(),
    val pseudoKinds: ImmutableList<String> = persistentListOf(),
) {
    val isEmpty
        get() =
            text.isBlank() &&
                authors.isEmpty() &&
                authorNames.isEmpty() &&
                recipients.isEmpty() &&
                recipientNames.isEmpty() &&
                kinds.isEmpty() &&
                since == null &&
                until == null &&
                hashtags.isEmpty() &&
                orTerms.isEmpty() &&
                excludeTerms.isEmpty() &&
                pseudoKinds.isEmpty() &&
                language == null &&
                domain == null

    companion object {
        val EMPTY = SearchQuery()
    }
}
