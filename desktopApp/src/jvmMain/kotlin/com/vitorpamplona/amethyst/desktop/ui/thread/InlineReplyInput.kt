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
package com.vitorpamplona.amethyst.desktop.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import kotlinx.coroutines.launch

sealed interface SendState {
    data object Idle : SendState

    data object Sending : SendState

    data class Error(
        val message: String,
    ) : SendState
}

@Composable
fun InlineReplyInput(
    myAvatarUrl: String?,
    onSend: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var sendState by remember { mutableStateOf<SendState>(SendState.Idle) }
    val scope = rememberCoroutineScope()

    val isSending = sendState is SendState.Sending

    fun doSend() {
        val content = text.trim()
        if (content.isEmpty() || isSending) return
        sendState = SendState.Sending
        scope.launch {
            try {
                onSend(content)
                text = ""
                sendState = SendState.Idle
            } catch (e: Exception) {
                sendState = SendState.Error(e.message ?: "Failed to send reply")
            }
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                userHex = "",
                pictureUrl = myAvatarUrl,
                size = 32.dp,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    // Clear error on new input
                    if (sendState is SendState.Error) sendState = SendState.Idle
                },
                placeholder = {
                    Text(
                        "Add a comment...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                val modifierHeld =
                                    if (PlatformInfo.isMacOS) {
                                        keyEvent.isMetaPressed
                                    } else {
                                        keyEvent.isCtrlPressed
                                    }
                                if (modifierHeld) {
                                    doSend()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = false,
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { doSend() },
                enabled = text.isNotBlank() && !isSending,
                shape = RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier = Modifier.height(36.dp),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        MaterialSymbols.AutoMirrored.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Send", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Error message
        val error = sendState
        if (error is SendState.Error) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            )
        }
    }
}
