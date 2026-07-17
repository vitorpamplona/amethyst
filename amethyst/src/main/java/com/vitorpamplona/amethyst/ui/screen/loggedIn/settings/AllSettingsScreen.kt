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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Preview
@Composable
fun AllSettingsScreenPreview() {
    ThemeComparisonColumn {
        AllSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showResetMarmotDialog by remember { mutableStateOf(false) }
    var isResettingMarmot by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val hasPrivateKey = accountViewModel.account.settings.keyPair.privKey != null

    val searchState = rememberTextFieldState()
    val query = searchState.text.toString()
    var searchExpanded by remember { mutableStateOf(false) }

    // Single source of truth for the search bar's expanded/collapsed transitions. Collapsing —
    // whether by the back arrow, the system back gesture, or picking a result — always clears the
    // query so the field returns empty the next time it opens.
    val onExpandedChange: (Boolean) -> Unit = { expanded ->
        searchExpanded = expanded
        if (!expanded) searchState.clearText()
    }

    // While the results are open, the system back gesture collapses the bar instead of leaving the
    // screen. Disabled otherwise so back behaves normally.
    BackHandler(enabled = searchExpanded) { onExpandedChange(false) }

    // The catalog is structurally stable for the screen's lifetime, so it is rebuilt only when
    // an input actually changes — not on every keystroke. `onResetMarmot` reads the volatile
    // `isResettingMarmot` through `rememberUpdatedState` so the memoized closure never goes stale.
    val onResetMarmot by rememberUpdatedState(newValue = { if (!isResettingMarmot) showResetMarmotDialog = true })
    val catalog =
        remember(hasPrivateKey, nav, uriHandler) {
            buildSettingsCatalog(
                nav = nav,
                uriHandler = uriHandler,
                hasPrivateKey = hasPrivateKey,
                onResetMarmot = { onResetMarmot() },
            )
        }

    val filtered =
        filterSettings(
            catalog = catalog,
            query = query,
            stringLookup = { stringRes(context, it) },
        )

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.settings), nav)
        },
        bottomBar = {
            AppBottomBar(Route.AllSettings, nav, accountViewModel) { route ->
                if (route == Route.AllSettings) {
                    scope.launch { scrollState.animateScrollTo(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        state = searchState,
                        onSearch = {},
                        expanded = searchExpanded,
                        onExpandedChange = onExpandedChange,
                        placeholder = { Text(stringRes(R.string.settings_search_placeholder)) },
                        leadingIcon = {
                            if (searchExpanded) {
                                IconButton(onClick = { onExpandedChange(false) }) {
                                    ArrowBackIcon()
                                }
                            } else {
                                Icon(
                                    symbol = MaterialSymbols.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { searchState.clearText() }) {
                                    Icon(
                                        symbol = MaterialSymbols.Close,
                                        contentDescription = stringRes(R.string.clear),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = onExpandedChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Expanded: filtered results are shown inside the docked bar's dropdown. A blank
                // query lists the whole catalog, narrowing as the user types.
                if (filtered.isEmpty()) {
                    SettingsSearchEmptyState(
                        query = query,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(filtered) { category ->
                            SettingsCategoryCard(
                                category = category,
                                onEntryClick = { onExpandedChange(false) },
                            )
                        }
                    }
                }
            }

            // Collapsed: the full categorized settings list. Hidden while the results dropdown is
            // open so it doesn't peek out beneath it.
            if (!searchExpanded) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    catalog.forEach { category -> SettingsCategoryCard(category) }
                }
            }
        }
    }

    if (showResetMarmotDialog) {
        ResetMarmotStateDialog(
            onConfirm = {
                showResetMarmotDialog = false
                isResettingMarmot = true
                scope.launch(Dispatchers.IO) {
                    val successMessage = stringRes(context, R.string.reset_marmot_success)
                    try {
                        accountViewModel.resetMarmotState()
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        val failureMessage =
                            stringRes(context, R.string.reset_marmot_failure, e.message ?: "")
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isResettingMarmot = false
                    }
                }
            },
            onDismiss = { showResetMarmotDialog = false },
        )
    }
}

@Composable
private fun SettingsSearchEmptyState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringRes(R.string.settings_search_no_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    category: SettingsCategory,
    onEntryClick: () -> Unit = {},
) {
    SettingsSection(category.titleRes, category.isDanger) {
        category.entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            SettingsEntryRow(entry, onEntryClick)
        }
    }
}

@Composable
private fun SettingsEntryRow(
    entry: SettingsEntry,
    onEntryClick: () -> Unit = {},
) {
    val onClick = {
        onEntryClick()
        entry.onClick()
    }
    when (val icon = entry.icon) {
        is SettingsIcon.Symbol ->
            SettingsItem(
                title = entry.titleRes,
                icon = icon.symbol,
                isDanger = entry.isDanger,
                onClick = onClick,
            )
        is SettingsIcon.Painter ->
            SettingsItem(
                title = entry.titleRes,
                iconPainter = icon.iconPainter,
                iconPainterRef = icon.iconPainterRef,
                isDanger = entry.isDanger,
                onClick = onClick,
            )
    }
}

@Composable
private fun ResetMarmotStateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(R.string.reset_marmot_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = stringRes(R.string.reset_marmot_confirm_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(R.string.reset_marmot_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
