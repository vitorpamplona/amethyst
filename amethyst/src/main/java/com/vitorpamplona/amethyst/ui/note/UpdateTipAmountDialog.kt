/**
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.note.buttons.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.tipping.TipEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
class UpdateTipAmountViewModel : ViewModel() {
    var account: Account? = null

    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Double>())
    var selectedTipType by mutableStateOf(TipEvent.TipType.ANONYMOUS)

    fun load(myAccount: Account) {
        this.account = myAccount
        this.amountSet = myAccount.settings.syncedSettings.tips.tipAmountChoices.value
        this.selectedTipType = myAccount.settings.syncedSettings.tips.defaultTipType.value
    }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toDoubleOrNull()
        if (newValue != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Double) {
        amountSet = amountSet - amount
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean =
        selectedTipType !=
            account
                ?.settings
                ?.syncedSettings
                ?.tips
                ?.defaultTipType
                ?.value ||
            amountSet !=
            account
                ?.settings
                ?.syncedSettings
                ?.tips
                ?.tipAmountChoices
                ?.value

    fun sendPost() {
        viewModelScope.launch(Dispatchers.IO) {
            account?.updateTipAmounts(amountSet, selectedTipType)

            nextAmount = TextFieldValue("")
        }
    }
}

@Composable
fun UpdateTipAmountDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val postViewModel: UpdateTipAmountViewModel = viewModel()
    postViewModel.load(accountViewModel.account)
    UpdateTipAmountDialog(postViewModel, accountViewModel, onClose)
}

@Composable
fun UpdateTipAmountDialog(
    postViewModel: UpdateTipAmountViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
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
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
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

                UpdateTipAmountContent(postViewModel, accountViewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateTipAmountContent(
    postViewModel: UpdateTipAmountViewModel,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier =
            Modifier
                .padding(10.dp)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            postViewModel.amountSet.forEach { amountInSats ->
                Button(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    onClick = { postViewModel.removeAmount(amountInSats) },
                ) {
                    Text(
                        "⚡ $amountInSats ✖",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                label = { Text(text = stringRes(R.string.new_amount)) },
                value = postViewModel.nextAmount,
                onValueChange = { postViewModel.nextAmount = it },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Number,
                    ),
                placeholder = {
                    Text(
                        text = "0.01, 0.1, 1, 5",
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                singleLine = true,
                modifier = Modifier.padding(end = 10.dp).weight(1f),
            )

            Button(
                onClick = { postViewModel.addAmount() },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringRes(R.string.add), color = Color.White)
            }
        }

        val tipTypes =
            listOf(
                Triple(
                    TipEvent.TipType.PUBLIC,
                    stringRes(id = R.string.zap_type_public),
                    stringRes(id = R.string.zap_type_public_explainer),
                ),
                Triple(
                    TipEvent.TipType.ANONYMOUS,
                    stringRes(id = R.string.zap_type_anonymous),
                    stringRes(id = R.string.zap_type_anonymous_explainer),
                ),
                Triple(
                    TipEvent.TipType.NONTIP,
                    stringRes(id = R.string.tip_type_nontip),
                    stringRes(id = R.string.tip_type_nontip_explainer),
                ),
            )

        val tipOptions =
            remember {
                tipTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
            }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSpinner(
                label = stringRes(id = R.string.tip_type_explainer),
                placeholder =
                    tipTypes.first { it.first == accountViewModel.defaultTipType() }.second,
                options = tipOptions,
                onSelect = { postViewModel.selectedTipType = tipTypes[it].first },
                modifier = Modifier.weight(1f).padding(end = 5.dp),
            )
        }
    }
}
