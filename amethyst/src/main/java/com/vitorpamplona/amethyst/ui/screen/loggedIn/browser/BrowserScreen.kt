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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.OmniboxSuggestions
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.favorites.BrowserHistoryEntry
import com.vitorpamplona.amethyst.favorites.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.favoriteAppItems
import com.vitorpamplona.amethyst.commons.R as CommonsR

/** How many of the most recent history entries the idle browser home surfaces under "Recent". */
private const val RECENTS_LIMIT = 12

/**
 * The Browser tab — a **launcher**, not a content surface. The user types a URL here and each opened
 * site lands in its own full-screen
 * [NappletBrowserActivity][com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity] (its own
 * task/recents entry), so apps are swapped the normal Android way and a running app never carries an
 * editable address bar.
 *
 * Idle, the body is the [BrowserHome]: pinned favorites on top, then recent visits. As the user types it
 * becomes a grouped omnibox suggestion list (favorites first + highlighted, then recents) with inline
 * ghost-text completion. Both decorate sites with the favicon captured when they were last opened.
 *
 * Works on any API level: each site loads in a full-screen direct-WebView activity, never the
 * cross-process SurfaceControlViewHost surface that the *embedded* favorite-app tabs require (API 30+).
 */
@Composable
fun BrowserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    BrowserLauncher(accountViewModel, nav)
}

@Composable
private fun BrowserLauncher(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    val history by BrowserHistoryRegistry.history.collectAsStateWithLifecycle()
    val iconKeys by BrowserIconRegistry.keys.collectAsStateWithLifecycle()

    var field by remember { mutableStateOf(TextFieldValue("")) }

    // Favorites + visit history flattened into the neutral candidate shape the ranker consumes.
    val candidates =
        remember(apps, history) {
            buildList {
                apps.forEach { if (it is FavoriteApp.WebApp) add(OmniboxSuggestions.Candidate(it.url, it.label, isFavorite = true)) }
                history.forEach {
                    add(
                        OmniboxSuggestions.Candidate(
                            url = it.url,
                            label = it.title.ifBlank { it.host },
                            isFavorite = false,
                            visitCount = it.visitCount,
                            lastVisitedAt = it.lastVisitedAt,
                        ),
                    )
                }
            }
        }

    // What the user actually typed, excluding any selected ghost-completion suffix (selection.min is the
    // caret when collapsed, or the start of the highlighted suffix when a completion is showing).
    val typed = field.text.take(field.selection.min.coerceIn(0, field.text.length))
    val suggestions = remember(typed, candidates) { OmniboxSuggestions.rank(typed, candidates) }

    fun open(text: String) {
        val target = OmniboxInput.resolve(text) ?: return
        FavoriteAppLauncher.launchUrl(context, target.url, target.forceTor)
    }

    // Inline autocomplete: when the user appends a character, offer the top host as selected ghost text so
    // the next keystroke replaces it. On deletion or mid-string edits, leave the value untouched.
    fun onValueChange(new: TextFieldValue) {
        val prevTyped = field.text.take(field.selection.min.coerceIn(0, field.text.length))
        val newText = new.text
        val appended =
            new.selection.collapsed &&
                new.selection.start == newText.length &&
                newText.length > prevTyped.length &&
                newText.startsWith(prevTyped)
        if (appended) {
            val completion = OmniboxSuggestions.completion(newText, OmniboxSuggestions.rank(newText, candidates))
            if (completion != null) {
                // Keep the user's own casing for the typed prefix; append only the remaining suffix.
                val full = newText + completion.substring(newText.length)
                field = TextFieldValue(full, TextRange(newText.length, full.length))
                return
            }
        }
        field = new
    }

    Scaffold(
        topBar = {
            OmniBar(
                nav = nav,
                field = field,
                onValueChange = ::onValueChange,
                onClear = { field = TextFieldValue("") },
                onOpen = { open(field.text) },
            )
        },
        bottomBar = {
            AppBottomBar(Route.Browser, nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
        when {
            typed.isNotBlank() && suggestions.isNotEmpty() ->
                SuggestionGrid(
                    suggestions = suggestions,
                    iconKeys = iconKeys,
                    onOpen = { open(it.url) },
                    modifier = contentModifier,
                )
            apps.isEmpty() && history.isEmpty() ->
                Box(
                    contentModifier.padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.favorite_apps_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> {
                val favoriteUrls = remember(apps) { apps.filterIsInstance<FavoriteApp.WebApp>().mapTo(HashSet()) { it.url } }
                BrowserHome(
                    apps = apps,
                    history = history,
                    iconKeys = iconKeys,
                    favoriteUrls = favoriteUrls,
                    onOpenApp = { FavoriteAppLauncher.launch(context, it) },
                    onRemoveApp = { FavoriteAppsRegistry.remove(it.id) },
                    onOpenUrl = { open(it) },
                    onToggleRecentFavorite = { entry ->
                        val id = "url:" + entry.url
                        if (FavoriteAppsRegistry.isFavorite(id)) {
                            FavoriteAppsRegistry.remove(id)
                        } else {
                            FavoriteAppsRegistry.add(
                                FavoriteApp.WebApp(entry.url, entry.title.ifBlank { entry.host }, System.currentTimeMillis()),
                            )
                        }
                    },
                    onRemoveRecent = { BrowserHistoryRegistry.remove(it) },
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun OmniBar(
    nav: INav,
    field: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onOpen: () -> Unit,
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
        // When reached from the drawer (pushed onto the back stack) rather than as a bottom-bar tab, show
        // a back arrow — same rule as the other launcher/feed top bars (see NappletsTopBar).
        if (nav.canPop()) {
            IconButton(onClick = nav::popBack) { ArrowBackIcon() }
        }
        TextField(
            value = field,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(CommonsR.string.browser_address_hint)) },
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { onOpen() }),
            trailingIcon = {
                if (field.text.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(MaterialSymbols.Clear, contentDescription = stringResource(R.string.browser_clear))
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
        if (field.text.isNotBlank()) {
            IconButton(onClick = onOpen) {
                Icon(MaterialSymbols.AutoMirrored.ArrowForward, contentDescription = stringResource(R.string.browser_go))
            }
        }
    }
}

/** The typed-state body: ranked suggestions split into a highlighted Favorites group then Recent. */
@Composable
private fun SuggestionGrid(
    suggestions: List<OmniboxSuggestions.Suggestion>,
    iconKeys: Set<String>,
    onOpen: (OmniboxSuggestions.Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites = suggestions.filter { it.isFavorite }
    val others = suggestions.filterNot { it.isFavorite }
    LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = modifier) {
        if (favorites.isNotEmpty()) {
            item(key = "h-fav") { SectionHeader(stringResource(R.string.browser_favorites)) }
            items(favorites, key = { "f:" + it.url }) { SuggestionRow(it, iconKeys, highlighted = true) { onOpen(it) } }
        }
        if (others.isNotEmpty()) {
            item(key = "h-rec") { SectionHeader(stringResource(R.string.favorite_app_recent)) }
            items(others, key = { "o:" + it.url }) { SuggestionRow(it, iconKeys, highlighted = false) { onOpen(it) } }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: OmniboxSuggestions.Suggestion,
    iconKeys: Set<String>,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(suggestion.host, suggestion.isFavorite, iconKeys, Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.host,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.label.isNotBlank() && !suggestion.label.equals(suggestion.host, ignoreCase = true)) {
                Text(
                    suggestion.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The idle body: favorites grid on top, then recent visits — in one grid so they scroll together. */
@Composable
private fun BrowserHome(
    apps: List<FavoriteApp>,
    history: List<BrowserHistoryEntry>,
    iconKeys: Set<String>,
    favoriteUrls: Set<String>,
    onOpenApp: (FavoriteApp) -> Unit,
    onRemoveApp: (FavoriteApp) -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleRecentFavorite: (BrowserHistoryEntry) -> Unit,
    onRemoveRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recents = remember(history) { history.take(RECENTS_LIMIT) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (apps.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-fav") { SectionHeader(stringResource(R.string.browser_favorites)) }
            favoriteAppItems(apps, onOpenApp, onRemoveApp)
        }
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-rec") { SectionHeader(stringResource(R.string.favorite_app_recent)) }
            items(recents, span = { GridItemSpan(maxLineSpan) }, key = { "r:" + it.url }) { entry ->
                RecentRow(
                    entry = entry,
                    iconKeys = iconKeys,
                    isFavorited = entry.url in favoriteUrls,
                    onClick = { onOpenUrl(entry.url) },
                    onToggleFavorite = { onToggleRecentFavorite(entry) },
                    onRemove = { onRemoveRecent(entry.url) },
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    entry: BrowserHistoryEntry,
    iconKeys: Set<String>,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(entry.host, isFavorite = isFavorited, iconKeys = iconKeys, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.ifBlank { entry.host },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.MoreVert, contentDescription = stringResource(R.string.browser_recent_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (isFavorited) R.string.favorite_app_remove else R.string.favorite_app_add)) },
                    leadingIcon = {
                        Icon(if (isFavorited) MaterialSymbols.Star else MaterialSymbols.StarBorder, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavorite()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_recent_remove)) },
                    leadingIcon = { Icon(MaterialSymbols.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A site's captured favicon, falling back to a glyph (a star for favorites, the globe otherwise). */
@Composable
private fun SiteIcon(
    host: String,
    isFavorite: Boolean,
    iconKeys: Set<String>,
    modifier: Modifier = Modifier,
) {
    val model = remember(host, iconKeys) { BrowserIconRegistry.iconModelFor(host) }
    val symbol = if (isFavorite) MaterialSymbols.Star else MaterialSymbols.Public
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (model == null) {
        Icon(symbol, contentDescription = null, modifier = modifier, tint = tint)
    } else {
        val glyph = rememberMaterialSymbolPainter(symbol, tint)
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            placeholder = glyph,
            error = glyph,
            fallback = glyph,
        )
    }
}

/** The host of [url] for a favorite's default label, falling back to the raw string. */
private fun hostOf(url: String): String = OmniboxInput.hostOf(url) ?: url
