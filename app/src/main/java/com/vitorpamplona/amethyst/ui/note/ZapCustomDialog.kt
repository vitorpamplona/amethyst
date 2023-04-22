package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        return try {
            customAmount.text.trim().toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun cancel() {
    }
}

@Composable
fun ZapCustomDialog(onClose: () -> Unit, account: Account, accountViewModel: AccountViewModel, baseNote: Note) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val postViewModel: ZapOptionstViewModel = viewModel()
    LaunchedEffect(account) {
        postViewModel.load(account)
    }

    var zappingProgress by remember { mutableStateOf(0f) }

    val zapTypes = listOf(
        Pair(LnZapEvent.ZapType.PUBLIC, "Public"),
        Pair(LnZapEvent.ZapType.PRIVATE, "Private"),
        Pair(LnZapEvent.ZapType.ANONYMOUS, "Anonymous"),
        Pair(LnZapEvent.ZapType.NONZAP, "Non-Zap")
    )

    val zapOptions = zapTypes.map { it.second }
    var selectedZapType by remember { mutableStateOf(zapTypes[0]) }

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

                    ZapButton(
                        isActive = postViewModel.canSend()
                    ) {
                        scope.launch(Dispatchers.IO) {
                            accountViewModel.zap(
                                baseNote,
                                postViewModel.value()!! * 1000L,
                                null,
                                postViewModel.customMessage.text,
                                context,
                                onError = {
                                    zappingProgress = 0f
                                    scope.launch {
                                        Toast
                                            .makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onProgress = {
                                    scope.launch(Dispatchers.Main) {
                                        zappingProgress = it
                                    }
                                },
                                zapType = selectedZapType.first
                            )
                        }
                        onClose()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        // stringResource(R.string.new_amount_in_sats
                        label = { Text(text = stringResource(id = R.string.amount_in_sats)) },
                        value = postViewModel.customAmount,
                        onValueChange = {
                            postViewModel.customAmount = it
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
                }
                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        // stringResource(R.string.new_amount_in_sats
                        label = { Text(text = stringResource(id = R.string.custom_zaps_add_a_message)) },
                        value = postViewModel.customMessage,
                        onValueChange = {
                            postViewModel.customMessage = it
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Text
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.custom_zaps_add_a_message_example),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .weight(1f)
                    )
                }
                TextSpinner(
                    label = "Zap Type",
                    placeholder = "Public",
                    options = zapOptions,
                    onSelect = {
                        selectedZapType = zapTypes[it]
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ZapButton(isActive: Boolean, onPost: () -> Unit) {
    Button(
        onClick = { onPost() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = "⚡Zap ", color = Color.White)
    }
}
