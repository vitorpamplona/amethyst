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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness

/**
 * "Somebody added you to a channel" prompts, rendered as a block inside Messages › New Requests.
 *
 * On the Notifications tab these are not a section at all: each pending invite is a
 * [ChannelInviteCard] in the ordinary card feed. Here Messages has its own list, which is not a `Card`
 * feed, so the same rows are drawn directly.
 *
 * Either way the row itself is a plain [NoteCompose] over the relay's kind-44100 — same author header,
 * 3-dot menu, reactions row and click-through as every other note — with only the body supplied per
 * kind by `RenderNoteRow` → `RenderChannelInvite`. This used to be a hand-assembled `NoteComposeLayout`
 * that reimplemented a fraction of that and therefore had none of the rest.
 *
 * These are deliberately NOT auto-accepted. On a Buzz relay another member can add you to a channel
 * server-side: the relay writes you into the kind-39002 roster and you can immediately read and post,
 * without you ever agreeing to see it. Amethyst used to silently subscribe to those channels' messages
 * while showing no row for them anywhere, so a channel could be joined, streaming, and invisible at once.
 * Now the relay's decision is surfaced as a question instead of being acted on.
 */
@Composable
fun ChannelInvitesSection(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val invites by accountViewModel.account.channelInvites.flow
        .collectAsStateWithLifecycle()

    if (invites.isEmpty()) return

    Column(modifier) {
        invites.forEach { invite ->
            // Keyed by the kind-44100 it came from: the list is sorted newest-first, so an arriving
            // invite shifts every row below it. Without a key Compose matches children by position and
            // each shifted row would recompose against a different note.
            key(invite.eventId) {
                val note = remember(invite.eventId) { LocalCache.getNoteIfExists(invite.eventId) }
                if (note != null) {
                    NoteCompose(
                        baseNote = note,
                        modifier = Modifier.fillMaxWidth(),
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        }
    }
}
