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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayPaymentTargets(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val address =
        remember(baseUser.pubkeyHex) {
            PaymentTargetsEvent.createAddress(baseUser.pubkeyHex)
        }

    LoadAddressableNote(address, accountViewModel) { note ->
        if (note != null) {
            EventFinderFilterAssemblerSubscription(note, accountViewModel)
            val event by observeNoteEvent<PaymentTargetsEvent>(note, accountViewModel)
            val targets =
                remember(event) {
                    event?.paymentTargets() ?: emptyList()
                }
            if (targets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    targets.forEach { target -> PaymentTargetChip(target) }
                }
            }
        }
    }
}

@Composable
private fun PaymentTargetChip(target: PaymentTarget) {
    val style = remember(target.type) { paymentTargetStyleFor(target.type) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringRes(R.string.copy_to_clipboard)
    val copiedMessage = stringRes(R.string.copied_to_clipboard)

    Surface(
        shape = RoundedCornerShape(50),
        color = style.color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, style.color.copy(alpha = 0.35f)),
        modifier =
            Modifier.combinedClickable(
                onClick = {
                    runCatching { uriHandler.openUri(style.uriFor(target.authority)) }
                },
                onLongClick = {
                    scope.launch {
                        clipboard.setText(target.authority)
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                onLongClickLabel = copyLabel,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                symbol = style.symbol,
                contentDescription = style.label,
                tint = style.color,
                modifier = Size16Modifier,
            )
            Text(
                text = style.label,
                color = style.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = shortAddress(target.authority),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

private data class PaymentTargetStyle(
    val symbol: MaterialSymbol,
    val color: Color,
    val label: String,
    val uriFor: (String) -> String,
)

private fun paymentTargetStyleFor(rawType: String): PaymentTargetStyle {
    val type = rawType.trim().lowercase()
    val walletIcon = MaterialSymbols.AccountBalanceWallet
    return when (type) {
        "bitcoin", "btc", "onchain" ->
            PaymentTargetStyle(MaterialSymbols.CurrencyBitcoin, BitcoinOrange, "BITCOIN") { "bitcoin:$it" }
        "lightning", "ln" ->
            PaymentTargetStyle(MaterialSymbols.Bolt, BitcoinOrange, "LIGHTNING") { "lightning:$it" }
        "lnurl" ->
            PaymentTargetStyle(MaterialSymbols.Bolt, BitcoinOrange, "LNURL") { "lightning:$it" }
        "liquid" ->
            PaymentTargetStyle(MaterialSymbols.CurrencyBitcoin, BitcoinOrange, "LIQUID") { "liquidnetwork:$it" }
        "ethereum", "eth" ->
            PaymentTargetStyle(walletIcon, ETHEREUM_PURPLE, "ETHEREUM") { "ethereum:$it" }
        "monero", "xmr" ->
            PaymentTargetStyle(walletIcon, MONERO_ORANGE, "MONERO") { "monero:$it" }
        "dash" ->
            PaymentTargetStyle(walletIcon, DASH_BLUE, "DASH") { "dash:$it" }
        else -> {
            val label = rawType.trim().ifEmpty { "PAY" }.uppercase()
            PaymentTargetStyle(walletIcon, GENERIC_TARGET_COLOR, label) { "payto://$type/$it" }
        }
    }
}

private fun shortAddress(authority: String): String {
    if (authority.contains('@') || authority.contains('/') || authority.length <= 18) return authority
    return authority.take(8) + "…" + authority.takeLast(4)
}

private val ETHEREUM_PURPLE = Color(0xFF627EEA)
private val MONERO_ORANGE = Color(0xFFFF6600)
private val DASH_BLUE = Color(0xFF008CE7)
private val GENERIC_TARGET_COLOR = Color(0xFF7C8DA0)
