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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.lang.TranslationServerConfig
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun TranslationServerSettingsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TranslationServerConfig.isEnabled(context)) }
    var serverUrl by remember { mutableStateOf(TranslationServerConfig.serverUrl(context)) }
    var apiKey by remember { mutableStateOf(TranslationServerConfig.apiKey(context)) }

    SettingsBlockTile(
        icon = MaterialSymbols.Translate,
        title = stringRes(R.string.translations_server_title),
        description = stringRes(R.string.translations_server_description),
    ) {
        SettingsRow(
            name = R.string.translations_server_enable,
            description = R.string.translations_server_enable_description,
        ) {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    TranslationServerConfig.setEnabled(context, it)
                },
            )
        }
        SettingsRow(
            name = R.string.translations_server_url,
            description = R.string.translations_server_url_description,
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    TranslationServerConfig.setServerUrl(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsRow(
            name = R.string.translations_server_api_key,
            description = R.string.translations_server_api_key_description,
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    TranslationServerConfig.setApiKey(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
