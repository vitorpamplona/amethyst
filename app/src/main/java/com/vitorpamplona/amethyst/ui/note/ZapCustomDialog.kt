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

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.LnZapEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException

class ZapOptionstViewModel : ViewModel() {
    private var account: Account? = null

    var customAmount by mutableStateOf(TextFieldValue("21"))
    var customMessage by mutableStateOf(TextFieldValue(""))

    fun load(account: Account) {
        this.account = account
    }

    fun canSend(): Boolean {
        return value() != null
    }

    fun value(): Long? {
        return customAmount.text.trim().toLongOrNull()
    }

    fun cancel() {}
}

@Composable
fun ZapCustomDialog(
    onClose: () -> Unit,
    onError: (title: String, text: String) -> Unit,
    onProgress: (percent: Float) -> Unit,
    onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
    accountViewModel: AccountViewModel,
    baseNote: Note,
) {
    val context = LocalContext.current
    val postViewModel: ZapOptionstViewModel = viewModel()

    LaunchedEffect(accountViewModel) { postViewModel.load(accountViewModel.account) }

    val zapTypes =
        listOf(
            Triple(
                LnZapEvent.ZapType.PUBLIC,
                stringResource(id = R.string.zap_type_public),
                stringResource(id = R.string.zap_type_public_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.PRIVATE,
                stringResource(id = R.string.zap_type_private),
                stringResource(id = R.string.zap_type_private_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.ANONYMOUS,
                stringResource(id = R.string.zap_type_anonymous),
                stringResource(id = R.string.zap_type_anonymous_explainer),
            ),
            Triple(
                LnZapEvent.ZapType.NONZAP,
                stringResource(id = R.string.zap_type_nonzap),
                stringResource(id = R.string.zap_type_nonzap_explainer),
            ),
        )

    val zapOptions =
        remember {
            zapTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
        }
    var selectedZapType by
        remember(accountViewModel) { mutableStateOf(accountViewModel.account.defaultZapType) }

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

                    ZapButton(
                        isActive = postViewModel.canSend() && !baseNote.isDraft(),
                    ) {
                        accountViewModel.zap(
                            baseNote,
                            postViewModel.value()!! * 1000L,
                            null,
                            postViewModel.customMessage.text,
                            context,
                            onError = onError,
                            onProgress = onProgress,
                            onPayViaIntent = onPayViaIntent,
                            zapType = selectedZapType,
                        )
                        onClose()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        // stringResource(R.string.new_amount_in_sats
                        label = { Text(text = stringResource(id = R.string.amount_in_sats)) },
                        value = postViewModel.customAmount,
                        onValueChange = { postViewModel.customAmount = it },
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Number,
                            ),
                        placeholder = {
                            Text(
                                text = "100, 1000, 5000",
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.padding(end = 5.dp).weight(1f),
                    )

                    TextSpinner(
                        label = stringResource(id = R.string.zap_type),
                        placeholder =
                            zapTypes
                                .filter { it.first == accountViewModel.account.defaultZapType }
                                .first()
                                .second,
                        options = zapOptions,
                        onSelect = { selectedZapType = zapTypes[it].first },
                        modifier = Modifier.weight(1f).padding(end = 5.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        // stringResource(R.string.new_amount_in_sats
                        label = {
                            if (
                                selectedZapType == LnZapEvent.ZapType.PUBLIC ||
                                selectedZapType == LnZapEvent.ZapType.ANONYMOUS
                            ) {
                                Text(text = stringResource(id = R.string.custom_zaps_add_a_message))
                            } else if (selectedZapType == LnZapEvent.ZapType.PRIVATE) {
                                Text(text = stringResource(id = R.string.custom_zaps_add_a_message_private))
                            } else if (selectedZapType == LnZapEvent.ZapType.NONZAP) {
                                Text(text = stringResource(id = R.string.custom_zaps_add_a_message_nonzap))
                            }
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
                        modifier = Modifier.padding(end = 5.dp).weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun ZapButton(
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
        Text(text = "⚡Zap ", color = Color.White)
    }
}

@Composable
fun ErrorMessageDialog(
    title: String,
    textContent: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickStartMessage: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(textContent) } },
        confirmButton = {
            Row(
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                onClickStartMessage?.let {
                    TextButton(onClick = onClickStartMessage) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dm),
                            contentDescription = null,
                        )
                        Spacer(StdHorzSpacer)
                        Text(stringResource(R.string.error_dialog_talk_to_user))
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = Size16dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Done,
                            contentDescription = null,
                        )
                        Spacer(StdHorzSpacer)
                        Text(stringResource(R.string.error_dialog_button_ok))
                    }
                }
            }
        },
    )
}

@Composable
fun PayViaIntentDialog(
    payingInvoices: ImmutableList<ZapPaymentHandler.Payable>,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current

    if (payingInvoices.size == 1) {
        payViaIntent(payingInvoices.first().invoice, context, onError)
        onClose()
    } else {
        Dialog(
            onDismissRequest = onClose,
            properties =
                DialogProperties(
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
        ) {
            Surface {
                Column(modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CloseButton(onPress = onClose)
                    }

                    Spacer(modifier = DoubleVertSpacer)

                    payingInvoices.forEachIndexed { index, it ->
                        val paid = remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = Size10dp),
                        ) {
                            if (it.user != null) {
                                BaseUserPicture(it.user, Size55dp, accountViewModel = accountViewModel)
                            } else {
                                DisplayBlankAuthor(size = Size55dp)
                            }

                            Spacer(modifier = DoubleHorzSpacer)

                            Column(modifier = Modifier.weight(1f)) {
                                if (it.user != null) {
                                    UsernameDisplay(it.user)
                                } else {
                                    Text(
                                        text = stringResource(id = R.string.wallet_number, index + 1),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    )
                                }
                                Row {
                                    Text(
                                        text = showAmount((it.amountMilliSats / 1000.0f).toBigDecimal()),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    )
                                    Spacer(modifier = StdHorzSpacer)
                                    Text(
                                        text = stringResource(id = R.string.sats),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    )
                                }
                            }

                            Spacer(modifier = DoubleHorzSpacer)

                            PayButton(isActive = !paid.value) {
                                paid.value = true

                                payViaIntent(it.invoice, context, onError)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun payViaIntent(
    invoice: String,
    context: Context,
    onError: (String) -> Unit,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoice"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        ContextCompat.startActivity(context, intent, null)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        // don't display ugly error messages
        // if (e.message != null) {
        //   onError(context.getString(R.string.no_wallet_found_with_error, e.message!!))
        // } else {
        onError(context.getString(R.string.no_wallet_found))
        // }
    }
}

@Composable
fun PayButton(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onPost: () -> Unit = {},
) {
    Button(
        modifier = modifier,
        onClick = onPost,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            ),
        contentPadding = ZeroPadding,
    ) {
        if (isActive) {
            Text(text = stringResource(R.string.pay), color = Color.White)
        } else {
            Text(text = stringResource(R.string.paid), color = Color.White)
        }
    }
}
