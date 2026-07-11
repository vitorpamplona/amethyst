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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Loads [pubkey] from the local cache, get-or-creating (and subscribing) if absent, then hands the
 * [User] (or null while it loads) to [content]. Shared by the auth prompt dialog and the relay-auth
 * settings screen, which both render a person by pubkey while their metadata streams in.
 */
@Composable
internal fun LoadRelayAuthUser(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
    content: @Composable (User?) -> Unit,
) {
    var user by remember(pubkey) { mutableStateOf(accountViewModel.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = accountViewModel.checkGetOrCreateUser(pubkey) }
    }
    content(user)
}

/** The string explaining a single [AuthPurposeKind] ("To send DMs to", "To download posts from", …). */
internal fun relayAuthReasonRes(kind: AuthPurposeKind): Int =
    when (kind) {
        AuthPurposeKind.SEND_DM -> R.string.relay_auth_reason_send_dm
        AuthPurposeKind.NOTIFY_INBOX -> R.string.relay_auth_reason_notify_inbox
        AuthPurposeKind.READ_OUTBOX -> R.string.relay_auth_reason_read_outbox
        AuthPurposeKind.POST_VENUE -> R.string.relay_auth_reason_post_venue
        AuthPurposeKind.READ_VENUE -> R.string.relay_auth_reason_read_venue
        AuthPurposeKind.MY_OWN_RELAY -> R.string.relay_auth_reason_my_own_relay
        AuthPurposeKind.OTHER -> R.string.relay_auth_reason_other
    }
