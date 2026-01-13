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
package com.vitorpamplona.amethyst.desktop.ui.auth

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.SharedRes
import dev.icerock.moko.resources.compose.stringResource

/**
 * Login card with Nostr key input field and action buttons.
 *
 * @param onLogin Callback when login is attempted with the key input
 * @param onGenerateNew Callback when "Generate New" is clicked
 * @param modifier Modifier for the card
 * @param cardWidth Width of the card (default 400.dp)
 * @param title Card title
 * @param subtitle Subtitle/hint text
 */
@Composable
fun LoginCard(
    onLogin: (String) -> Result<Unit>,
    onGenerateNew: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 400.dp,
    title: String = stringResource(SharedRes.strings.login_card_title),
    subtitle: String = stringResource(SharedRes.strings.login_card_subtitle),
) {
    var keyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

            KeyInputField(
                value = keyInput,
                onValueChange = {
                    keyInput = it
                    errorMessage = null
                },
                errorMessage = errorMessage,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onLogin(keyInput).fold(
                            onSuccess = { /* handled by caller */ },
                            onFailure = { errorMessage = it.message },
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = keyInput.isNotBlank(),
                ) {
                    Text(stringResource(SharedRes.strings.login_button))
                }

                OutlinedButton(
                    onClick = onGenerateNew,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(SharedRes.strings.login_generate_button))
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
