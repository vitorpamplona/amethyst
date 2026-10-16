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
package com.vitorpamplona.amethyst.commons.ui.spellcheck

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.TextContextMenuArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocalization
import androidx.compose.ui.platform.PlatformLocalization
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.spell_check_add_to_dictionary
import com.vitorpamplona.amethyst.commons.resources.spell_check_no_suggestions
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Swaps the field's text context menu for one that lists the right-clicked word's suggestions
 * first, then "Add to dictionary", then the usual Cut/Copy/Paste/Select All.
 *
 * Compose Desktop's own way to add entries (`ContextMenuDataProvider`) can only append them after
 * Select All, and its newer `appendTextContextMenuComponents` is off unless
 * `ComposeFoundationFlags.isNewContextMenuEnabled` is turned on for the whole app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun SpellCheckContextMenu(
    spell: SpellCheckFieldState?,
    content: @Composable () -> Unit,
) {
    if (spell == null) {
        content()
        return
    }

    val noSuggestions = stringRes(Res.string.spell_check_no_suggestions)
    val addToDictionary = stringRes(Res.string.spell_check_add_to_dictionary)
    val menu = remember(spell, noSuggestions, addToDictionary) { SpellCheckTextContextMenu(spell, noSuggestions, addToDictionary) }

    CompositionLocalProvider(LocalTextContextMenu provides menu, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
private class SpellCheckTextContextMenu(
    private val spell: SpellCheckFieldState,
    private val noSuggestionsLabel: String,
    private val addToDictionaryLabel: String,
) : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit,
    ) {
        val localization = LocalLocalization.current
        TextContextMenuArea(
            textManager = textManager,
            items = {
                val spelling =
                    spellCheckMenuEntries(spell, noSuggestionsLabel, addToDictionaryLabel).map {
                        ContextMenuItem(it.label, it.enabled, it.onClick)
                    }
                spelling + editingItems(textManager, localization)
            },
            state = state,
            content = content,
        )
    }

    // The same four entries, in the same order, as Compose's TextContextMenu.Default.
    private fun editingItems(
        textManager: TextContextMenu.TextManager,
        localization: PlatformLocalization,
    ): List<ContextMenuItem> =
        listOf(
            localization.cut to textManager.cut,
            localization.copy to textManager.copy,
            localization.paste to textManager.paste,
            localization.selectAll to textManager.selectAll,
        ).mapNotNull { (label, action) ->
            action?.let { ContextMenuItem(label, it.enabled, it.execute) }
        }
}
