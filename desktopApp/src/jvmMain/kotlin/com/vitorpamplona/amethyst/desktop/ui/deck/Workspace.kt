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
package com.vitorpamplona.amethyst.desktop.ui.deck

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.LayoutMode

data class Workspace(
    val id: String =
        java.util.UUID
            .randomUUID()
            .toString(),
    val name: String,
    val iconName: String,
    val layoutMode: LayoutMode,
    val columns: List<WorkspaceColumn>,
    val singlePaneScreens: List<String> = emptyList(),
) {
    data class WorkspaceColumn(
        val typeKey: String,
        val param: String? = null,
        val width: Float = 400f,
    )
}

object WorkspaceIcons {
    private val icons: Map<String, MaterialSymbol> =
        mapOf(
            "Groups" to MaterialSymbols.Groups,
            "Edit" to MaterialSymbols.Edit,
            "MenuBook" to MaterialSymbols.MenuBook,
            "Home" to MaterialSymbols.Home,
            "Chat" to MaterialSymbols.Chat,
            "Search" to MaterialSymbols.Search,
            "SportsEsports" to MaterialSymbols.SportsEsports,
            "Bookmark" to MaterialSymbols.Bookmark,
            "Explore" to MaterialSymbols.Explore,
            "Person" to MaterialSymbols.Person,
            "Star" to MaterialSymbols.Star,
            "Favorite" to MaterialSymbols.Favorite,
            "Work" to MaterialSymbols.Work,
            "Code" to MaterialSymbols.Code,
        )

    val availableNames: List<String> = icons.keys.sorted()

    fun resolve(name: String): MaterialSymbol = icons[name] ?: MaterialSymbols.Home
}
