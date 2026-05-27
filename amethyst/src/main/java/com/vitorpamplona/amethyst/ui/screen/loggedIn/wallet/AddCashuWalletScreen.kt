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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import androidx.compose.material3.Icon as Material3Icon

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
    var showPicker by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.cashu_mints),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showPicker = true }) {
                    Icon(MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringRes(R.string.cashu_browse_mints))
                }
            }
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

            // Autocomplete from the cache-backed mint directory.
            // Suggestions are filtered to URLs the user hasn't already
            // added and that aren't an exact case-insensitive match for
            // the current input (no point suggesting what they already
            // typed). Wrapped in `derivedStateOf` so the recompute only
            // fires when `mintInput` or `mints` change, not on every
            // recomposition of the surrounding form. An empty field
            // shows no suggestions — the autocomplete should react to
            // typing, not dump every mint we've ever seen unsolicited.
            val suggestions by remember(mints) {
                derivedStateOf {
                    val typed = mintInput.trim().trimEnd('/').lowercase()
                    if (typed.isEmpty()) {
                        emptyList()
                    } else {
                        val alreadyAdded = mints.map { it.lowercase().trimEnd('/') }.toSet()
                        LocalCache.mintDirectory
                            .suggest(typed, limit = 6)
                            .filter { it != typed && it !in alreadyAdded }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                MintSuggestionList(
                    suggestions = suggestions,
                    onPick = { url ->
                        mintInput = url
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

    if (showPicker) {
        MintPickerSheet(
            viewModel = viewModel,
            excludeUrls = mints.toSet(),
            onPick = { entry ->
                val url = entry.url.trim().trimEnd('/')
                if (url.isNotEmpty() && url !in mints) mints.add(url)
            },
            onDismiss = { showPicker = false },
        )
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
 * Cache-backed mint-URL autocomplete rendered inline under the mint
 * input. Each row fills the text field on tap so the user can verify /
 * add the same way they would with a hand-typed URL — we deliberately
 * don't auto-add on tap because users often want to ping first.
 */
@Composable
private fun MintSuggestionList(
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
                }
            }
        }
    }
}

// ============================================================
// @Preview composables — Android Studio rendering only
// ============================================================

@Preview
@Composable
fun MintSuggestionListPreview() {
    ThemeComparisonColumn {
        MintSuggestionList(
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
fun MintSuggestionListSinglePreview() {
    ThemeComparisonColumn {
        MintSuggestionList(
            suggestions = listOf("https://mint.minibits.cash/bitcoin"),
            onPick = {},
        )
    }
}
