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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextColor
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteLabels
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash

/**
 * NIP-32: shows the hashtags that people the user follows (or the user themselves) attached
 * to this post via kind 1985 label events, as "🏷 #tag added by <user>" rows. Labels from
 * non-follows are ignored, and hashtags the author already used in the post are skipped
 * (they render with the post itself). Renders nothing when there is nothing to show.
 */
@Composable
fun DisplayFollowLabelsInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val labelState by observeNoteLabels(baseNote, accountViewModel)

    val labels = labelState?.note?.labels ?: return
    if (labels.isEmpty()) return

    val follows = accountViewModel.account.followingKeySet()
    val loggedUser = accountViewModel.account.userProfile().pubkeyHex

    val visibleLabels =
        labels.mapNotNull { (tag, labelNotes) ->
            // The author already used the hashtag — it renders with the post itself.
            if (baseNote.event?.isTaggedHash(tag) == true) return@mapNotNull null

            val labeler =
                labelNotes
                    .firstOrNull {
                        val pubkey = it.author?.pubkeyHex
                        pubkey != null && (pubkey == loggedUser || pubkey in follows)
                    }?.author ?: return@mapNotNull null

            tag to labeler
        }

    if (visibleLabels.isEmpty()) return

    Column(modifier) {
        Spacer(HalfVertSpacer)
        visibleLabels.forEach { (tag, labeler) ->
            FollowLabelRow(tag, labeler, accountViewModel, nav)
        }
    }
}

@Composable
private fun FollowLabelRow(
    tag: String,
    labeler: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = MaterialSymbols.Tag,
            contentDescription = null,
            modifier = Size16Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(StdHorzSpacer)
        ClickableTextColor(
            "#$tag",
            linkColor = MaterialTheme.colorScheme.lessImportantLink,
            maxLines = 1,
        ) {
            nav.nav(Route.Hashtag(tag))
        }
        Spacer(StdHorzSpacer)
        Text(
            stringRes(R.string.hashtag_label_added_by),
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
        )
        Spacer(StdHorzSpacer)
        UsernameDisplay(
            labeler,
            fontWeight = FontWeight.Bold,
            textColor = MaterialTheme.colorScheme.placeholderText,
            accountViewModel = accountViewModel,
        )
    }
}
