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

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import kotlinx.coroutines.launch

@Composable
fun PaymentButton(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val address =
        remember(user.pubkeyHex) {
            PaymentTargetsEvent.createAddress(user.pubkeyHex)
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
                PaymentButtonWithTargets(targets)
            }
        }
    }
}

@Composable
fun PaymentButtonWithTargets(targets: List<PaymentTarget>) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { expanded = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            symbol = MaterialSymbols.AccountBalanceWallet,
            contentDescription = stringRes(R.string.payment_targets),
        )
    }

    if (expanded) {
        M3ActionDialog(
            title = stringRes(R.string.payment_targets),
            onDismiss = { expanded = false },
        ) {
            M3ActionSection {
                targets.forEach { target ->
                    PaymentTargetRow(
                        target = target,
                        onShowQr = { qrContent = target.authority },
                        onCopy = {
                            scope.launch {
                                clipboardManager.setText(target.authority)
                                Toast
                                    .makeText(
                                        context,
                                        stringRes(context, R.string.copied_to_clipboard),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                        onPay = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, "payto://${target.type}/${target.authority}".toUri())
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                                expanded = false
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                errorMessage = stringRes(context, R.string.no_payment_app_found)
                            }
                        },
                    )
                }
            }
        }
    }

    qrContent?.let { value ->
        PaymentTargetQrDialog(value = value, onDismiss = { qrContent = null })
    }

    errorMessage?.let { msg ->
        ErrorMessageDialog(
            title = stringRes(R.string.error_dialog_payment_error),
            textContent = msg,
            onDismiss = { errorMessage = null },
        )
    }
}

@Composable
private fun PaymentTargetRow(
    target: PaymentTarget,
    onShowQr: () -> Unit,
    onCopy: () -> Unit,
    onPay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.type.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = target.authority,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onShowQr) {
            Icon(
                symbol = MaterialSymbols.QrCode2,
                contentDescription = stringRes(R.string.show_qr),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = stringRes(R.string.copy_to_clipboard),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPay) {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = stringRes(R.string.payment_targets),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaymentTargetQrDialog(
    value: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QrCodeDrawer(
                    contents = value,
                    modifier = Modifier.size(260.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
