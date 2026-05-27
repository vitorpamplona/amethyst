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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuMintDirectoryEntry
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCashuWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = viewModel()
    // Synchronous init so state-flow getters don't hit a null `account` on
    // the first composition pass. init() is idempotent — just sets refs.
    viewModel.init(accountViewModel)

    val existingWallet by viewModel.walletEvent.collectAsState()
    val existingMints by viewModel.mints.collectAsState()
    val isEditMode = existingWallet != null

    val mints = remember { mutableStateListOf<String>() }
    var mintInput by remember { mutableStateOf("") }

    // Kick the one-shot backfill so the mint-URL autocomplete has
    // suggestions on first open instead of waiting for the next relay
    // round-trip. Cheap (one sweep over the existing cache); idempotent.
    LaunchedEffect(Unit) { LocalCache.ensureMintDirectoryBackfilled() }
    var keyMode by remember {
        mutableStateOf(
            if (isEditMode) CashuWalletViewModel.P2pkKeyMode.KeepCurrent else CashuWalletViewModel.P2pkKeyMode.AutoGenerate,
        )
    }
    var manualPrivkey by remember { mutableStateOf("") }
    val createState by viewModel.createState.collectAsState()

    // Pre-fill the mints list with the existing wallet's mints whenever we
    // enter edit mode (or the existing mints update). Without this, hitting
    // the Edit button silently wipes the user's mint list because the local
    // `mints` state holder starts empty.
    LaunchedEffect(existingMints) {
        if (existingMints.isNotEmpty()) {
            val current = mints.toSet()
            existingMints.forEach { if (it !in current) mints.add(it) }
        }
    }

    LaunchedEffect(createState) {
        if (createState is CashuWalletCreateState.Success) {
            nav.popBack()
            viewModel.resetCreateState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringRes(
                            if (isEditMode) R.string.wallet_edit_cashu_title else R.string.wallet_add_cashu_title,
                        ),
                    )
                },
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
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(
                text = stringRes(R.string.cashu_mints),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            mints.forEachIndexed { index, mint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mint,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { mints.removeAt(index) }) {
                            Icon(
                                symbol = MaterialSymbols.Delete,
                                contentDescription = stringRes(R.string.cashu_remove_mint),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            val pingState by viewModel.mintPingState.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = mintInput,
                    onValueChange = {
                        mintInput = it
                        viewModel.resetMintPing()
                    },
                    label = { Text(stringRes(R.string.cashu_mint_url)) },
                    placeholder = { Text("https://mint.example.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.pingMint(mintInput.trim().trimEnd('/')) },
                    enabled = mintInput.isNotBlank() && pingState !is MintPingState.Pinging,
                ) {
                    if (pingState is MintPingState.Pinging) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringRes(R.string.cashu_verify))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = {
                        val trimmed = mintInput.trim().trimEnd('/')
                        if (trimmed.isNotEmpty() && trimmed !in mints) {
                            mints.add(trimmed)
                            mintInput = ""
                            viewModel.resetMintPing()
                        }
                    },
                    enabled = mintInput.isNotBlank(),
                ) {
                    Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Live mint directory subscription drives the rich
            // suggestion rows below — open while this screen is on,
            // closed via DisposableEffect when the user backs out so we
            // don't keep relay subscriptions alive after the screen is
            // dismissed.
            DisposableEffect(Unit) {
                viewModel.openMintDirectory()
                onDispose { viewModel.closeMintDirectory() }
            }
            val directoryEntries by viewModel.directory.entries.collectAsState()

            // Empty input → "Popular mints" (the same ranked list the
            // old Browse sheet showed). Typed input → "Matching mints"
            // filtered case-insensitively. Either way we exclude what
            // the user already added so the list isn't padded with
            // disabled rows.
            val suggestions by remember(directoryEntries, mints) {
                derivedStateOf {
                    val typed = mintInput.trim().trimEnd('/').lowercase()
                    val alreadyAdded = mints.map { it.lowercase().trimEnd('/') }.toSet()
                    viewModel.directory
                        .search(typed, limit = 6)
                        .filter { it.url.lowercase().trimEnd('/') !in alreadyAdded }
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringRes(
                            if (mintInput.isBlank()) {
                                R.string.cashu_mint_popular_header
                            } else {
                                R.string.cashu_mint_matches_header
                            },
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MintSuggestionList(
                    suggestions = suggestions,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onPick = { entry ->
                        mintInput = entry.url
                        viewModel.resetMintPing()
                    },
                )
            }

            when (val ps = pingState) {
                is MintPingState.Ok -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            if (ps.name.isNullOrBlank()) {
                                stringRes(R.string.cashu_mint_reachable)
                            } else {
                                stringRes(R.string.cashu_mint_reachable_named, ps.name)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is MintPingState.Failed -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringRes(R.string.cashu_mint_unreachable, ps.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringRes(R.string.cashu_p2pk_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.cashu_p2pk_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isEditMode) {
                P2pkRadio(
                    label = stringRes(R.string.cashu_p2pk_keep_current),
                    selected = keyMode == CashuWalletViewModel.P2pkKeyMode.KeepCurrent,
                    onSelect = { keyMode = CashuWalletViewModel.P2pkKeyMode.KeepCurrent },
                )
            }
            P2pkRadio(
                label = stringRes(R.string.cashu_p2pk_autogen),
                sub = if (isEditMode) stringRes(R.string.cashu_p2pk_autogen_warning_edit) else null,
                selected = keyMode == CashuWalletViewModel.P2pkKeyMode.AutoGenerate,
                onSelect = { keyMode = CashuWalletViewModel.P2pkKeyMode.AutoGenerate },
            )
            P2pkRadio(
                label = stringRes(R.string.cashu_p2pk_manual_label),
                selected = keyMode == CashuWalletViewModel.P2pkKeyMode.Manual,
                onSelect = { keyMode = CashuWalletViewModel.P2pkKeyMode.Manual },
            )

            if (keyMode == CashuWalletViewModel.P2pkKeyMode.Manual) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPrivkey,
                    onValueChange = { manualPrivkey = it },
                    label = { Text(stringRes(R.string.cashu_p2pk_manual_label)) },
                    placeholder = { Text("hex…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val err = (createState as? CashuWalletCreateState.Error)?.message
            if (err != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveWallet(
                        mints = mints.toList(),
                        keyMode = keyMode,
                        manualPrivkey = manualPrivkey.takeIf { keyMode == CashuWalletViewModel.P2pkKeyMode.Manual },
                    )
                },
                enabled =
                    mints.isNotEmpty() &&
                        createState !is CashuWalletCreateState.Saving &&
                        (keyMode != CashuWalletViewModel.P2pkKeyMode.Manual || manualPrivkey.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(
                        if (isEditMode) R.string.wallet_save_changes else R.string.wallet_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun P2pkRadio(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    sub: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Mint directory autocomplete rendered inline under the mint input.
 * Each row uses [MintDirectoryRow] so the user sees follower avatars
 * and recommendation counts the same way they would in a dedicated
 * picker. Tapping a row fills the URL field rather than adding the
 * mint directly — users typically want to verify first.
 *
 * Rows are visually separated by an outlined surface and divider so
 * a long list reads as discrete entries instead of merging into a
 * single coloured block.
 */
@Composable
private fun MintSuggestionList(
    suggestions: List<CashuMintDirectoryEntry>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onPick: (CashuMintDirectoryEntry) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            suggestions.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                MintDirectoryRow(
                    entry = entry,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.clickable { onPick(entry) },
                ) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
