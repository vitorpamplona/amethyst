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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
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
    private val icons: Map<String, ImageVector> =
        mapOf(
            "Groups" to Icons.Default.Groups,
            "Edit" to Icons.Default.Edit,
            "MenuBook" to Icons.Default.MenuBook,
            "Home" to Icons.Default.Home,
            "Chat" to Icons.Default.Chat,
            "Search" to Icons.Default.Search,
            "SportsEsports" to Icons.Default.SportsEsports,
            "Bookmark" to Icons.Default.Bookmark,
            "Explore" to Icons.Default.Explore,
            "Person" to Icons.Default.Person,
            "Star" to Icons.Default.Star,
            "Favorite" to Icons.Default.Favorite,
            "Work" to Icons.Default.Work,
            "Code" to Icons.Default.Code,
        )

    val availableNames: List<String> = icons.keys.sorted()

    fun resolve(name: String): ImageVector = icons[name] ?: Icons.Default.Home
}
