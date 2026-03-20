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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TransactionPriority
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import kotlinx.collections.immutable.toImmutableList

class TipOptionsViewModel : ViewModel() {
    private var account: Account? = null

    var customAmount by mutableStateOf(TextFieldValue("0.01"))
    var customMessage by mutableStateOf(TextFieldValue(""))

    fun load(account: Account) {
        this.account = account
    }

    fun canSend(): Boolean = value() != null

    fun value(): ULong? =
        customAmount.text.trim().let { amount ->
            if (amount.toDoubleOrNull() != null) {
                decToPiconero(amount)
            } else {
                null
            }
        }

    fun cancel() {
        customAmount = TextFieldValue("0.01")
        customMessage = TextFieldValue("")
    }
}

@Composable
fun TipCustomDialog(
    onClose: () -> Unit,
    onError: (title: String, text: String) -> Unit,
    onNotEnoughMoney: (Long, Long, Long) -> Unit,
    onProgress: (percent: Float) -> Unit,
    accountViewModel: AccountViewModel,
    baseNote: Note,
) {
    val context = LocalContext.current
    val postViewModel: TipOptionsViewModel = viewModel()

    LaunchedEffect(accountViewModel) { postViewModel.load(accountViewModel.account) }

    var selectedTipType by
        remember(accountViewModel) { mutableStateOf(accountViewModel.account.defaultTipType) }
    var selectedTransactionPriority by
        remember(accountViewModel) { mutableStateOf(accountViewModel.account.defaultMoneroTransactionPriority) }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )

                    TipButton(
                        text = "Tip ",
                        isActive = postViewModel.canSend() && !baseNote.isDraft(),
                    ) {
                        postViewModel.value()?.let { value: ULong ->
                            accountViewModel.tip(
                                baseNote,
                                value,
                                postViewModel.customMessage.text,
                                context,
                                onError = onError,
                                onNotEnoughMoney = onNotEnoughMoney,
                                onProgress = onProgress,
                                tipType = selectedTipType,
                                priority = selectedTransactionPriority,
                            )
                            postViewModel.cancel()
                            onClose()
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(id = R.string.amount_in_xmr)) },
                        value = postViewModel.customAmount,
                        onValueChange = { postViewModel.customAmount = it },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Number,
                            ),
                        placeholder = {
                            Text(
                                text = "0.05, 0.1, 0.5",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                        modifier =
                            Modifier
                                .padding(end = 5.dp)
                                .weight(1f),
                    )

                    TextSpinner(
                        label = stringResource(R.string.transaction_priority),
                        placeholder = selectedTransactionPriority.toLocalizedString(context),
                        options =
                            TransactionPriority.entries
                                .map {
                                    TitleExplainer(it.toLocalizedString(context))
                                }.toImmutableList(),
                        onSelect = { selectedTransactionPriority = TransactionPriority.entries[it] },
                        modifier =
                            Modifier
                                .padding(end = 5.dp)
                                .weight(1f),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .padding(end = 5.dp),
                ) {
                    TipTypesTextSpinner(
                        label = stringResource(id = R.string.tip_type),
                        selected = accountViewModel.account.defaultTipType,
                        onSelect = { selectedTipType = it },
                    )
                }

                if (selectedTipType != TipEvent.TipType.PRIVATE) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            label = {
                                Text(text = stringResource(id = R.string.custom_tips_add_a_message))
                            },
                            value = postViewModel.customMessage,
                            onValueChange = { postViewModel.customMessage = it },
                            keyboardOptions =
                                KeyboardOptions.Default.copy(
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Text,
                                ),
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.custom_zaps_add_a_message_example),
                                    color = MaterialTheme.colorScheme.placeholderText,
                                )
                            },
                            singleLine = true,
                            modifier =
                                Modifier
                                    .padding(end = 5.dp)
                                    .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TipButton(
    text: String,
    isActive: Boolean,
    onPost: () -> Unit,
) {
    Button(
        onClick = { if (isActive) onPost() },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            ),
    ) {
        TipIcon(
            modifier =
                Modifier
                    .padding(end = 5.dp)
                    .size(18.dp),
            tint = Color.White,
        )
        Text(text = text, color = Color.White)
    }
}
