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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.SharedRes
import dev.icerock.moko.resources.compose.stringResource

/**
 * Warning card displayed after generating a new Nostr key pair.
 * Reminds users to save their keys and shows both public and secret keys.
 *
 * @param npub The public key in npub format
 * @param nsec The secret key in nsec format (nullable for read-only accounts)
 * @param onContinue Callback when user acknowledges they've saved their keys
 * @param modifier Modifier for the card
 * @param cardWidth Width of the card (default 500.dp)
 */
@Composable
fun NewKeyWarningCard(
    npub: String,
    nsec: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 500.dp,
) {
    Card(
        modifier = modifier.width(cardWidth),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                stringResource(SharedRes.strings.new_key_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(SharedRes.strings.new_key_warning_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(SharedRes.strings.new_key_public_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectableKeyText(npub)

            Spacer(Modifier.height(12.dp))

            nsec?.let { secretKey ->
                Text(
                    stringResource(SharedRes.strings.new_key_secret_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Red,
                )
                SelectableKeyText(secretKey)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(SharedRes.strings.new_key_continue_button))
            }
        }
    }
}

@Preview
@Composable
fun NewKeyWarningCardPreview() {
    NewKeyWarningCard(
        npub = "npub1example1234567890abcdefghijklmnopqrstuvwxyz",
        nsec = "nsec1example1234567890abcdefghijklmnopqrstuvwxyz",
        onContinue = {},
    )
}
