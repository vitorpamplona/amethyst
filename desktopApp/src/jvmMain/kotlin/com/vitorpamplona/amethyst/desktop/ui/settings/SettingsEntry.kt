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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import kotlinx.collections.immutable.ImmutableList

/**
 * Pure, searchable metadata for one entry in the Settings accordion.
 *
 * Kept UI-free and free of any Compose slot so [matches] is trivially unit
 * testable and the type stays a value-comparable [Immutable] data class. The
 * matcher indexes the visible [title], the [subtitle], and a curated [keywords]
 * list that also carries action synonyms (e.g. "reconnect", "connect wallet")
 * so a user typing an action term still surfaces the card that hosts it.
 */
@Immutable
data class SettingsMeta(
    val id: String,
    val icon: MaterialSymbol,
    val title: String,
    val subtitle: String,
    val keywords: ImmutableList<String>,
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        return title.contains(q, ignoreCase = true) ||
            subtitle.contains(q, ignoreCase = true) ||
            keywords.any { it.contains(q, ignoreCase = true) }
    }
}

/**
 * One accordion entry: its searchable [meta] plus the composable [content] that
 * renders the section body when the card is expanded.
 *
 * [Immutable] is an honest promise here — every field except [content] is a
 * `val` of a stable type, and a stable holder may carry a composable member.
 * That lets the accordion cards skip on unrelated recompositions as long as the
 * entry list is remembered (see the screen's builder).
 */
@Immutable
class SettingsEntry(
    val meta: SettingsMeta,
    val content: @Composable () -> Unit,
)
