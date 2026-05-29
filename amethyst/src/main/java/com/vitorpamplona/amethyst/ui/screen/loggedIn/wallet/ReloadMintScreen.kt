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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import java.util.UUID

/**
 * Stash the pending nutzap and open the Reload Mint screen. Called from the zap
 * picker when the user taps an underfunded (NEEDS_RELOAD) cashu logo.
 */
fun navigateToReloadMint(
    accountViewModel: AccountViewModel,
    nav: INav,
    baseNote: Note,
    amountSats: Long,
) {
    val uid = UUID.randomUUID().toString()
    accountViewModel.tempReloadRequestCache.put(uid, ReloadMintRequest(baseNote, amountSats))
    nav.nav(Route.ReloadMint(uid))
}

private fun shortMint(url: String): String = url.removePrefix("https://").removePrefix("http://").removeSuffix("/")

private fun sats(value: Long): String = showAmount(value.toBigDecimal().setScale(1))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReloadMintScreen(
    requestId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val request = remember(requestId) { accountViewModel.tempReloadRequestCache.get(requestId) }

    // The cache entry can be gone after process death — nothing to fund, pop.
    if (request == null) {
        LaunchedEffect(Unit) { nav.popBack() }
        return
    }

    val viewModel: ReloadMintViewModel = viewModel()
    LaunchedEffect(requestId) { viewModel.init(accountViewModel, request) }

    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (ui.status is ReloadStatus.Done) {
        LaunchedEffect(Unit) { nav.popBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.reload_mint_title)) },
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
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringRes(R.string.reload_mint_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )

            // ── Destination ───────────────────────────────────────────────
            SectionHeader(stringRes(R.string.reload_mint_section_to))
            if (ui.targetOptions.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.targetOptions.forEach { mint ->
                        FilterChip(
                            selected = mint == ui.selectedTarget,
                            onClick = { viewModel.selectTarget(mint) },
                            label = { Text(shortMint(mint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shortMint(ui.selectedTarget),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringRes(R.string.reload_mint_needs_more, sats(ui.shortfallSats)),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ── All balances ──────────────────────────────────────────────
            SectionHeader(stringRes(R.string.reload_mint_section_balances))
            ui.balances.forEach { bal ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = shortMint(bal.mintUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color =
                            if (bal.mintUrl == ui.selectedTarget) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "${sats(bal.balanceSats)} sat", color = MaterialTheme.colorScheme.placeholderText)
                }
            }

            // ── Source ────────────────────────────────────────────────────
            SectionHeader(stringRes(R.string.reload_mint_section_from))
            ui.sources.forEach { source ->
                SourceRow(
                    source = source,
                    selected = source == ui.selectedSource,
                    onSelect = { viewModel.selectSource(source) },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Summary + action ──────────────────────────────────────────
            Text(
                text = stringRes(R.string.reload_mint_move_summary, sats(ui.shortfallSats), sats(ui.estFeeSats)),
                style = MaterialTheme.typography.bodyMedium,
            )

            when (val status = ui.status) {
                is ReloadStatus.Working -> {
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                    Text(status.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
                }

                is ReloadStatus.AwaitingInvoice -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Text(stringRes(R.string.reload_mint_awaiting_payment), style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard(context, status.invoice) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.reload_mint_copy_invoice))
                    }
                }

                is ReloadStatus.Failed -> {
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { viewModel.confirm() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringRes(R.string.reload_mint_retry))
                    }
                }

                else -> {
                    val source = ui.selectedSource
                    val enabled = source is ReloadSource.Lightning || (source is ReloadSource.Mint && source.canCover)
                    Button(
                        onClick = { viewModel.confirm() },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.reload_mint_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SourceRow(
    source: ReloadSource,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val enabled = source is ReloadSource.Lightning || (source is ReloadSource.Mint && source.canCover)
    val title =
        when (source) {
            is ReloadSource.Mint -> shortMint(source.mintUrl)
            ReloadSource.Lightning -> stringRes(R.string.reload_mint_pay_lightning)
        }
    val trailing =
        when (source) {
            is ReloadSource.Mint ->
                if (source.canCover) "${sats(source.balanceSats)} sat" else stringRes(R.string.reload_mint_not_enough)
            ReloadSource.Lightning -> stringRes(R.string.reload_mint_lightning_desc)
        }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = enabled, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("invoice", text))
}
