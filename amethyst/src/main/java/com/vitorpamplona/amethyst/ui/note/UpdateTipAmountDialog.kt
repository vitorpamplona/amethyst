/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.model.TransactionPriority
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import kotlinx.collections.immutable.toImmutableList

class UpdateTipAmountViewModel(val account: Account) : ViewModel() {
    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<String>())
    var selectedTipType by mutableStateOf(TipEvent.TipType.PRIVATE)
    var selectedTransactionPriority by mutableStateOf(TransactionPriority.UNIMPORTANT)

    fun load() {
        this.amountSet = account.tipAmountChoices
        this.selectedTipType = account.defaultTipType
        this.selectedTransactionPriority = account.defaultMoneroTransactionPriority
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> {
        return commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }
    }

    fun addAmount() {
        val newValue = nextAmount.text.trim()
        // NOTE: toDoubleOrNull is only used for verification
        if (newValue.toDoubleOrNull() != null && decToPiconero(newValue) != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: String) {
        amountSet = amountSet - amount
    }

    fun sendPost() {
        account.changeTipAmounts(amountSet)
        account.changeDefaultTipType(selectedTipType)
        account.changeDefaultMoneroTransactionPriority(selectedTransactionPriority)

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
        selectedTipType = account.defaultTipType
        selectedTransactionPriority = account.defaultMoneroTransactionPriority
    }

    fun hasChanged(): Boolean {
        return (
            selectedTipType != account.defaultTipType ||
                selectedTransactionPriority != account.defaultMoneroTransactionPriority ||
                amountSet != account.tipAmountChoices

        )
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <UpdateTipAmountViewModel : ViewModel> create(modelClass: Class<UpdateTipAmountViewModel>): UpdateTipAmountViewModel {
            return UpdateTipAmountViewModel(account) as UpdateTipAmountViewModel
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateTipAmountDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val postViewModel: UpdateTipAmountViewModel =
        viewModel(
            key = "UpdateTipAmountViewModel",
            factory = UpdateTipAmountViewModel.Factory(accountViewModel.account),
        )

    val uri = LocalUriHandler.current

    LaunchedEffect(accountViewModel) {
        postViewModel.load()
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .imePadding(),
            ) {
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

                    SaveButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged(),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.animateContentSize()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    postViewModel.amountSet.forEach { amount ->
                                        Button(
                                            modifier = Modifier.padding(horizontal = 3.dp),
                                            shape = ButtonBorder,
                                            colors =
                                                ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            onClick = { postViewModel.removeAmount(amount) },
                                        ) {
                                            TipIcon(
                                                Modifier
                                                    .padding(end = 5.dp)
                                                    .size(18.dp),
                                                tint = Color.White,
                                            )

                                            Text(
                                                "$amount ✖",
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                label = { Text(text = stringResource(R.string.new_amount_in_xmr)) },
                                value = postViewModel.nextAmount,
                                onValueChange = { postViewModel.nextAmount = it },
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
                                        .padding(end = 10.dp)
                                        .weight(1f),
                            )

                            Button(
                                onClick = { postViewModel.addAmount() },
                                shape = ButtonBorder,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                            ) {
                                Text(text = stringResource(R.string.add), color = Color.White)
                            }
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TipTypesTextSpinner(
                                label = stringResource(id = R.string.tip_type_explainer),
                                selected = accountViewModel.account.defaultTipType,
                                onSelect = {
                                    postViewModel.selectedTipType = it
                                },
                            )
                        }

                        TextSpinner(
                            label = stringResource(R.string.transaction_priority_explainer),
                            placeholder = accountViewModel.account.defaultMoneroTransactionPriority.toLocalizedString(context),
                            options =
                                TransactionPriority.entries.map {
                                    TitleExplainer(it.toLocalizedString(context))
                                }.toImmutableList(),
                            onSelect = {
                                postViewModel.selectedTransactionPriority = TransactionPriority.entries[it]
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TipTypesTextSpinner(
    label: String,
    selected: TipEvent.TipType,
    onSelect: (TipEvent.TipType) -> Unit,
) {
    val tipTypes =
        listOf(
            Triple(
                TipEvent.TipType.PRIVATE,
                stringResource(id = R.string.tip_type_private),
                stringResource(id = R.string.tip_type_private_explainer),
            ),
            Triple(
                TipEvent.TipType.ANONYMOUS,
                stringResource(id = R.string.tip_type_anonymous),
                stringResource(id = R.string.tip_type_anonymous_explainer),
            ),
            Triple(
                TipEvent.TipType.PUBLIC,
                stringResource(id = R.string.tip_type_public),
                stringResource(id = R.string.tip_type_public_explainer),
            ),
        )

    val tipOptions =
        remember {
            tipTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
        }

    TextSpinner(
        label = label,
        placeholder =
            tipTypes.filter { it.first == selected }.first().second,
        options = tipOptions,
        onSelect = {
            onSelect(tipTypes[it].first)
        },
    )
}
