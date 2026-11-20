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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_send
import com.vitorpamplona.amethyst.commons.resources.encrypt_files_label
import com.vitorpamplona.amethyst.commons.resources.media_studio_encrypt_description
import com.vitorpamplona.amethyst.commons.resources.send
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.MediaStudioDialog
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.SwitchRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * The files a chat is about to send, in the media studio: each with its own quality, alt text
 * (which the message carries as the file's description), content warning and metadata strip.
 * Send uploads them and sends the message ([upload]); a file that fails stays in the studio with
 * its reason, and Send tries again. Every chat goes through here: DMs, public channels, Concord,
 * Marmot groups, minichat and nests.
 *
 * [isNip17] adds the file-encryption switch to Privacy; [showContentWarning] is false where the
 * blob host has nowhere to put a warning (cordn, see CordnBlobUpload). A [ChatFileUploadState.lockedServer]
 * (a Buzz workspace's) is the only server offered.
 */
@Composable
fun ChatFileUploadDialog(
    state: ChatFileUploadState,
    @Suppress("UNUSED_PARAMETER") title: @Composable () -> Unit,
    upload: () -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
    @Suppress("UNUSED_PARAMETER") nav: INav,
    isNip17: Boolean = false,
    showContentWarning: Boolean = true,
    confirmLabel: StringResource = Res.string.send,
) {
    val media = state.multiOrchestrator ?: return
    MediaStudioDialog(
        media = media,
        defaultServer = state.selectedServer,
        isUploading = state.mediaUploadTracker.isUploading,
        onAdd = { server ->
            if (state.lockedServer == null) state.selectedServer = server
            upload()
        },
        onRemove = state::deleteMediaToUpload,
        onCancel = {
            state.reset()
            onCancel()
        },
        accountViewModel = accountViewModel,
        confirmLabel = confirmLabel,
        showSensitive = showContentWarning,
        lockedServer = state.lockedServer,
        privacyExtras =
            if (isNip17) {
                {
                    SwitchRow(stringRes(Res.string.encrypt_files_label), stringRes(Res.string.media_studio_encrypt_description), state.encryptFiles, MaterialSymbols.Lock) { state.encryptFiles = it }
                }
            } else {
                null
            },
    )
}

@Composable
fun SendButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        enabled = isActive,
        onClick = onPost,
    ) {
        Text(text = stringRes(Res.string.accessibility_send))
    }
}
