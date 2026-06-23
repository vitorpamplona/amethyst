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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.FavoriteAppsGrid

/**
 * The Browser tab — a **launcher**, not a content surface. The user types a URL here and each opened
 * site lands in its own full-screen [BrowserHostActivity] (its own task/recents entry), so apps are
 * swapped the normal Android way and a running app never carries an editable address bar. Below the
 * omnibox sits the shared [FavoriteAppsGrid] for one-tap access to pinned clients.
 *
 * Requires API 30+ (the host activity embeds a SurfaceControlViewHost surface); below that the Browser
 * nav item is hidden, so this screen is unreachable — the fallback message is just defense in depth.
 */
@Composable
fun BrowserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BrowserLauncher(accountViewModel, nav)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowserLauncher(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }

    fun open() {
        val url = normalizeUrl(query) ?: return
        FavoriteAppLauncher.launchUrl(context, url)
    }

    Scaffold(
        topBar = {
            OmniBar(
                query = query,
                onQueryChange = { query = it },
                onOpen = ::open,
                onFavorite = {
                    val url = normalizeUrl(query) ?: return@OmniBar
                    FavoriteAppsRegistry.add(
                        FavoriteApp.WebUrl(url = url, label = hostOf(url), addedAt = System.currentTimeMillis()),
                    )
                },
            )
        },
        bottomBar = {
            AppBottomBar(Route.Browser, nav, accountViewModel) { route -> nav.navBottomBar(route) }
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

@Composable
private fun OmniBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // The omnibox is a plain Row in the topBar slot (not a Material3 TopAppBar), so it must
                // apply the status-bar inset itself — otherwise it draws under the status bar.
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.browser_address_hint)) },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onOpen() }),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
        if (query.isNotBlank()) {
            IconButton(onClick = onFavorite) {
                Icon(MaterialSymbols.StarBorder, contentDescription = stringResource(R.string.favorite_app_add))
            }
            IconButton(onClick = onOpen) {
                Icon(MaterialSymbols.AutoMirrored.ArrowForward, contentDescription = stringResource(R.string.browser_go))
            }
        }
    }
}

/** The host of [url] for a favorite's default label, falling back to the raw string. */
private fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

/**
 * Turns raw omnibox text into a loadable URL: trims, rejects blanks, and prepends `https://` when no
 * scheme is present (so `example.com` works). Returns null when there's nothing to open.
 */
private fun normalizeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.contains("://")) trimmed else "https://$trimmed"
}
