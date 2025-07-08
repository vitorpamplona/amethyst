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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.gallery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialogOneButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun QuickActionGallery(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (() -> Unit) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    content { popupExpanded.value = true }

    if (popupExpanded.value) {
        if (baseNote.author == accountViewModel.account.userProfile()) {
            DeleteFromGalleryDialog(
                note = baseNote,
                onDismiss = { popupExpanded.value = false },
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
fun DeleteFromGalleryDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = stringRes(R.string.quick_action_request_deletion_gallery_title),
        textContent = stringRes(R.string.quick_action_request_deletion_gallery_alert_body_v2),
        buttonIcon = Icons.Default.Delete,
        buttonText = stringRes(R.string.quick_action_delete_dialog_btn),
        onClickDoOnce = {
            accountViewModel.removeFromMediaGallery(note)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
