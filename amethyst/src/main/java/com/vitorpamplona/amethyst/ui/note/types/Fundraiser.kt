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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.CopyIcon
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

@Composable
fun RenderFundraiser(
    baseNote: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? FundraiserEvent ?: return

    val title = remember(noteEvent) { noteEvent.title()?.ifBlank { null } }
    val body = remember(noteEvent) { noteEvent.content.ifBlank { null } }
    val coverImage = remember(noteEvent) { noteEvent.coverImage()?.ifBlank { null } }
    val goalAmountSats = remember(noteEvent) { noteEvent.goal() ?: 0L }
    val deadline = remember(noteEvent) { noteEvent.deadline() }
    val wallets = remember(noteEvent) { noteEvent.wallets() }
    val topics = remember(noteEvent) { noteEvent.topics() }

    Column(MaterialTheme.colorScheme.replyModifier) {
        coverImage?.let {
            Box {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(R.string.preview_card_image_for, it),
                    contentScale = ContentScale.FillWidth,
                    mainImageModifier = Modifier.fillMaxWidth(),
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageHeader(baseNote, accountViewModel) },
                )
            }
        }

        Column(Modifier.padding(10.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }

            body?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (makeItShort) 5 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
            }

            if (goalAmountSats > 0) {
                GoalProgressBar(
                    note = baseNote,
                    goalAmountSats = goalAmountSats,
                    accountViewModel = accountViewModel,
                )
            }

            deadline?.let {
                if (it > TimeUtils.now()) {
                    val context = LocalContext.current
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringRes(R.string.fundraiser_ends, timeAheadNoDot(it, context)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }

            if (wallets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OnChainDonation(wallets, accountViewModel)
            }

            if (topics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                DisplayUncitedHashtags(
                    event = noteEvent,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun OnChainDonation(
    wallets: List<String>,
    accountViewModel: AccountViewModel,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Text(
        text = stringRes(R.string.fundraiser_onchain_donation),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.placeholderText,
    )
    Spacer(Modifier.height(4.dp))

    wallets.forEach { address ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            clipboard.setText(address)
                            accountViewModel.toastManager.toast(
                                R.string.copy_to_clipboard,
                                R.string.copied_to_clipboard,
                            )
                        }
                    },
        ) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CopyIcon(modifier = Size18Modifier)
        }
    }
}
