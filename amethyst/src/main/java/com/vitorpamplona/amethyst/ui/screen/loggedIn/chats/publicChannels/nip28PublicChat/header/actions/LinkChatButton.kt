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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.actions

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding

@Composable
fun LinkChatButton(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, channel.toNostrUri().toUri())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            context.startActivity(intent)

            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, channel.toNostrUri())
                    putExtra(Intent.EXTRA_TITLE, stringRes(context, R.string.quick_action_share_browser_link))
                }

            val shareIntent =
                Intent.createChooser(
                    sendIntent,
                    stringRes(context, R.string.quick_action_copy_note_id),
                )

            context.startActivity(shareIntent)
        },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = stringRes(R.string.quick_action_copy_note_id),
            modifier = Size20Modifier,
        )
    }
}
