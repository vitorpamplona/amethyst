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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * The Favorite Apps grid: the user's pinned web clients / nsites / napplets as big launch buttons.
 * Reached as its own bottom-bar tab, and the same [FavoriteAppsGrid] is reused inside the browser
 * launcher. Tap launches via [FavoriteAppLauncher] (full-screen activity for a URL; sandboxed host for
 * an nsite/napplet); long-press removes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteAppsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.favorite_apps)) })
        },
        bottomBar = {
            AppBottomBar(Route.FavoriteApps, nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        if (apps.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.favorite_apps_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            FavoriteAppsGrid(
                apps = apps,
                onOpen = { FavoriteAppLauncher.launch(context, it) },
                onRemove = { FavoriteAppsRegistry.remove(it.id) },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            )
        }
    }
}

/**
 * A reusable adaptive grid of favorite-app launch buttons. Stateless: the caller owns the list and the
 * open/remove actions, so it drops into the Favorite Apps tab and the browser launcher unchanged.
 */
@Composable
fun FavoriteAppsGrid(
    apps: List<FavoriteApp>,
    onOpen: (FavoriteApp) -> Unit,
    onRemove: (FavoriteApp) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        favoriteAppItems(apps, onOpen, onRemove)
    }
}

/**
 * Emits the favorite-app cells into any [LazyVerticalGrid] (the Favorite Apps tab, the browser home),
 * so callers can mix them with their own headers/sections in a single grid.
 */
fun LazyGridScope.favoriteAppItems(
    apps: List<FavoriteApp>,
    onOpen: (FavoriteApp) -> Unit,
    onRemove: (FavoriteApp) -> Unit,
) {
    items(apps, key = { it.id }) { app ->
        FavoriteAppCell(
            app = app,
            onOpen = { onOpen(app) },
            onRemove = { onRemove(app) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteAppCell(
    app: FavoriteApp,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // For a plain web favorite, prefer the favicon captured when its site was opened; nsites/napplets keep
    // their manifest icon. Observing the key set recomputes the model as an icon arrives.
    val iconKeys by BrowserIconRegistry.keys.collectAsStateWithLifecycle()
    val faviconModel =
        remember(app, iconKeys) {
            (app as? FavoriteApp.WebUrl)?.let { OmniboxInput.hostOf(it.url)?.let(BrowserIconRegistry::iconModelFor) }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuOpen = true },
                ).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            FavoriteAppIcon(
                app = app,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
                iconModel = faviconModel,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_app_remove)) },
                leadingIcon = { Icon(MaterialSymbols.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
