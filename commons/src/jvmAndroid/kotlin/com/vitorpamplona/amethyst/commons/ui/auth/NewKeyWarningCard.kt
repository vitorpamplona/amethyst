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
package com.vitorpamplona.amethyst.commons.ui.auth

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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "IMPORTANT: Save your keys!",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Red,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Your secret key (nsec) is the ONLY way to access your account. " +
                    "If you lose it, your account is gone forever. Save it somewhere safe!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Public Key (shareable):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectableKeyText(npub)

            Spacer(Modifier.height(12.dp))

            nsec?.let { secretKey ->
                Text(
                    "Secret Key (NEVER share this!):",
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
                Text("I've saved my keys, continue")
            }
        }
    }
}
