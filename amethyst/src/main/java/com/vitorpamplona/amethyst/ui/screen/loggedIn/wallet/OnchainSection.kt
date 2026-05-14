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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/**
 * "Bitcoin" section card on the wallet screen, shown above the lightning NWC
 * wallet list. Every account has exactly one Taproot address (derived from
 * its Nostr pubkey via NIP-BC / BIP-341), so this is always a single card —
 * not a list.
 *
 * Phase C scope: derive + display + copy address. Balance, recent zaps, send,
 * and tap-to-detail UI come in later phases.
 */
@Composable
fun OnchainSection(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val pubKey = accountViewModel.account.signer.pubKey

    // Cache the derived address — bech32m + tap-tweak is cheap but pubkey doesn't
    // change for the lifetime of the screen.
    val address =
        remember(pubKey) {
            runCatching { TaprootAddress.fromPubKey(pubKey) }.getOrNull()
        }

    // Balance fetched from the configured OnchainBackend. null = unknown / loading
    // / unconfigured. We intentionally do not retry on failure here — the user can
    // refresh by leaving and re-entering the screen.
    var balanceSats by remember(pubKey) { mutableStateOf<Long?>(null) }
    var balanceState by remember(pubKey) { mutableStateOf(BalanceState.LOADING) }

    LaunchedEffect(address) {
        if (address == null) {
            balanceState = BalanceState.UNAVAILABLE
            return@LaunchedEffect
        }
        val backend = LocalCache.onchainBackend
        if (backend == null) {
            balanceState = BalanceState.UNAVAILABLE
            return@LaunchedEffect
        }
        balanceState = BalanceState.LOADING
        try {
            val utxos = withContext(Dispatchers.IO) { backend.getUtxosForAddress(address) }
            balanceSats = utxos.sumOf { it.valueSats }
            balanceState = BalanceState.READY
        } catch (t: Throwable) {
            balanceState = BalanceState.ERROR
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Bitcoin",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (address == null) {
                Text(
                    text = "Address derivation unavailable for this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BalanceRow(state = balanceState, sats = balanceSats)
                Text(
                    text = "Your Taproot address",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ActionRow(address = address, accountViewModel = accountViewModel)
            }
        }
    }
}

private enum class BalanceState { LOADING, READY, ERROR, UNAVAILABLE }

@Composable
private fun BalanceRow(
    state: BalanceState,
    sats: Long?,
) {
    val text =
        when (state) {
            BalanceState.LOADING -> {
                "Loading balance…"
            }

            BalanceState.READY -> {
                val formatted = NumberFormat.getNumberInstance().format(sats ?: 0L)
                "$formatted sats"
            }

            BalanceState.ERROR -> {
                "Balance unavailable"
            }

            BalanceState.UNAVAILABLE -> {
                "Chain backend not configured"
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ActionRow(
    address: String,
    accountViewModel: AccountViewModel,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showSendDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = {
            scope.launch { clipboard.setText(address) }
        }) {
            Text("Copy address")
        }
        Button(onClick = { showSendDialog = true }) {
            Text("Send")
        }
    }

    if (showSendDialog) {
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = { showSendDialog = false },
        )
    }
}
