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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_all
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_non_zaps
import com.vitorpamplona.amethyst.commons.resources.wallet_filter_zaps
import com.vitorpamplona.amethyst.commons.resources.wallet_incoming
import com.vitorpamplona.amethyst.commons.resources.wallet_loading
import com.vitorpamplona.amethyst.commons.resources.wallet_no_transactions
import com.vitorpamplona.amethyst.commons.resources.wallet_outgoing
import com.vitorpamplona.amethyst.commons.resources.wallet_refresh
import com.vitorpamplona.amethyst.commons.resources.wallet_transactions
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.formatMonthDayTime
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionType
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletTransactionsScreen(
    walletId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = viewModel()

    LaunchedEffect(accountViewModel, walletId) {
        walletViewModel.init(accountViewModel)
        walletViewModel.selectWallet(walletId)
        walletViewModel.fetchTransactions()
    }

    val transactions by walletViewModel.filteredTransactions.collectAsState()
    val isLoading by walletViewModel.isLoading.collectAsState()
    val isLoadingMore by walletViewModel.isLoadingMore.collectAsState()
    val hasMore by walletViewModel.hasMoreTransactions.collectAsState()
    val currentFilter by walletViewModel.transactionFilter.collectAsState()
    val error by walletViewModel.error.collectAsState()

    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 5 && !isLoadingMore && hasMore && transactions.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            walletViewModel.loadMoreTransactions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.wallet_transactions)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { walletViewModel.fetchTransactions() }) {
                        Icon(
                            symbol = MaterialSymbols.Refresh,
                            contentDescription = stringRes(Res.string.wallet_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val currentError = error
        if (isLoading && transactions.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringRes(Res.string.wallet_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else if (currentError != null && transactions.isEmpty()) {
            // A wallet refusal (e.g. RESTRICTED) leaves the list empty. Without this
            // branch the screen would claim "no transactions yet" and hide the reason.
            EmptyState(
                title = stringRes(R.string.wallet_transactions_load_failed),
                modifier = Modifier.padding(padding).padding(24.dp),
                description = currentError,
                onRefresh = { walletViewModel.fetchTransactions() },
                refreshLabel = stringRes(Res.string.wallet_refresh),
            )
        } else if (transactions.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringRes(Res.string.wallet_no_transactions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                state = listState,
            ) {
                if (currentError != null) {
                    // Already-loaded transactions stay visible; the banner explains why
                    // the latest refresh or page load did not land.
                    item {
                        Text(
                            text = currentError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item {
                    TransactionFilterRow(currentFilter) { walletViewModel.setTransactionFilter(it) }
                }
                items(transactions) { tx ->
                    TransactionItem(tx, accountViewModel, nav)
                    HorizontalDivider()
                }
                if (isLoadingMore) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionFilterRow(
    currentFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = currentFilter == TransactionFilter.ALL,
            onClick = { onFilterSelected(TransactionFilter.ALL) },
            label = { Text(stringRes(Res.string.wallet_filter_all)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.ZAPS,
            onClick = { onFilterSelected(TransactionFilter.ZAPS) },
            label = { Text(stringRes(Res.string.wallet_filter_zaps)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.NON_ZAPS,
            onClick = { onFilterSelected(TransactionFilter.NON_ZAPS) },
            label = { Text(stringRes(Res.string.wallet_filter_non_zaps)) },
        )
    }
}

@Composable
private fun TransactionItem(
    tx: NwcTransaction,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isIncoming = tx.type == NwcTransactionType.INCOMING
    val amountSats = (tx.amount ?: 0L) / 1000L
    val formattedAmount =
        remember(amountSats) {
            val fmt = NumberFormat.getIntegerInstance()
            (if (isIncoming) "+" else "-") + fmt.format(amountSats)
        }

    val context = LocalContext.current
    val dateText =
        remember(tx.created_at, context) {
            tx.created_at?.let { formatMonthDayTime(it, context) } ?: ""
        }

    val directionLabel =
        if (isIncoming) stringRes(Res.string.wallet_incoming) else stringRes(Res.string.wallet_outgoing)

    val labels =
        remember(tx.metadata, tx.description, tx.type, directionLabel) {
            TransactionRowLabels.resolve(tx, directionLabel)
        }
    val counterpartyPubkeyHex = (labels.title as? TransactionRowLabels.Title.User)?.pubkeyHex

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (counterpartyPubkeyHex != null) {
            UserPicture(
                userHex = counterpartyPubkeyHex,
                size = 40.dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else {
            Icon(
                symbol = if (isIncoming) MaterialSymbols.ArrowDownward else MaterialSymbols.ArrowUpward,
                contentDescription =
                    if (isIncoming) {
                        stringRes(Res.string.wallet_incoming)
                    } else {
                        stringRes(Res.string.wallet_outgoing)
                    },
                modifier = Modifier.size(40.dp),
                tint =
                    if (isIncoming) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            when (val title = labels.title) {
                is TransactionRowLabels.Title.User ->
                    TransactionUserName(title.pubkeyHex, title.name, accountViewModel)

                is TransactionRowLabels.Title.Literal ->
                    Text(
                        text = title.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }

            labels.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "$formattedAmount sats",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isIncoming) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
        )
    }
}

@Composable
private fun TransactionUserName(
    pubkeyHex: String,
    fallbackName: String?,
    accountViewModel: AccountViewModel,
) {
    LoadUser(baseUserHex = pubkeyHex, accountViewModel = accountViewModel) { user ->
        if (user != null) {
            UsernameDisplay(
                baseUser = user,
                fontWeight = FontWeight.Medium,
                accountViewModel = accountViewModel,
            )
        } else {
            Text(
                text = fallbackName ?: (pubkeyHex.take(8) + "..."),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
