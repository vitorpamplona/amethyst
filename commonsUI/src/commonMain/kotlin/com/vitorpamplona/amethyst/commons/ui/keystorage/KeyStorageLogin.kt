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
package com.vitorpamplona.amethyst.commons.ui.keystorage

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_locked
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_not_kept
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_unreachable
import com.vitorpamplona.amethyst.commons.resources.key_storage_remember_key
import com.vitorpamplona.amethyst.commons.resources.key_storage_try_again
import com.vitorpamplona.amethyst.commons.resources.key_storage_unlock
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

/**
 * The login screen's part: *Remember my key on this device*, and a line when a key typed now
 * would not be kept as the user expects. Renders nothing where the platform has no key storage
 * the user deals with.
 */
@Composable
fun KeyStorageLoginOptions(
    rememberKey: Boolean,
    onRememberKeyChange: (Boolean) -> Unit,
) {
    val controller = LocalKeyStorage.current ?: return
    RememberKeyCheckbox(rememberKey, onRememberKeyChange)
    if (rememberKey) KeyStorageLoginNotice(controller)
    KeyStorageLoginFix(controller)
}

/**
 * Try again (the keyring refused) or Unlock (the key file is locked), here as in Settings › Key
 * storage, which a logged-off user cannot reach: either one logs the saved account in once the
 * keys open. Shown whatever the checkbox says, since it is about the saved account, not the key
 * being typed.
 */
@Composable
private fun KeyStorageLoginFix(controller: KeyStorageController) {
    val status by controller.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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

@Composable
private fun RememberKeyCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringRes(Res.string.key_storage_remember_key))
    }
}

@Composable
private fun KeyStorageLoginNotice(controller: KeyStorageController) {
    val status by controller.status.collectAsStateWithLifecycle()
    val notice =
        when (val current = status) {
            is KeyStorageStatus.KeyringUnreachable -> Res.string.key_storage_login_notice_unreachable
            KeyStorageStatus.NotKept -> Res.string.key_storage_login_notice_not_kept
            is KeyStorageStatus.PasswordFile ->
                when {
                    !current.hasPassword -> Res.string.key_storage_login_notice_password
                    !current.unlocked -> Res.string.key_storage_login_notice_locked
                    else -> null
                }
            else -> null
        } ?: return
    Text(
        text = stringRes(notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
