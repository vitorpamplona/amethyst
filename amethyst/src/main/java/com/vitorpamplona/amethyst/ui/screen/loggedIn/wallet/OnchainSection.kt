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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/**
 * "Bitcoin" card on the wallet screen, shown above the lightning NWC wallet
 * list. Visually mirrors [WalletCard] so the two payment rails read as the
 * same kind of object; bitcoin-orange accent in place of NWC's primary
 * (purple) keeps the rails distinct at a glance.
 *
 * Every account has exactly one Taproot address (derived from its Nostr
 * pubkey via NIP-BC / BIP-341), so this is always a single card — not a list.
 */
@Composable
fun OnchainSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val pubKey = accountViewModel.account.signer.pubKey

    val address =
        remember(pubKey) {
            runCatching { TaprootAddress.fromPubKey(pubKey) }.getOrNull()
        }

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

    val orange = MaterialTheme.colorScheme.bitcoinColor

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.OnchainTransactions) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, orange),
        colors =
            CardDefaults.cardColors(
                containerColor = orange.copy(alpha = 0.12f),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HeaderRow(
                orange = orange,
                balanceState = balanceState,
                balanceSats = balanceSats,
            )

            if (address == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Address derivation unavailable for this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                AddressBlock(address = address)

                Spacer(modifier = Modifier.height(12.dp))
                ActionRow(address = address, accountViewModel = accountViewModel, orange = orange)
            }
        }
    }
}

private enum class BalanceState { LOADING, READY, ERROR, UNAVAILABLE }

@Composable
private fun HeaderRow(
    orange: Color,
    balanceState: BalanceState,
    balanceSats: Long?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BitcoinChip(orange)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Bitcoin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                PublicChip()
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Onchain · Taproot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BalanceBlock(state = balanceState, sats = balanceSats, orange = orange)
    }
}

@Composable
private fun PublicChip() {
    var showDialog by remember { mutableStateOf(false) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(onSurfaceVariant.copy(alpha = 0.12f))
                .clickable { showDialog = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Info,
            contentDescription = null,
            tint = onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringRes(R.string.wallet_onchain_public_chip),
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    symbol = MaterialSymbols.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = { Text(stringRes(R.string.wallet_onchain_public_dialog_title)) },
            text = { Text(stringRes(R.string.wallet_onchain_public_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringRes(R.string.wallet_onchain_public_dialog_confirm))
                }
            },
        )
    }
}

@Composable
private fun BitcoinChip(orange: Color) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color = orange),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.CurrencyBitcoin,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BalanceBlock(
    state: BalanceState,
    sats: Long?,
    orange: Color,
) {
    if (state == BalanceState.LOADING && sats == null) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = orange,
        )
        return
    }

    Column(horizontalAlignment = Alignment.End) {
        when (state) {
            BalanceState.READY -> {
                val formatted =
                    remember(sats) {
                        NumberFormat.getIntegerInstance().format(sats ?: 0L)
                    }
                Text(
                    text = formatted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = orange,
                )
                Text(
                    text = "sats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BalanceState.ERROR -> {
                Text(
                    text = "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            BalanceState.UNAVAILABLE -> {
                Text(
                    text = "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "no backend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BalanceState.LOADING -> Unit
        }
    }
}

@Composable
private fun AddressBlock(address: String) {
    Text(
        text = "Your Taproot address",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = address,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActionRow(
    address: String,
    accountViewModel: AccountViewModel,
    orange: Color,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showSendDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { scope.launch { clipboard.setText(address) } },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { showSendDialog = true },
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = orange,
                    contentColor = Color.White,
                ),
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Send,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Send", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showSendDialog) {
        OnchainZapSendDialog(
            accountViewModel = accountViewModel,
            onDismiss = { showSendDialog = false },
        )
    }
}
