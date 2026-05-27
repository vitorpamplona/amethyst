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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.util.getText
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import androidx.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = viewModel()
    // Synchronous init so state-flow getters don't hit a null `account` on
    // the first composition pass. init() is idempotent — just sets refs.
    viewModel.init(accountViewModel)

    val walletEvent by viewModel.walletEvent.collectAsState()
    val discovering by viewModel.discovering.collectAsState()
    val mints by viewModel.mints.collectAsState()
    val balanceSats by viewModel.balanceSats.collectAsState()
    val history by viewModel.history.collectAsState()
    val pendingQuotes by viewModel.pendingQuotes.collectAsState()

    var receiveOpen by remember { mutableStateOf(false) }
    var sendLnOpen by remember { mutableStateOf(false) }
    var sendTokenOpen by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }

    // pendingQuotes drives a non-modal banner in the wallet body (see
    // PendingQuoteBanner below). Tapping the banner is what opens the
    // Receive dialog with the stored quote pre-loaded — we do NOT auto-pop
    // the dialog on every entry to the screen, which would re-surface
    // dismissed quotes every time the user navigates back.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.cashu_wallet_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nav.nav(Route.CashuWalletSettings) }) {
                        Icon(
                            symbol = MaterialSymbols.Settings,
                            contentDescription = stringRes(R.string.cashu_settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            walletEvent != null ->
                CashuWalletContent(
                    modifier = Modifier.padding(padding),
                    balanceSats = balanceSats,
                    mints = mints,
                    history = history,
                    pendingQuoteCount = pendingQuotes.size,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onReceive = { receiveOpen = true },
                    onSendLn = { sendLnOpen = true },
                    onSendToken = { sendTokenOpen = true },
                    onRedeem = { redeemOpen = true },
                    onRecommendMint = { viewModel.recommendMint(it) },
                    onResumePendingQuote = {
                        pendingQuotes.firstOrNull()?.let {
                            viewModel.resumeMintQuote(it)
                            receiveOpen = true
                        }
                    },
                )

            // NIP-60 wallets are portable across clients — show a "looking
            // for your wallet" state until the relay subscription returns
            // anything (or times out). Without this, a user who created
            // their wallet in another app would see the Create CTA on
            // first launch and would clobber the remote kind:17375.
            discovering ->
                DiscoveringCashuWallet(modifier = Modifier.padding(padding))

            else ->
                EmptyCashuWallet(
                    modifier = Modifier.padding(padding),
                    onCreate = { nav.nav(Route.WalletAddCashu) },
                )
        }
    }

    if (receiveOpen) {
        ReceiveDialog(
            viewModel = viewModel,
            mints = mints,
            onDismiss = {
                receiveOpen = false
                viewModel.resetMintState()
            },
        )
    }
    if (sendLnOpen) {
        SendLnDialog(
            viewModel = viewModel,
            mints = mints,
            onDismiss = {
                sendLnOpen = false
                viewModel.resetMeltState()
            },
        )
    }
    if (sendTokenOpen) {
        SendTokenDialog(
            viewModel = viewModel,
            mints = mints,
            onDismiss = {
                sendTokenOpen = false
                viewModel.resetSendTokenState()
            },
        )
    }
    if (redeemOpen) {
        RedeemDialog(
            viewModel = viewModel,
            onDismiss = {
                redeemOpen = false
                viewModel.resetRedeemState()
            },
        )
    }
}

@Composable
private fun DiscoveringCashuWallet(modifier: Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(R.string.cashu_discovering),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringRes(R.string.cashu_discovering_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyCashuWallet(
    modifier: Modifier,
    onCreate: () -> Unit,
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
            text = stringRes(R.string.cashu_no_wallet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringRes(R.string.cashu_no_wallet_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreate) {
            Text(stringRes(R.string.wallet_add_cashu_title))
        }
    }
}

@Composable
private fun CashuWalletContent(
    modifier: Modifier,
    balanceSats: Long,
    mints: List<String>,
    history: List<CashuSpendingHistoryEvent>,
    pendingQuoteCount: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
    onReceive: () -> Unit,
    onSendLn: () -> Unit,
    onSendToken: () -> Unit,
    onRedeem: () -> Unit,
    onRecommendMint: (String) -> Unit,
    onResumePendingQuote: () -> Unit,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            BalanceCard(balanceSats)
        }

        if (pendingQuoteCount > 0) {
            item { PendingQuoteBanner(count = pendingQuoteCount, onResume = onResumePendingQuote) }
        }

        item {
            ActionRow(
                onReceive = onReceive,
                onSendLn = onSendLn,
                onSendToken = onSendToken,
                onRedeem = onRedeem,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.cashu_mints),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(mints, key = { it }) { mint ->
            MintRow(mint = mint, onRecommend = { onRecommendMint(mint) })
        }

        if (history.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringRes(R.string.cashu_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(history, key = { it.id }) { entry ->
                HistoryRow(entry, accountViewModel, nav)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * Banner that surfaces unfinished mint quotes — tappable to resume the
 * receive flow with the stored invoice. Driven by
 * [com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState.pendingQuotes].
 *
 * Replaces the earlier auto-popup behaviour which re-surfaced the Receive
 * dialog on every entry to the screen.
 */
@Composable
private fun PendingQuoteBanner(
    count: Int,
    onResume: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onResume),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.cashu_pending_quotes_title,
                            count,
                            count,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringRes(R.string.cashu_pending_quotes_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Text(
                text = stringRes(R.string.cashu_pending_quotes_resume),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun BalanceCard(balanceSats: Long) {
    val formatted =
        remember(balanceSats) {
            NumberFormat.getIntegerInstance().format(balanceSats)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringRes(R.string.cashu_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatted,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringRes(R.string.wallet_sats),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ActionRow(
    onReceive: () -> Unit,
    onSendLn: () -> Unit,
    onSendToken: () -> Unit,
    onRedeem: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(MaterialSymbols.Bolt, stringRes(R.string.cashu_action_receive), onReceive, Modifier.weight(1f))
        ActionTile(MaterialSymbols.AutoMirrored.Send, stringRes(R.string.cashu_action_send_ln), onSendLn, Modifier.weight(1f))
        ActionTile(MaterialSymbols.ContentPaste, stringRes(R.string.cashu_action_send_token), onSendToken, Modifier.weight(1f))
        ActionTile(MaterialSymbols.Add, stringRes(R.string.cashu_action_redeem), onRedeem, Modifier.weight(1f))
    }
}

/**
 * Square-ish tile with the icon stacked over a 1-2 line label.
 *
 * Replaces an earlier OutlinedButton-per-tile layout: with 4 tiles in a row
 * and OutlinedButton's default 24dp horizontal padding plus a 20dp icon,
 * the "Send Token" label clipped on standard 360dp-wide phones. A Surface
 * with explicit padding gives us full control over the content area.
 */
@Composable
private fun ActionTile(
    icon: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier =
                Modifier
                    .clickable(onClick = onClick)
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MintRow(
    mint: String,
    onRecommend: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Material3Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = mint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onRecommend != null) {
                IconButton(onClick = onRecommend, modifier = Modifier.size(28.dp)) {
                    Icon(
                        symbol = MaterialSymbols.ThumbUp,
                        contentDescription = stringRes(R.string.cashu_recommend_mint),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Renders one kind:7376 history event in the same visual language as the
 * LN + on-chain transaction lists: counterparty avatar on the left,
 * description on top, timestamp underneath, signed amount on the right.
 *
 * For inbound nutzap redemptions (kind:9321 referenced with a
 * "redeemed" marker on the kind:7376) we can resolve the sender's
 * Nostr pubkey from LocalCache and show their profile picture + name.
 * For everything else (LN mint/melt, send-as-token, redeem cashuB)
 * there is no Nostr counterparty, so we fall back to a directional
 * arrow icon matching the LN wallet's empty-counterparty pattern.
 */
@Composable
private fun HistoryRow(
    entry: CashuSpendingHistoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val signer = accountViewModel.account.signer

    // Decrypt the encrypted half of the spending history once per row.
    // Cheap (NIP-44 on a few hundred bytes) and Compose memoizes the
    // produceState by entry.id so it doesn't repeat on recomposition.
    val decoded by produceState<DecodedHistoryRow?>(initialValue = null, key1 = entry.id) {
        value = decodeHistoryRow(entry, signer)
    }

    val nutzapSenderHex =
        remember(decoded) {
            // Sender lookup: the redeemed-marker `e` tag points at the kind:9321
            // event id; if it's already in LocalCache (we get it via our cashu
            // wallet filter assembler) its event.pubKey is the sender.
            entry.redeemedReferences().firstNotNullOfOrNull { ref ->
                LocalCache
                    .getOrCreateNote(ref.eventId)
                    .event
                    ?.takeIf { it is NutzapEvent }
                    ?.pubKey
            }
        }

    val isIncoming = decoded?.direction == SpendingDirection.IN

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left avatar / icon.
            if (nutzapSenderHex != null) {
                UserPicture(
                    userHex = nutzapSenderHex,
                    size = 40.dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                Icon(
                    symbol = if (isIncoming) MaterialSymbols.ArrowDownward else MaterialSymbols.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint =
                        if (isIncoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Middle: title + timestamp.
            Column(modifier = Modifier.weight(1f)) {
                if (nutzapSenderHex != null) {
                    HistoryUserName(nutzapSenderHex, accountViewModel)
                } else {
                    Text(
                        text =
                            stringRes(
                                if (isIncoming) {
                                    R.string.cashu_history_incoming
                                } else {
                                    R.string.cashu_history_outgoing
                                },
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text =
                        DateFormat
                            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(entry.createdAt * 1000)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Right: signed amount.
            val amount = decoded?.amount
            if (amount != null) {
                Text(
                    text = (if (isIncoming) "+" else "−") + NumberFormat.getIntegerInstance().format(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isIncoming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

private data class DecodedHistoryRow(
    val direction: SpendingDirection?,
    val amount: Long?,
)

private suspend fun decodeHistoryRow(
    entry: CashuSpendingHistoryEvent,
    signer: NostrSigner,
): DecodedHistoryRow =
    runCatching {
        DecodedHistoryRow(
            direction = entry.direction(signer),
            amount = entry.amount(signer),
        )
    }.getOrNull() ?: DecodedHistoryRow(direction = null, amount = null)

@Composable
private fun HistoryUserName(
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
                text = pubkeyHex.take(8) + "…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================================================================
// Receive (mint from LN)
// ============================================================================

@Composable
private fun ReceiveDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    onDismiss: () -> Unit,
) {
    val state by viewModel.mintState.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(mints.firstOrNull() ?: "") }

    // Poll every 3s while waiting for payment.
    LaunchedEffect(state) {
        if (state is CashuMintFlowState.AwaitingPayment) {
            while (true) {
                delay(3000)
                viewModel.checkAndCompleteMint()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cashu_action_receive)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuMintFlowState.AwaitingPayment -> {
                        Text(
                            stringRes(R.string.cashu_receive_invoice_explainer, s.amountSats.toString()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = s.flow.invoice,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringRes(R.string.cashu_invoice_bolt11)) },
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            scope.launch { clipboard.setText(s.flow.invoice) }
                        }) {
                            Icon(
                                symbol = MaterialSymbols.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes(R.string.cashu_copy_invoice))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringRes(R.string.cashu_waiting_for_payment),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    is CashuMintFlowState.Completing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_completing_mint))
                        }
                    }

                    is CashuMintFlowState.Completed -> {
                        Text(stringRes(R.string.cashu_received_amount, s.amountSats.toString()))
                    }

                    is CashuMintFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FormReceive(
                            mints = mints,
                            amount = amount,
                            onAmountChange = { amount = it },
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }

                    CashuMintFlowState.Requesting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_requesting_invoice))
                        }
                    }

                    CashuMintFlowState.Idle -> {
                        FormReceive(
                            mints = mints,
                            amount = amount,
                            onAmountChange = { amount = it },
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuMintFlowState.Idle, is CashuMintFlowState.Error -> {
                    TextButton(
                        onClick = {
                            val n = amount.toLongOrNull() ?: 0L
                            viewModel.startMintFromLightning(pickedMint, n)
                        },
                        enabled = amount.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(R.string.cashu_request_invoice)) }
                }

                is CashuMintFlowState.AwaitingPayment -> {
                    // While the user is waiting for an invoice they may
                    // decide they don't want it after all (typo'd amount,
                    // wrong mint, lost interest). Discard NIP-09-deletes
                    // the kind:7374 so the pending-invoice banner won't
                    // re-surface it on next entry.
                    TextButton(onClick = { viewModel.discardMintQuote() }) {
                        Text(stringRes(R.string.cashu_discard_invoice))
                    }
                }

                is CashuMintFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun FormReceive(
    mints: List<String>,
    amount: String,
    onAmountChange: (String) -> Unit,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { v -> onAmountChange(v.filter { it.isDigit() }) },
        label = { Text(stringRes(R.string.cashu_amount_sats)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

@Composable
private fun MintPicker(
    mints: List<String>,
    picked: String,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = picked.ifEmpty { stringRes(R.string.cashu_pick_mint) },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        mints.forEach { m ->
            DropdownMenuItem(
                text = { Text(m) },
                onClick = {
                    onPick(m)
                    expanded = false
                },
            )
        }
    }
}

// ============================================================================
// Send LN (melt)
// ============================================================================

@Composable
private fun SendLnDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    onDismiss: () -> Unit,
) {
    val state by viewModel.meltState.collectAsState()
    var invoice by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(mints.firstOrNull() ?: "") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cashu_action_send_ln)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuMeltFlowState.Quoting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_getting_quote))
                        }
                    }

                    is CashuMeltFlowState.Quoted -> {
                        Text(
                            stringRes(
                                R.string.cashu_quote_confirm,
                                s.quote.amount.toString(),
                                s.quote.feeReserve.toString(),
                            ),
                        )
                    }

                    is CashuMeltFlowState.Paying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_paying_invoice))
                        }
                    }

                    is CashuMeltFlowState.Completed -> {
                        Text(stringRes(R.string.cashu_paid_amount, s.paidAmount.toString(), s.fees.toString()))
                    }

                    is CashuMeltFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InvoiceForm(
                            invoice = invoice,
                            onInvoiceChange = { invoice = it },
                            onPaste = { scope.launch { clipboard.getText()?.let { invoice = it } } },
                            mints = mints,
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }

                    CashuMeltFlowState.Idle -> {
                        InvoiceForm(
                            invoice = invoice,
                            onInvoiceChange = { invoice = it },
                            onPaste = { scope.launch { clipboard.getText()?.let { invoice = it } } },
                            mints = mints,
                            pickedMint = pickedMint,
                            onMintChange = { pickedMint = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuMeltFlowState.Idle, is CashuMeltFlowState.Error -> {
                    TextButton(
                        onClick = { viewModel.startMelt(pickedMint, invoice) },
                        enabled = invoice.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(R.string.cashu_get_quote)) }
                }

                is CashuMeltFlowState.Quoted -> {
                    TextButton(onClick = { viewModel.confirmMelt() }) {
                        Text(stringRes(R.string.cashu_pay_invoice))
                    }
                }

                is CashuMeltFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun InvoiceForm(
    invoice: String,
    onInvoiceChange: (String) -> Unit,
    onPaste: () -> Unit,
    mints: List<String>,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = invoice,
            onValueChange = onInvoiceChange,
            label = { Text(stringRes(R.string.cashu_invoice_bolt11)) },
            placeholder = { Text("lnbc…") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPaste) {
            Icon(MaterialSymbols.ContentPaste, contentDescription = stringRes(R.string.paste_from_clipboard))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

// ============================================================================
// Send as token
// ============================================================================

@Composable
private fun SendTokenDialog(
    viewModel: CashuWalletViewModel,
    mints: List<String>,
    onDismiss: () -> Unit,
) {
    val state by viewModel.sendTokenState.collectAsState()
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var pickedMint by remember { mutableStateOf(mints.firstOrNull() ?: "") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cashu_action_send_token)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuSendTokenFlowState.Building -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_building_token))
                        }
                    }

                    is CashuSendTokenFlowState.Ready -> {
                        Text(stringRes(R.string.cashu_token_ready, s.amount.toString()))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = s.token,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { scope.launch { clipboard.setText(s.token) } }) {
                            Icon(
                                symbol = MaterialSymbols.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringRes(R.string.cashu_copy_token))
                        }
                    }

                    is CashuSendTokenFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SendTokenForm(amount, { amount = it }, memo, { memo = it }, mints, pickedMint, { pickedMint = it })
                    }

                    CashuSendTokenFlowState.Idle -> {
                        SendTokenForm(amount, { amount = it }, memo, { memo = it }, mints, pickedMint, { pickedMint = it })
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuSendTokenFlowState.Idle, is CashuSendTokenFlowState.Error -> {
                    TextButton(
                        onClick = {
                            val n = amount.toLongOrNull() ?: 0L
                            viewModel.sendAsToken(pickedMint, n, memo.ifBlank { null })
                        },
                        enabled = amount.isNotBlank() && pickedMint.isNotBlank(),
                    ) { Text(stringRes(R.string.cashu_create_token)) }
                }

                is CashuSendTokenFlowState.Ready -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun SendTokenForm(
    amount: String,
    onAmount: (String) -> Unit,
    memo: String,
    onMemo: (String) -> Unit,
    mints: List<String>,
    pickedMint: String,
    onMintChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = amount,
        onValueChange = { v -> onAmount(v.filter { it.isDigit() }) },
        label = { Text(stringRes(R.string.cashu_amount_sats)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = memo,
        onValueChange = onMemo,
        label = { Text(stringRes(R.string.cashu_memo_optional)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MintPicker(mints, pickedMint, onMintChange)
}

// ============================================================================
// Redeem
// ============================================================================

@Composable
private fun RedeemDialog(
    viewModel: CashuWalletViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.redeemState.collectAsState()
    var token by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.cashu_action_redeem)) },
        text = {
            Column {
                when (val s = state) {
                    is CashuRedeemFlowState.Redeeming -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringRes(R.string.cashu_redeeming))
                        }
                    }

                    is CashuRedeemFlowState.Completed -> {
                        Text(stringRes(R.string.cashu_redeemed_amount, s.amount.toString()))
                    }

                    is CashuRedeemFlowState.Error -> {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TokenInput(token, { token = it }) {
                            scope.launch { clipboard.getText()?.let { token = it } }
                        }
                    }

                    CashuRedeemFlowState.Idle -> {
                        TokenInput(token, { token = it }) {
                            scope.launch { clipboard.getText()?.let { token = it } }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is CashuRedeemFlowState.Idle, is CashuRedeemFlowState.Error -> {
                    TextButton(
                        onClick = { viewModel.redeemToken(token) },
                        enabled = token.isNotBlank(),
                    ) { Text(stringRes(R.string.cashu_redeem_button)) }
                }

                is CashuRedeemFlowState.Completed -> {
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.cashu_done)) }
                }

                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

@Composable
private fun TokenInput(
    token: String,
    onTokenChange: (String) -> Unit,
    onPaste: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringRes(R.string.cashu_token_label)) },
            placeholder = { Text("cashuB… / cashuA…") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPaste) {
            Icon(MaterialSymbols.ContentPaste, contentDescription = stringRes(R.string.paste_from_clipboard))
        }
    }
}
