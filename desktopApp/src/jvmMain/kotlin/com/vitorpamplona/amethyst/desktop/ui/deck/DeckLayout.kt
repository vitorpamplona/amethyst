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
package com.vitorpamplona.amethyst.desktop.ui.deck

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.ui.ZapFeedback
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm
import kotlinx.coroutines.CoroutineScope
import java.awt.Cursor

@Composable
fun DeckLayout(
    deckState: DeckState,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    accountManager: AccountManager,
    account: AccountState.LoggedIn,
    iAccount: com.vitorpamplona.amethyst.desktop.model.DesktopIAccount,
    nwcConnection: Nip47URINorm?,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator,
    appScope: CoroutineScope,
    onShowComposeDialog: () -> Unit,
    onShowReplyDialog: (com.vitorpamplona.quartz.nip01Core.core.Event) -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns by deckState.columns.collectAsState()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidthDp = with(density) { constraints.maxWidth.toDp().value }
        deckState.setAvailableWidth(availableWidthDp)

        // Auto-fit columns on first composition or when available width changes significantly
        LaunchedEffect(availableWidthDp, columns.size) {
            val dividers = (columns.size - 1) * DeckState.DIVIDER_WIDTH
            val totalColumnWidth = columns.sumOf { it.width.toDouble() }.toFloat()
            val diff = kotlin.math.abs(totalColumnWidth + dividers - availableWidthDp)
            if (diff > 20f && columns.isNotEmpty()) {
                deckState.fitColumnsToWidth(availableWidthDp)
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState()),
        ) {
            columns.forEachIndexed { index, column ->
                if (index > 0) {
                    DraggableDivider(
                        onDrag = { delta ->
                            val leftCol = columns[index - 1]
                            val rightCol = columns[index]
                            deckState.resizePair(leftCol.id, rightCol.id, delta, availableWidthDp)
                        },
                    )
                }

                DeckColumnContainer(
                    column = column,
                    canClose = columns.size > 1,
                    onClose = { deckState.removeColumn(column.id) },
                    onDoubleClickHeader = { deckState.expandColumn(column.id, availableWidthDp) },
                    relayManager = relayManager,
                    localCache = localCache,
                    accountManager = accountManager,
                    account = account,
                    iAccount = iAccount,
                    nwcConnection = nwcConnection,
                    subscriptionsCoordinator = subscriptionsCoordinator,
                    appScope = appScope,
                    onShowComposeDialog = onShowComposeDialog,
                    onShowReplyDialog = onShowReplyDialog,
                    onZapFeedback = onZapFeedback,
                )
            }
        }
    }
}

@Composable
private fun DraggableDivider(onDrag: (Float) -> Unit) {
    Box(
        modifier =
            Modifier
                .width(12.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x / density)
                    }
                },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
