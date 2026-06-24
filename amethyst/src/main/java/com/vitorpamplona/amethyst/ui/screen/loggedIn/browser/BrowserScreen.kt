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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.OmniboxSuggestions
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.favorites.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.FavoriteAppsGrid

/**
 * The Browser tab — a **launcher**, not a content surface. The user types a URL here and each opened
 * site lands in its own full-screen
 * [NappletBrowserActivity][com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity] (its own
 * task/recents entry), so apps are swapped the normal Android way and a running app never carries an
 * editable address bar. As the user types, the body becomes an omnibox suggestion list (favorites +
 * visit history, ranked) with inline ghost-text completion; cleared, it shows the [FavoriteAppsGrid].
 *
 * Requires API 30+ (the keyless `:napplet` browser host needs it); below that the Browser nav item is
 * hidden, so this screen is unreachable — the fallback message is just defense in depth.
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
    val history by BrowserHistoryRegistry.history.collectAsStateWithLifecycle()

    var field by remember { mutableStateOf(TextFieldValue("")) }

    // Favorites + visit history flattened into the neutral candidate shape the ranker consumes.
    val candidates =
        remember(apps, history) {
            buildList {
                apps.forEach { if (it is FavoriteApp.WebUrl) add(OmniboxSuggestions.Candidate(it.url, it.label, isFavorite = true)) }
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
                onFavorite = {
                    val url = OmniboxInput.resolve(field.text)?.url ?: return@OmniBar
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
        when {
            typed.isNotBlank() && suggestions.isNotEmpty() ->
                SuggestionList(
                    suggestions = suggestions,
                    onOpen = { open(it.url) },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                )
            apps.isEmpty() ->
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
            else ->
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
    nav: INav,
    field: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
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
            placeholder = { Text(stringResource(R.string.browser_address_hint)) },
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
            IconButton(onClick = onFavorite) {
                Icon(MaterialSymbols.StarBorder, contentDescription = stringResource(R.string.favorite_app_add))
            }
            IconButton(onClick = onOpen) {
                Icon(MaterialSymbols.AutoMirrored.ArrowForward, contentDescription = stringResource(R.string.browser_go))
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<OmniboxSuggestions.Suggestion>,
    onOpen: (OmniboxSuggestions.Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(suggestions, key = { it.url }) { suggestion ->
            SuggestionRow(suggestion) { onOpen(suggestion) }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: OmniboxSuggestions.Suggestion,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A star marks a pinned favorite; otherwise it came from visit history.
        Icon(
            if (suggestion.isFavorite) MaterialSymbols.Star else MaterialSymbols.History,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.host,
                style = MaterialTheme.typography.bodyLarge,
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

/** The host of [url] for a favorite's default label, falling back to the raw string. */
private fun hostOf(url: String): String = OmniboxInput.hostOf(url) ?: runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
