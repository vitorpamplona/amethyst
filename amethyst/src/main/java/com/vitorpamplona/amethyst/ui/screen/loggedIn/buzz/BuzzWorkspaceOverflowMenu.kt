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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.buzz.BuzzInviteMinter
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The Buzz workspace top-bar overflow (3-dot) menu. Holds the two workspace-owner actions that used
 * to sit inline above the channel list: "Add people to this workspace" (kind-9030, delegated to the
 * screen's [onAddPeople] dialog) and "Create invite link" (mints via the relay's `/api/invites`
 * endpoint — see [BuzzInviteMinter]). Any member sees both, but the relay only serves owners/admins,
 * so a rejection surfaces as the error dialog.
 */
@Composable
fun BuzzWorkspaceOverflowMenu(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    onAddPeople: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var minting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BuzzInviteMinter.MintedInvite?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    IconButton(onClick = { menuOpen = true }) {
        Icon(
            symbol = MaterialSymbols.MoreVert,
            contentDescription = stringRes(R.string.more_options),
            modifier = Modifier.size(24.dp),
        )
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(symbol = MaterialSymbols.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            text = { Text(stringRes(R.string.buzz_community_add_people)) },
            onClick = {
                menuOpen = false
                onAddPeople()
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                if (minting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(symbol = MaterialSymbols.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            },
            text = { Text(stringRes(R.string.buzz_invite_create)) },
            enabled = !minting,
            onClick = {
                menuOpen = false
                if (minting) return@DropdownMenuItem
                minting = true
                error = null
                scope.launch {
                    try {
                        result =
                            BuzzInviteMinter.mint(
                                relay = relay,
                                ttlSecs = null,
                                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForPushRegistration,
                                httpAuth = accountViewModel.account::createHTTPAuthorization,
                            )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = e.message ?: e::class.simpleName
                    } finally {
                        minting = false
                    }
                }
            },
        )
    }

    result?.let { minted ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(stringRes(R.string.buzz_invite_link_title)) },
            text = {
                SelectionContainer {
                    Text(minted.url, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, minted.url)
                        }
                    context.startActivity(Intent.createChooser(send, null))
                    result = null
                }) { Text(stringRes(R.string.buzz_invite_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { clipboard.setText(minted.url) }
                    result = null
                }) { Text(stringRes(R.string.buzz_invite_copy)) }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringRes(R.string.buzz_invite_error_title)) },
            text = { Text(message, color = MaterialTheme.colorScheme.error) },
            confirmButton = {
                TextButton(onClick = { error = null }) { Text(stringRes(R.string.buzz_invite_dismiss)) }
            },
        )
    }
}
