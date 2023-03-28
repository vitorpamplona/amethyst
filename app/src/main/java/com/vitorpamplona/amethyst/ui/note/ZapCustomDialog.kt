package com.vitorpamplona.amethyst.ui.note

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ZapOptionstViewModel : ViewModel() {
    private var account: Account? = null

    var customAmount by mutableStateOf(TextFieldValue("1000"))
    var customMessage by mutableStateOf(TextFieldValue(""))

    fun load(account: Account) {
        this.account = account
    }

    fun cancel() {
        customAmount = TextFieldValue("")
    }
}

fun isNumeric(toCheck: String): Boolean {
    return toCheck.toLongOrNull() != null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZapCustomDialog(onClose: () -> Unit, account: Account, accountViewModel: AccountViewModel, baseNote: Note) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val postViewModel: ZapOptionstViewModel = viewModel()
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
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(com.vitorpamplona.amethyst.R.drawable.zap),
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                    Text(
                        text = "Custom Zap",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W500,
                        modifier = Modifier.padding(start = 10.dp)
                    )
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
                        label = { Text(text = "Custom Amount") },
                        value = postViewModel.customAmount,
                        onValueChange = {
                            if (isNumeric(it.text)) {
                                postViewModel.customAmount = it
                            }
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Number
                        ),
                        placeholder = {
                            Text(
                                text = postViewModel.customAmount.text,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        // stringResource(R.string.new_amount_in_sats
                        label = { Text(text = "Message") },
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
                                text = postViewModel.customMessage.text,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .weight(1f)
                    )

                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                accountViewModel.zap(
                                    baseNote,
                                    postViewModel.customAmount.text.toLong() * 1000,
                                    postViewModel.customMessage.text,
                                    context,
                                    onError = {
                                        Toast
                                            .makeText(context, it, Toast.LENGTH_SHORT).show()
                                    },
                                    onProgress = {
                                        scope.launch(Dispatchers.Main) {
                                        }
                                    }
                                )
                            }
                            onClose()
                        }
                    ) {
                        Text(text = "⚡Zap ", color = Color.White)
                    }
                }
            }
        }
    }
}
