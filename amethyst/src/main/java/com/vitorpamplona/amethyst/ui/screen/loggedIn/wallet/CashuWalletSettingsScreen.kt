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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import androidx.compose.material3.Icon as Material3Icon

/**
 * Settings hub for the Cashu wallet. Lives behind the gear icon on the
 * main wallet screen. Hosts:
 *
 *  - "Edit wallet details" → routes to the AddCashuWallet form in edit mode
 *    (which already detects an existing kind:17375 and pre-fills).
 *  - "My recommendations" → list of NIP-87 kind:38000 events this account
 *    has published, each with a button to NIP-09 retract it. The list is
 *    fed by [CashuWalletState.ownRecommendations], which the wallet's own
 *    filter assembler pulls automatically.
 *
 * Future placeholders (auto-recommend toggle, nutzap relay overrides,
 * export/backup) belong here too — keeping all wallet-shaped knobs in
 * one place avoids re-cluttering the main wallet screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuWalletSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = viewModel()
    viewModel.init(accountViewModel)

    val recommendations by viewModel.ownRecommendations.collectAsState()
    var pendingDelete by remember { mutableStateOf<MintRecommendationEvent?>(null) }
    var newRecommendationInput by remember { mutableStateOf("") }

    // Kick the one-shot directory backfill so the autocomplete is useful
    // on first screen open instead of waiting for new relay deliveries.
    LaunchedEffect(Unit) { LocalCache.ensureMintDirectoryBackfilled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.cashu_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsRow(
                    icon = MaterialSymbols.Edit,
                    title = stringRes(R.string.cashu_settings_edit_wallet),
                    subtitle = stringRes(R.string.cashu_settings_edit_wallet_subtitle),
                    onClick = { nav.nav(Route.WalletAddCashu) },
                )
            }

            // NUT-09 recovery: ask every mint in our list which blind
            // signatures it has previously issued for our seed and
            // republish them as fresh kind:7375 events. Useful after a
            // device loss or rogue-relay NIP-09 of our token events.
            item {
                val restoreState by viewModel.restoreState.collectAsState()
                val restoreSubtitle =
                    when (val s = restoreState) {
                        is CashuWalletViewModel.RestoreFlowState.Idle ->
                            stringRes(R.string.cashu_settings_restore_subtitle)
                        is CashuWalletViewModel.RestoreFlowState.Running ->
                            stringRes(R.string.cashu_settings_restore_running)
                        is CashuWalletViewModel.RestoreFlowState.Completed ->
                            if (s.totalSatsRecovered == 0L) {
                                stringRes(R.string.cashu_settings_restore_nothing_found)
                            } else {
                                stringRes(
                                    R.string.cashu_settings_restore_result,
                                    s.totalSatsRecovered.toString(),
                                    s.proofsRecovered.toString(),
                                )
                            }
                        is CashuWalletViewModel.RestoreFlowState.Error -> s.message
                    }
                SettingsRow(
                    icon = MaterialSymbols.CloudDownload,
                    title = stringRes(R.string.cashu_settings_restore_title),
                    subtitle = restoreSubtitle,
                    onClick = {
                        if (restoreState !is CashuWalletViewModel.RestoreFlowState.Running) {
                            viewModel.restoreFromAllMints()
                        }
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.cashu_settings_my_recommendations),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Add-recommendation input + autocomplete. Suggestions come
            // from LocalCache.mintDirectory (kind:10019 / kind:38000 /
            // kind:38172 the cache has seen), filtered to drop URLs the
            // user has already recommended so they can't double-publish
            // and the same row doesn't show up twice on screen.
            item {
                val alreadyRecommended =
                    remember(recommendations) {
                        recommendations
                            .flatMap { it.mintUrls() }
                            .map { it.lowercase().trimEnd('/') }
                            .toSet()
                    }
                val suggestions by remember(newRecommendationInput, alreadyRecommended) {
                    derivedStateOf {
                        val typed = newRecommendationInput.trim().trimEnd('/').lowercase()
                        // Only react to what the user types — never show
                        // the full directory on an empty field, which
                        // would dump every mint we've ever seen as
                        // unsolicited suggestions.
                        if (typed.isEmpty()) {
                            emptyList()
                        } else {
                            LocalCache.mintDirectory
                                .suggest(typed, limit = 6)
                                .filter { it != typed && it !in alreadyRecommended }
                        }
                    }
                }
                AddRecommendationRow(
                    input = newRecommendationInput,
                    onInputChange = { newRecommendationInput = it },
                    canAdd =
                        newRecommendationInput.isNotBlank() &&
                            newRecommendationInput.trim().trimEnd('/').lowercase() !in alreadyRecommended,
                    onAdd = {
                        val trimmed = newRecommendationInput.trim().trimEnd('/')
                        if (trimmed.isNotEmpty()) {
                            viewModel.recommendMint(trimmed)
                            newRecommendationInput = ""
                        }
                    },
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    RecommendationSuggestionList(
                        suggestions = suggestions,
                        onPick = { url ->
                            // One-tap recommend: publish straight from
                            // the suggestion and clear the field so the
                            // user can chain multiple adds without
                            // re-tapping the text input.
                            viewModel.recommendMint(url)
                            newRecommendationInput = ""
                        },
                    )
                }
            }

            if (recommendations.isEmpty()) {
                item { EmptyRecommendationsHint() }
            } else {
                items(recommendations, key = { it.id }) { event ->
                    RecommendationRow(event = event, onDelete = { pendingDelete = event })
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringRes(R.string.cashu_settings_delete_confirm_title)) },
            text = {
                Text(
                    stringRes(
                        R.string.cashu_settings_delete_confirm_body,
                        target.mintUrls().firstOrNull() ?: target.dTag() ?: target.id.take(8),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecommendation(target)
                        pendingDelete = null
                    },
                ) { Text(stringRes(R.string.cashu_settings_delete_recommendation)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: MaterialSymbol,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddRecommendationRow(
    input: String,
    onInputChange: (String) -> Unit,
    canAdd: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringRes(R.string.cashu_settings_add_recommendation)) },
            placeholder = { Text("https://mint.example.com") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onAdd, enabled = canAdd) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = stringRes(R.string.cashu_settings_add_recommendation),
                modifier = Modifier.size(22.dp),
                tint =
                    if (canAdd) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun RecommendationSuggestionList(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            suggestions.forEach { url ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(url) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Material3Icon(
                        imageVector = CustomHashTagIcons.Cashu,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        symbol = MaterialSymbols.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecommendationsHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringRes(R.string.cashu_settings_no_recommendations),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecommendationRow(
    event: MintRecommendationEvent,
    onDelete: () -> Unit,
) {
    // Primary label: first u-tag URL when present, falling back to the
    // d-tag (which may be the mint's announcement pubkey or the URL itself
    // when no announcement was cached at publish time).
    val mintUrl = remember(event.id) { event.mintUrls().firstOrNull() }
    val dTag = remember(event.id) { event.dTag() }
    val title = mintUrl ?: dTag ?: event.id.take(16)
    val subtitle = if (mintUrl != null && dTag != null && mintUrl != dTag) dTag else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.ThumbUp,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.content.isNotBlank()) {
                    Text(
                        text = event.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(R.string.cashu_settings_delete_recommendation),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ============================================================
// @Preview composables — Android Studio rendering only
// ============================================================
// Each preview is rendered in both dark and light themes via
// ThemeComparisonColumn so we can eyeball both at once. Plain-data
// composables only — anything that takes AccountViewModel / INav /
// CashuWalletViewModel can't be cheaply mocked, so those are skipped.

/** Synthesise a [MintRecommendationEvent] for previews without touching the signer. */
private fun fakeRecommendation(
    mintUrl: String,
    review: String = "",
): MintRecommendationEvent =
    MintRecommendationEvent(
        id = "0".repeat(64),
        pubKey = "1".repeat(64),
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("d", mintUrl),
                arrayOf("k", "38172"),
                arrayOf("u", mintUrl),
            ),
        content = review,
        sig = "2".repeat(128),
    )

@Preview
@Composable
fun SettingsRowEditWalletPreview() {
    ThemeComparisonColumn {
        SettingsRow(
            icon = MaterialSymbols.Edit,
            title = "Edit wallet details",
            subtitle = "Mints, nutzap key",
            onClick = {},
        )
    }
}

@Preview
@Composable
fun SettingsRowNoSubtitlePreview() {
    ThemeComparisonColumn {
        SettingsRow(
            icon = MaterialSymbols.Settings,
            title = "Some setting",
            subtitle = null,
            onClick = {},
        )
    }
}

@Preview
@Composable
fun EmptyRecommendationsHintPreview() {
    ThemeComparisonColumn {
        EmptyRecommendationsHint()
    }
}

@Preview
@Composable
fun AddRecommendationRowEmptyPreview() {
    ThemeComparisonColumn {
        AddRecommendationRow(
            input = "",
            onInputChange = {},
            canAdd = false,
            onAdd = {},
        )
    }
}

@Preview
@Composable
fun AddRecommendationRowTypingPreview() {
    ThemeComparisonColumn {
        AddRecommendationRow(
            input = "https://mint.minibits.cash",
            onInputChange = {},
            canAdd = true,
            onAdd = {},
        )
    }
}

@Preview
@Composable
fun RecommendationSuggestionListPreview() {
    ThemeComparisonColumn {
        RecommendationSuggestionList(
            suggestions =
                listOf(
                    "https://mint.minibits.cash/bitcoin",
                    "https://mint.coinos.io",
                    "https://nutmix.cash",
                ),
            onPick = {},
        )
    }
}

@Preview
@Composable
fun RecommendationRowPreview() {
    ThemeComparisonColumn {
        RecommendationRow(
            event = fakeRecommendation("https://mint.minibits.cash/bitcoin"),
            onDelete = {},
        )
    }
}

@Preview
@Composable
fun RecommendationRowWithReviewPreview() {
    ThemeComparisonColumn {
        RecommendationRow(
            event =
                fakeRecommendation(
                    mintUrl = "https://mint.coinos.io",
                    review = "Fast and reliable, been using for months.",
                ),
            onDelete = {},
        )
    }
}
