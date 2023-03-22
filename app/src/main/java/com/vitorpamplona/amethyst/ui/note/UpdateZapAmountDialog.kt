package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.model.Contact
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.qrcode.SimpleQrCodeScanner
import kotlinx.coroutines.launch

class UpdateZapAmountViewModel : ViewModel() {
    private var account: Account? = null

    var nextAmount by mutableStateOf(TextFieldValue(""))
    var amountSet by mutableStateOf(listOf<Long>())
    var walletConnectRelay by mutableStateOf(TextFieldValue(""))
    var walletConnectPubkey by mutableStateOf(TextFieldValue(""))

    fun load(account: Account) {
        this.account = account
        this.amountSet = account.zapAmountChoices
        this.walletConnectPubkey = account.zapPaymentRequest?.pubKeyHex?.let { TextFieldValue(it) } ?: TextFieldValue("")
        this.walletConnectRelay = account.zapPaymentRequest?.relayUri?.let { TextFieldValue(it) } ?: TextFieldValue("")
    }

    fun toListOfAmounts(commaSeparatedAmounts: String): List<Long> {
        return commaSeparatedAmounts.split(",").map { it.trim().toLongOrNull() ?: 0 }
    }

    fun addAmount() {
        val newValue = nextAmount.text.trim().toLongOrNull()
        if (newValue != null) {
            amountSet = amountSet + newValue
        }

        nextAmount = TextFieldValue("")
    }

    fun removeAmount(amount: Long) {
        amountSet = amountSet - amount
    }

    fun sendPost() {
        account?.changeZapAmounts(amountSet)

        if (walletConnectRelay.text.isNotBlank() && walletConnectPubkey.text.isNotBlank()) {
            account?.changeZapPaymentRequest(Contact(walletConnectPubkey.text, walletConnectRelay.text))
        } else {
            account?.changeZapPaymentRequest(null)
        }

        nextAmount = TextFieldValue("")
    }

    fun cancel() {
        nextAmount = TextFieldValue("")
    }

    fun hasChanged(): Boolean {
        return (
            amountSet != account?.zapAmountChoices ||
                walletConnectPubkey.text != (account?.zapPaymentRequest?.pubKeyHex ?: "") ||
                walletConnectRelay.text != (account?.zapPaymentRequest?.relayUri ?: "")
            )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateZapAmountDialog(onClose: () -> Unit, account: Account) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val postViewModel: UpdateZapAmountViewModel = viewModel()

    LaunchedEffect(account) {
        postViewModel.load(account)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface() {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = {
                        postViewModel.cancel()
                        onClose()
                    })

                    SaveButton(
                        onPost = {
                            postViewModel.sendPost()
                            onClose()
                        },
                        isActive = postViewModel.hasChanged()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.animateContentSize()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            postViewModel.amountSet.forEach { amountInSats ->
                                Button(
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary
                                    ),
                                    onClick = {
                                        postViewModel.removeAmount(amountInSats)
                                    }
                                ) {
                                    Text(
                                        "⚡ ${showAmount(amountInSats.toBigDecimal().setScale(1))} ✖",
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.new_amount_in_sats)) },
                        value = postViewModel.nextAmount,
                        onValueChange = {
                            postViewModel.nextAmount = it
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number
                        ),
                        placeholder = {
                            Text(
                                text = "100, 1000, 5000",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .weight(1f)
                    )

                    Button(
                        onClick = { postViewModel.addAmount() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Text(text = stringResource(R.string.add), color = Color.White)
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 0.25.dp
                )

                var qrScanning by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(id = R.string.wallet_connect_service), Modifier.weight(1f))
                    IconButton(onClick = {
                        qrScanning = true
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_qrcode),
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.wallet_connect_service_explainer),
                        Modifier.weight(1f),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                        fontSize = 14.sp
                    )
                }

                if (qrScanning) {
                    SimpleQrCodeScanner {
                        qrScanning = false
                        if (!it.isNullOrEmpty()) {
                            try {
                                val contact = Nip47.parse(it)
                                if (contact != null) {
                                    postViewModel.walletConnectPubkey = TextFieldValue(contact.pubKeyHex)
                                    postViewModel.walletConnectRelay = TextFieldValue(contact.relayUri ?: "")
                                }
                            } catch (e: IllegalArgumentException) {
                                scope.launch {
                                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.wallet_connect_service_pubkey)) },
                        value = postViewModel.walletConnectPubkey,
                        onValueChange = {
                            postViewModel.walletConnectPubkey = it
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None
                        ),
                        placeholder = {
                            Text(
                                text = "npub, hex",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.wallet_connect_service_relay)) },
                        modifier = Modifier.weight(1f),
                        value = postViewModel.walletConnectRelay,
                        onValueChange = { postViewModel.walletConnectRelay = it },
                        placeholder = {
                            Text(
                                text = "relay.server.com",
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                maxLines = 1
                            )
                        },
                        singleLine = true
                    )
                }
            }
        }
    }
}
