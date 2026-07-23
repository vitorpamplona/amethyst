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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.service.buzz.BuzzInviteMinter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A "Create invite link" text button for a Buzz workspace owner/admin: mints a link via the relay's
 * `/api/invites` endpoint ([BuzzInviteMinter]) and shows it with Copy / Share. Any member sees the
 * button, but the relay only serves owners/admins — a rejection surfaces as the error dialog.
 */
@Composable
fun BuzzInviteMintButton(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
) {
    var minting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BuzzInviteMinter.MintedInvite?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    TextButton(
        onClick = {
            if (minting) return@TextButton
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
    ) {
        if (minting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Icon(symbol = MaterialSymbols.Link, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(stringRes(R.string.buzz_invite_create))
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
                    ContextCompat.startActivity(context, Intent.createChooser(send, null), null)
                    result = null
                }) { Text(stringRes(R.string.buzz_invite_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(minted.url))
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
