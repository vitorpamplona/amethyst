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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness

@Composable
fun TranslationServiceUrlSetting(accountViewModel: AccountViewModel) {
    val savedUrl by accountViewModel.account.settings.syncedSettings.languages.translationServiceUrl
        .collectAsStateWithLifecycle()
    val savedApiKey by accountViewModel.account.settings.translationServiceApiKey
        .collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(savedUrl) }
    var apiKeyText by remember { mutableStateOf(savedApiKey) }

    LaunchedEffect(savedUrl) {
        if (savedUrl != text) {
            text = savedUrl
        }
    }

    LaunchedEffect(savedApiKey) {
        if (savedApiKey != apiKeyText) {
            apiKeyText = savedApiKey
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = DividerThickness)
        TranslationPrivacyWarning()
        Spacer(modifier = Modifier.height(12.dp))
        SettingsRow(
            name = R.string.translation_service_url,
            description = R.string.translation_service_url_description,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("https://translate.nostr.wine") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.trim().trimEnd('/') != savedUrl) {
                TextButton(onClick = { accountViewModel.updateTranslationServiceUrl(text) }) {
                    Text(stringRes(R.string.save))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsRow(
            name = R.string.translation_service_api_key,
            description = R.string.translation_service_api_key_description,
        ) {
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (apiKeyText.trim() != savedApiKey) {
                TextButton(onClick = { accountViewModel.updateTranslationServiceApiKey(apiKeyText) }) {
                    Text(stringRes(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun TranslationPrivacyWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = stringRes(R.string.translation_service_privacy_warning_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.translation_service_privacy_warning_description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
