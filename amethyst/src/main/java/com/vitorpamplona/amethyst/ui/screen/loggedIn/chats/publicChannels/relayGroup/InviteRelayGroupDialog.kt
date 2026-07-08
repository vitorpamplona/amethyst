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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * Mint a NIP-29 invite (kind 9009) for the group as soon as the dialog opens and
 * show the code to copy and share. Requires admin/moderator rights on the relay.
 */
@Composable
fun InviteRelayGroupDialog(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val code by remember { mutableStateOf(RandomInstance.bytes(6).toHexKey()) }

    // A shareable, cross-client coordinate for the group (opens the chat in any
    // NIP-29 client). Null until the relay-signed metadata has loaded.
    val nAddr = channel.toNAddr()?.let { "nostr:$it" }
    val isClosed = channel.isClosed()

    // A join code is only meaningful for closed (invite-only) groups; open groups
    // join directly from the shared naddr. So mint the kind-9009 invite only when
    // the group is actually closed, rather than on every dialog open.
    LaunchedEffect(code, isClosed) {
        if (isClosed) accountViewModel.createRelayGroupInvite(channel, code)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.relay_group_invite_title)) },
        text = {
            Column {
                Text(stringRes(R.string.relay_group_invite_description))

                nAddr?.let {
                    Text(
                        text = it,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                }

                // Closed groups additionally need a one-time code to join.
                if (isClosed) {
                    Text(stringRes(R.string.relay_group_invite_code_label))
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            // Copy the group link, plus the code when the group is closed (so a
            // recipient has both to join). Never fall back to copying a code the
            // dialog didn't show — for an open group with metadata not yet loaded
            // there is simply nothing to copy, so disable the button.
            val toCopy = listOfNotNull(nAddr, if (isClosed) code else null).joinToString("\n")
            TextButton(
                enabled = toCopy.isNotBlank(),
                onClick = {
                    clipboard.setText(AnnotatedString(toCopy))
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}
