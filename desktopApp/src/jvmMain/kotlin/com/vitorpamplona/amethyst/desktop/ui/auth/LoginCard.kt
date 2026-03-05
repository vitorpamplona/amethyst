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
package com.vitorpamplona.amethyst.desktop.ui.auth

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_button
import com.vitorpamplona.amethyst.commons.resources.login_card_subtitle
import com.vitorpamplona.amethyst.commons.resources.login_card_title
import com.vitorpamplona.amethyst.commons.resources.login_generate_button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private val HEX_64_REGEX = Regex("^[0-9a-fA-F]{64}$")

fun validateBunkerUri(input: String): String? {
    val trimmed = input.trim()
    if (!trimmed.startsWith("bunker://", ignoreCase = true)) return "Not a bunker URI"

    val afterScheme = trimmed.substring("bunker://".length)
    val parts = afterScheme.split("?", limit = 2)
    val pubkeyPart = parts[0]

    if (pubkeyPart.length != 64 || !pubkeyPart.matches(HEX_64_REGEX)) {
        return "Invalid bunker URI. Expected: bunker://<64-hex-chars>?relay=wss://..."
    }

    if (parts.size < 2 || !parts[1].contains("relay=wss://", ignoreCase = true)) {
        return "Bunker URI must include at least one relay parameter (relay=wss://...)"
    }

    return null // valid
}

@Composable
fun LoginCard(
    onLogin: (String) -> Result<Unit>,
    onGenerateNew: () -> Unit,
    onLoginBunker: (suspend (String) -> Result<Unit>)? = null,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 400.dp,
    title: String = stringResource(Res.string.login_card_title),
    subtitle: String = stringResource(Res.string.login_card_subtitle),
) {
    var keyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isBunker = keyInput.trim().startsWith("bunker://", ignoreCase = true)

    Card(
        modifier = modifier.width(cardWidth),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KeyInputField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = null
                    },
                    errorMessage = errorMessage,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val decoded = scanQrFromWebcam()
                            withContext(Dispatchers.Main) {
                                if (decoded != null) {
                                    keyInput = decoded
                                    errorMessage = null
                                } else {
                                    errorMessage = "No QR code found"
                                }
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isBunker) {
                Text(
                    "This URI connects to your remote signer. Treat it like a password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (isConnecting) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Connecting to remote signer...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (isBunker && onLoginBunker != null) {
                                val validationError = validateBunkerUri(keyInput)
                                if (validationError != null) {
                                    errorMessage = validationError
                                    return@Button
                                }
                                isConnecting = true
                                errorMessage = null
                                scope.launch(Dispatchers.IO) {
                                    val result = onLoginBunker(keyInput.trim())
                                    withContext(Dispatchers.Main) {
                                        result.fold(
                                            onSuccess = { isConnecting = false },
                                            onFailure = {
                                                errorMessage = it.message
                                                isConnecting = false
                                            },
                                        )
                                    }
                                }
                            } else {
                                onLogin(keyInput).fold(
                                    onSuccess = { /* handled by caller */ },
                                    onFailure = { errorMessage = it.message },
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = keyInput.isNotBlank(),
                    ) {
                        Text(if (isBunker) "Connect to Signer" else stringResource(Res.string.login_button))
                    }

                    OutlinedButton(
                        onClick = onGenerateNew,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(Res.string.login_generate_button))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun LoginCardPreview() {
    LoginCard(
        onLogin = { Result.success(Unit) },
        onGenerateNew = {},
    )
}
