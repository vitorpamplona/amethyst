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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.datasource.OnchainZapsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnchainTransactionsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: OnchainTransactionsViewModel = viewModel()

    LaunchedEffect(accountViewModel) {
        viewModel.init(accountViewModel)
        viewModel.fetchTransactions()
    }

    val windowSince by viewModel.oldestBlockTime.collectAsState()
    OnchainZapsFilterAssemblerSubscription(
        user = accountViewModel.account.userProfile(),
        windowSinceSeconds = windowSince,
        accountViewModel = accountViewModel,
    )

    val transactions by viewModel.filteredTransactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMoreTransactions.collectAsState()
    val currentFilter by viewModel.transactionFilter.collectAsState()
    val address by viewModel.displayAddress.collectAsState()
    val error by viewModel.error.collectAsState()

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
            viewModel.loadMoreTransactions()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.wallet_onchain_transactions)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchTransactions() }) {
                        Icon(
                            symbol = MaterialSymbols.Refresh,
                            contentDescription = stringRes(R.string.wallet_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            address == null -> {
                EmptyMessage(padding, stringRes(R.string.wallet_onchain_no_address))
            }
            isLoading && transactions.isEmpty() -> {
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
                        stringRes(R.string.wallet_loading),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            error != null && transactions.isEmpty() -> {
                EmptyMessage(
                    padding,
                    error ?: stringRes(R.string.wallet_onchain_no_backend),
                )
            }
            transactions.isEmpty() -> {
                EmptyMessage(padding, stringRes(R.string.wallet_no_transactions))
            }
            else -> {
                val uriHandler = LocalUriHandler.current
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    state = listState,
                ) {
                    item { AddressHeader(address) }
                    item {
                        TransactionFilterRow(currentFilter) { viewModel.setTransactionFilter(it) }
                    }
                    items(transactions, key = { it.tx.txid }) { txView ->
                        OnchainTransactionItem(
                            view = txView,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onClick = { handleTxClick(txView, nav, uriHandler) },
                        )
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
}

@Composable
private fun EmptyMessage(
    padding: androidx.compose.foundation.layout.PaddingValues,
    message: String,
) {
    Column(
        modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddressHeader(address: String?) {
    if (address.isNullOrBlank()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            label = { Text(stringRes(R.string.wallet_filter_all)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.ZAPS,
            onClick = { onFilterSelected(TransactionFilter.ZAPS) },
            label = { Text(stringRes(R.string.wallet_filter_zaps)) },
        )
        FilterChip(
            selected = currentFilter == TransactionFilter.NON_ZAPS,
            onClick = { onFilterSelected(TransactionFilter.NON_ZAPS) },
            label = { Text(stringRes(R.string.wallet_filter_non_zaps)) },
        )
    }
}

@Composable
private fun OnchainTransactionItem(
    view: OnchainTxView,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: () -> Unit,
) {
    val isIncoming = view.isIncoming
    val amountSats = view.tx.netValueSats.absoluteValue
    val formattedAmount =
        remember(view.tx.netValueSats) {
            val fmt = NumberFormat.getIntegerInstance()
            (if (isIncoming) "+" else "-") + fmt.format(amountSats)
        }

    val dateText =
        remember(view.tx.blockTime, view.tx.confirmations) {
            val ts = view.tx.blockTime
            if (ts != null) {
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts * 1000L))
            } else {
                ""
            }
        }

    val counterpartyPubkeyHex = view.counterpartyPubkeyHex()
    val counterpartyAddress = view.tx.counterpartyAddresses.firstOrNull()
    val zapComment = view.zap?.content?.takeIf { it.isNotBlank() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                        stringRes(R.string.wallet_incoming)
                    } else {
                        stringRes(R.string.wallet_outgoing)
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
            if (counterpartyPubkeyHex != null) {
                OnchainCounterpartyName(counterpartyPubkeyHex, accountViewModel)
            } else if (counterpartyAddress != null) {
                Text(
                    text = counterpartyAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text =
                        if (isIncoming) {
                            stringRes(R.string.wallet_incoming)
                        } else {
                            stringRes(R.string.wallet_outgoing)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (zapComment != null) {
                Text(
                    text = zapComment,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateText.ifBlank { stringRes(R.string.wallet_onchain_pending) },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (view.tx.confirmations == 0) {
                            MaterialTheme.colorScheme.bitcoinColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
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

/**
 * Dispatch a transaction-row tap: jump to the matched on-chain zap event's
 * thread when we have one, otherwise open the tx on a public block explorer.
 * Mempool.space works over Tor and clearnet; deriving the user's configured
 * explorer URL would also need to know whether Bitcoin traffic is being
 * routed over Tor right now, which the UI layer doesn't carry — stick with
 * mempool.space as a sensible default.
 */
private fun handleTxClick(
    view: OnchainTxView,
    nav: INav,
    uriHandler: UriHandler,
) {
    val zap = view.zap
    if (zap != null) {
        nav.nav(Route.Note(zap.id))
    } else {
        runCatching { uriHandler.openUri("https://mempool.space/tx/${view.tx.txid}") }
    }
}

@Composable
private fun OnchainCounterpartyName(
    pubkeyHex: String,
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
                text = pubkeyHex.take(8) + "...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
