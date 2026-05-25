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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuMintDirectoryEntry
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Modal bottom sheet that lets the user pick a Cashu mint from the NIP-87
 * directory.
 *
 * On open(): starts the directory subscription against the user's outbox
 * relays via the ViewModel. On close (back press / drag-down / Add tap):
 * stops the subscription. The directory state itself is account-scoped, so
 * the subscription only runs while at least one picker is on screen.
 *
 * [excludeUrls] — already-added mint URLs that should appear with a
 * disabled "Added" state instead of an "Add" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MintPickerSheet(
    viewModel: CashuWalletViewModel,
    excludeUrls: Set<String>,
    onPick: (CashuMintDirectoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) {
        viewModel.openMintDirectory()
        onDispose { viewModel.closeMintDirectory() }
    }

    val entries by viewModel.directory.entries.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered =
        remember(entries, query) {
            val q = query.trim().lowercase()
            if (q.isBlank()) {
                entries
            } else {
                entries.filter {
                    it.url.lowercase().contains(q) ||
                        (it.announcement?.content ?: "").lowercase().contains(q) ||
                        (it.announcement?.dTag() ?: "").lowercase().contains(q)
                }
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringRes(R.string.cashu_mint_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.cashu_mint_picker_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringRes(R.string.cashu_mint_picker_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                entries.isEmpty() -> EmptyPicker()
                filtered.isEmpty() -> EmptyMatches(query)
                else ->
                    LazyColumn(
                        modifier = Modifier.heightIn(min = 100.dp, max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.url }) { entry ->
                            MintRow(
                                entry = entry,
                                alreadyAdded = entry.url in excludeUrls,
                                onAdd = {
                                    onPick(entry)
                                    onDismiss()
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun MintRow(
    entry: CashuMintDirectoryEntry,
    alreadyAdded: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val displayName =
                entry.announcement
                    ?.content
                    ?.takeIf { it.isNotBlank() && it.length < 80 }
            Text(
                text = displayName ?: entry.url,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (displayName != null) {
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.followsRecommendationCount > 0) {
                    RecBadge(
                        count = entry.followsRecommendationCount,
                        label = stringRes(R.string.cashu_mint_rec_follows),
                        primary = true,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (entry.recommendationCount > 0) {
                    RecBadge(
                        count = entry.recommendationCount,
                        label = stringRes(R.string.cashu_mint_rec_total),
                        primary = false,
                    )
                }
                if (entry.recommendationCount == 0 && entry.followsRecommendationCount == 0) {
                    Text(
                        text = stringRes(R.string.cashu_mint_no_recs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (alreadyAdded) {
            Text(
                text = stringRes(R.string.cashu_mint_picker_added),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            TextButton(onClick = onAdd) {
                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringRes(R.string.cashu_mint_picker_add))
            }
        }
    }
}

@Composable
private fun RecBadge(
    count: Int,
    label: String,
    primary: Boolean,
) {
    val container =
        if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer =
        if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .background(container, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onContainer,
        )
    }
}

@Composable
private fun EmptyPicker() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringRes(R.string.cashu_mint_picker_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyMatches(query: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(R.string.cashu_mint_picker_no_matches, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
