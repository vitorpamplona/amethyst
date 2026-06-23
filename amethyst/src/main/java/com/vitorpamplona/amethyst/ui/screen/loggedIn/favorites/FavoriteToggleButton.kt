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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites

import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry

/**
 * A star toggle that pins/unpins an nsite or napplet (a [FavoriteApp.NostrApp]) by its addressable
 * [coordinate]. Drops into the shared static-site card's header-actions slot; the favorite store and
 * its strings stay here in the app layer, never in the shared card.
 */
@Composable
fun FavoriteToggleButton(
    coordinate: String,
    label: String,
    iconUrl: String? = null,
) {
    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    val id = "nostr:$coordinate"
    val isFavorite = remember(apps, id) { apps.any { it.id == id } }

    IconButton(
        onClick = {
            if (isFavorite) {
                FavoriteAppsRegistry.remove(id)
            } else {
                FavoriteAppsRegistry.add(FavoriteApp.NostrApp(coordinate, label, System.currentTimeMillis(), iconUrl))
            }
        },
    ) {
        Icon(
            if (isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder,
            contentDescription = stringResource(if (isFavorite) R.string.favorite_app_remove else R.string.favorite_app_add),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
