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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_status_detail
import com.vitorpamplona.amethyst.commons.resources.key_storage_title
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.resources.key_storage_where
import com.vitorpamplona.amethyst.commons.ui.keystorage.LocalKeyStorage
import com.vitorpamplona.amethyst.commons.ui.keystorage.keyStorageDescription
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * Settings › Key storage: where this device keeps private keys (the OS keyring, a password file,
 * or nowhere), whether that works now, and the action that fixes it: try the keyring again, or
 * unlock the password file. Only listed where the platform provides a [KeyStorageController].
 */
@Composable
fun KeyStorageSettingsScreen(nav: INav) {
    val controller = LocalKeyStorage.current
    Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.key_storage_title), nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (controller != null) KeyStorageSettingsBody(controller)
        }
    }
}

@Composable
private fun KeyStorageSettingsBody(controller: KeyStorageController) {
    val status by controller.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SettingsSection(Res.string.key_storage_where) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(keyStorageDescription(status))
            (status as? KeyStorageStatus.KeyringUnreachable)?.let {
                Text(
                    stringRes(Res.string.key_storage_status_detail, it.detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val current = status) {
                    is KeyStorageStatus.KeyringUnreachable ->
                        OutlinedButton(onClick = { scope.launch { controller.retry() } }) {
                            Text(stringRes(Res.string.key_storage_try_again))
                        }
                    is KeyStorageStatus.PasswordFile ->
                        if (current.hasPassword && !current.unlocked) {
                            OutlinedButton(onClick = { scope.launch { controller.unlock() } }) {
                                Text(stringRes(Res.string.key_storage_unlock))
                            }
                        }
                    else -> Unit
                }
            }
        }
    }
}
