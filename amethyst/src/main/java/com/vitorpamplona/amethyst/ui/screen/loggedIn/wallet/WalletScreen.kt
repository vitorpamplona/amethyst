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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = viewModel()
    walletViewModel.init(accountViewModel)

    val hasWallet by walletViewModel.hasWalletSetup.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.wallet)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nav.nav(Route.WalletAdd) }) {
                        Icon(
                            symbol = MaterialSymbols.Add,
                            contentDescription = stringRes(R.string.wallet_add),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!hasWallet) {
            NoWalletSetup(
                modifier = Modifier.padding(padding),
                nav = nav,
            )
        } else {
            MultiWalletHomeContent(
                walletViewModel = walletViewModel,
                modifier = Modifier.padding(padding),
                nav = nav,
            )
        }
    }
}

@Composable
private fun NoWalletSetup(
    modifier: Modifier,
    nav: INav,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringRes(R.string.wallet_no_wallets),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(R.string.wallet_no_wallets_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { nav.nav(Route.WalletAdd) }) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringRes(R.string.wallet_add_connection))
        }
    }
}

@Composable
private fun MultiWalletHomeContent(
    walletViewModel: WalletViewModel,
    modifier: Modifier,
    nav: INav,
) {
    val walletInfoList by walletViewModel.walletInfoList.collectAsState()

    LaunchedEffect(Unit) {
        walletViewModel.fetchAllBalances()
        walletViewModel.wallets.value.forEach { wallet ->
            walletViewModel.fetchInfoForWallet(wallet.id)
        }
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.wallet_your_wallets),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        itemsIndexed(walletInfoList, key = { _, info -> info.walletId }) { index, walletInfo ->
            WalletCard(
                walletInfo = walletInfo,
                index = index,
                totalCount = walletInfoList.size,
                onSelect = {
                    walletViewModel.selectWallet(walletInfo.walletId)
                    nav.nav(Route.WalletDetail(walletInfo.walletId))
                },
                onSetDefault = {
                    walletViewModel.setDefaultWallet(walletInfo.walletId)
                },
                onRename = { newName ->
                    walletViewModel.renameWallet(walletInfo.walletId, newName)
                },
                onMoveUp = {
                    walletViewModel.moveWallet(index, index - 1)
                },
                onMoveDown = {
                    walletViewModel.moveWallet(index, index + 1)
                },
                onRemove = {
                    walletViewModel.removeWallet(walletInfo.walletId)
                },
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { nav.nav(Route.WalletAdd) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringRes(R.string.wallet_add))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WalletCard(
    walletInfo: WalletInfo,
    index: Int,
    totalCount: Int,
    onSelect: () -> Unit,
    onSetDefault: () -> Unit,
    onRename: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringRes(R.string.wallet_remove_confirm)) },
            text = { Text(stringRes(R.string.wallet_remove_confirm_description)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    onRemove()
                }) {
                    Text(stringRes(R.string.wallet_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }

    if (showRenameDialog) {
        RenameWalletDialog(
            currentName = walletInfo.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRename(newName)
            },
        )
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        border =
            if (walletInfo.isDefault) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (walletInfo.isDefault) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = walletInfo.alias ?: walletInfo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (walletInfo.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                symbol = MaterialSymbols.Star,
                                contentDescription = stringRes(R.string.wallet_default),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (walletInfo.alias != null && walletInfo.alias != walletInfo.name) {
                        Text(
                            text = walletInfo.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Reorder buttons
                if (totalCount > 1) {
                    Column {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                MaterialSymbols.KeyboardArrowUp,
                                contentDescription = stringRes(R.string.wallet_move_up),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalCount - 1,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                MaterialSymbols.KeyboardArrowDown,
                                contentDescription = stringRes(R.string.wallet_move_down),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Balance
                if (walletInfo.isLoading && walletInfo.balanceSats == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        val formattedBalance =
                            remember(walletInfo.balanceSats) {
                                val fmt = NumberFormat.getIntegerInstance()
                                fmt.format(walletInfo.balanceSats ?: 0L)
                            }
                        Text(
                            text = formattedBalance,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringRes(R.string.wallet_sats),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (walletInfo.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = walletInfo.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!walletInfo.isDefault) {
                    OutlinedButton(
                        onClick = onSetDefault,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes(R.string.wallet_set_default), style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(MaterialSymbols.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringRes(R.string.wallet_rename), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        MaterialSymbols.Delete,
                        contentDescription = stringRes(R.string.wallet_remove),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameWalletDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.wallet_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringRes(R.string.wallet_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringRes(R.string.wallet_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
