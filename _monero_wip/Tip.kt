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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.PendingTransaction
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.note.MoneroIcon
import com.vitorpamplona.amethyst.ui.note.TipTypesTextSpinner
import com.vitorpamplona.amethyst.ui.note.decToPiconero
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import com.vitorpamplona.quartz.experimental.moneroTips.TipSplitSetup
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TipCard(
    address: String,
    toUserPubKeyHex: String,
    account: Account,
    titleText: String? = null,
    buttonText: String? = null,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    onError: (String, String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
        ) {
            Tip(
                address,
                toUserPubKeyHex,
                account,
                titleText,
                buttonText,
                onSuccess,
                onClose,
                onError,
            )
        }
    }
}

@Composable
fun Tip(
    address: String,
    toUserPubKeyHex: String,
    account: Account,
    titleText: String? = null,
    buttonText: String? = null,
    onSuccess: () -> Unit,
    onClose: () -> Unit,
    onError: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
    ) {
        MoneroIcon(Size20Modifier)

        Text(
            text = titleText ?: stringResource(R.string.monero_tips),
            fontSize = 20.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(start = 10.dp),
        )
    }

    HorizontalDivider(thickness = DividerThickness)

    var message by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("0.1") }
    var amountError by remember { mutableStateOf("") }
    var tipType by remember { mutableStateOf(account.defaultTipType) }

    var sending by remember { mutableStateOf(false) }

    if (tipType != TipEvent.TipType.PRIVATE) {
        OutlinedTextField(
            label = { Text(text = stringResource(R.string.note_to_receiver)) },
            modifier = Modifier.fillMaxWidth(),
            value = message,
            onValueChange = { message = it },
            placeholder = {
                Text(
                    text = stringResource(R.string.thank_you_so_much),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            singleLine = true,
        )
    }

    OutlinedTextField(
        label = { Text(text = stringResource(R.string.amount_in_xmr)) },
        modifier = Modifier.fillMaxWidth(),
        value = amount,
        onValueChange = {
            runCatching {
                if (it.isEmpty()) {
                    amount = "0"
                } else {
                    amount = it
                }
            }
        },
        placeholder = {
            Text(
                text = "0.1",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
            ),
        singleLine = true,
        isError = amountError.isNotEmpty(),
        supportingText =
            if (amountError.isNotEmpty()) {
                { Text(amountError) }
            } else {
                null
            },
    )

    TipTypesTextSpinner(
        label = stringResource(R.string.tip_type),
        selected = account.defaultTipType,
        onSelect = {
            tipType = it
        },
    )

    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        onClick = {
            if (!sending) {
                scope.launch(Dispatchers.IO) {
                    val tip = TipSplitSetup(address, null, null, true)
                    val amount = amount.toDoubleOrNull()?.let { decToPiconero(amount) }
                    if (amount == null || amount == 0UL) {
                        amountError = context.getString(R.string.invalid_monero_amount)
                        return@launch
                    }
                    amountError = ""

                    sending = true
                    val transaction = account.tip(listOf(tip), amount, account.defaultMoneroTransactionPriority)
                    if (transaction.status.type != PendingTransaction.StatusType.OK) {
                        sending = false

                        val error = "${transaction.status.error[0].uppercase()}${transaction.status.error.substring(1)}"
                        onError(
                            context.getString(R.string.error_dialog_tip_error),
                            error,
                        )
                        return@launch
                    } else if (tipType == TipEvent.TipType.PRIVATE) {
                        sending = false
                    }

                    if (tipType != TipEvent.TipType.PRIVATE) {
                        val tips = listOf(tip)

                        val signer =
                            if (tipType == TipEvent.TipType.PUBLIC) {
                                null
                            } else {
                                val keyPair = KeyPair()
                                NostrSignerInternal(keyPair)
                            }

                        val proofMessage =
                            if (tipType == TipEvent.TipType.PUBLIC) {
                                account.userProfile().pubkeyHex
                            } else {
                                signer!!.pubKey
                            }

                        var proofsWithStatus = account.getProofs(transaction.txId, tips, proofMessage)
                        while (proofsWithStatus.any { (proof, _) -> !proof.status.isOk() }) {
                            proofsWithStatus = account.getProofs(transaction.txId, tips, proofMessage)
                        }
                        val proofs: MutableMap<String, Array<String>> = mutableMapOf()
                        for ((proof, recipient) in proofsWithStatus) {
                            proofs += proof.proof to arrayOf(recipient)
                        }

                        account.sendTipProof(
                            null,
                            setOf(toUserPubKeyHex),
                            transaction.txId,
                            proofs,
                            tipType,
                            message,
                            signer,
                        ) {
                            sending = false
                            onSuccess()
                        }
                    }
                }
            }
        },
        shape = QuoteBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
    ) {
        if (sending) {
            LoadingAnimation()
            Spacer(modifier = DoubleHorzSpacer)
        }

        Text(
            text = buttonText ?: stringResource(R.string.send_monero),
            color = Color.White,
            fontSize = 20.sp,
        )
    }
}
