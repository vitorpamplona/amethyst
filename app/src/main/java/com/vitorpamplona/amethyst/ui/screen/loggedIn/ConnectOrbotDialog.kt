package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material.MaterialRichText
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.components.richTextDefaults
import kotlinx.coroutines.launch

@Composable
fun ConnectOrbotDialog(onClose: () -> Unit, onPost: () -> Unit, portNumber: MutableState<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface() {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CloseButton(onCancel = {
                        onClose()
                    })

                    val toastMessage = stringResource(R.string.invalid_port_number)

                    UseOrbotButton(
                        onPost = {
                            try {
                                Integer.parseInt(portNumber.value)
                            } catch (_: Exception) {
                                scope.launch {
                                    Toast.makeText(
                                        context,
                                        toastMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@UseOrbotButton
                            }

                            onPost()
                        },
                        isActive = true
                    )
                }

                Column(
                    modifier = Modifier.padding(30.dp)
                ) {
                    val myMarkDownStyle = richTextDefaults.copy(
                        stringStyle = richTextDefaults.stringStyle?.copy(
                            linkStyle = SpanStyle(
                                textDecoration = TextDecoration.Underline,
                                color = MaterialTheme.colors.primary
                            )
                        )
                    )

                    Row() {
                        MaterialRichText(
                            style = myMarkDownStyle
                        ) {
                            Markdown(
                                content = stringResource(R.string.connect_through_your_orbot_setup_markdown)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedTextField(
                            value = portNumber.value,
                            onValueChange = { portNumber.value = it },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Number
                            ),
                            label = { Text(text = stringResource(R.string.orbot_socks_port)) },
                            placeholder = {
                                Text(
                                    text = "9050",
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UseOrbotButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = stringResource(R.string.use_orbot), color = Color.White)
    }
}
