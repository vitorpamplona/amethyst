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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_not_kept
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_password
import com.vitorpamplona.amethyst.commons.resources.key_storage_login_notice_unreachable
import com.vitorpamplona.amethyst.commons.resources.key_storage_remember_key
import com.vitorpamplona.amethyst.commons.ui.stringRes

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
            is KeyStorageStatus.PasswordFile -> if (current.hasPassword) null else Res.string.key_storage_login_notice_password
            else -> null
        } ?: return
    Text(
        text = stringRes(notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
