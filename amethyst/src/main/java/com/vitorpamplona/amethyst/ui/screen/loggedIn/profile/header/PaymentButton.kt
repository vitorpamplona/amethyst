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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.IAccountViewModel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent

@Composable
fun PaymentButton(
    user: User,
    accountViewModel: IAccountViewModel,
) {
    val address =
        remember(user.pubkeyHex) {
            PaymentTargetsEvent.createAddress(user.pubkeyHex)
        }

    LoadAddressableNote(address, accountViewModel) { note ->
        val targets =
            remember(note) {
                (note?.event as? PaymentTargetsEvent)?.paymentTargets() ?: emptyList()
            }
        PaymentButtonWithTargets(targets)
    }
}

@Composable
fun PaymentButtonWithTargets(targets: List<PaymentTarget>) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { expanded = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = stringRes(R.string.payment_targets),
        )
    }

    if (expanded) {
        M3ActionDialog(
            title = stringRes(R.string.payment_targets),
            onDismiss = { expanded = false },
        ) {
            M3ActionSection {
                if (targets.isEmpty()) {
                    M3ActionRow(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        text = stringRes(R.string.no_payment_targets_message),
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    targets.forEach { target ->
                        M3ActionRow(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            text = "${target.type.replaceFirstChar(Char::titlecase)}: ${target.authority}",
                            onClick = {
                                expanded = false
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, "payto://${target.type}/${target.authority}".toUri())
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    context.startActivity(intent)
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
    }

    errorMessage?.let { msg ->
        ErrorMessageDialog(
            title = stringRes(R.string.error_dialog_payment_error),
            textContent = msg,
            onDismiss = { errorMessage = null },
        )
    }
}
