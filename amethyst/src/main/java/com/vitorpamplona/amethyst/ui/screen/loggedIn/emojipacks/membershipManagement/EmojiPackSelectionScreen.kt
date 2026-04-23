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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.membershipManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteAndMap
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent

@Composable
fun EmojiPackSelectionScreen(
    packAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address = packAddress, accountViewModel = accountViewModel) { note ->
        note?.let {
            EmojiPackSelectionView(
                modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun EmojiPackSelectionView(
    modifier: Modifier = Modifier,
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.emoji_pack_management_title), nav::popBack)
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .imePadding(),
        ) {
            EmojiPackSelectionBody(note, accountViewModel)
        }
    }
}

@Composable
private fun EmojiPackSelectionBody(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            LoadAddressableNote(
                address = accountViewModel.account.emoji.getEmojiPackSelectionAddress(),
                accountViewModel = accountViewModel,
            ) { selectionNote ->
                selectionNote?.let {
                    val hasAddedThis by observeNoteAndMap(it, accountViewModel) { currentNote ->
                        currentNote.event?.isTaggedAddressableNote(note.idHex) == true
                    }

                    EmojiPackSelectionItem(
                        isIncluded = hasAddedThis,
                        packTitle = (note.event as? EmojiPackEvent)?.titleOrName() ?: note.dTag(),
                        onAdd = {
                            accountViewModel.addEmojiPack(note)
                        },
                        onRemove = {
                            accountViewModel.removeEmojiPack(note)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPackSelectionItem(
    isIncluded: Boolean,
    packTitle: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = { if (isIncluded) onRemove() else onAdd() }),
        headlineContent = {
            Text(
                text = stringRes(R.string.my_emoji_list_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text =
                    if (isIncluded) {
                        stringRes(R.string.emoji_pack_is_in_list, packTitle)
                    } else {
                        stringRes(R.string.emoji_pack_is_not_in_list, packTitle)
                    },
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.EmojiEmotions,
                    contentDescription = null,
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { if (isIncluded) onRemove() else onAdd() },
                    modifier =
                        Modifier
                            .background(
                                color =
                                    if (isIncluded) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                shape = RoundedCornerShape(percent = 80),
                            ),
                ) {
                    if (isIncluded) {
                        Icon(
                            symbol = MaterialSymbols.BookmarkRemove,
                            contentDescription = stringRes(R.string.remove_from_emoji_list),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        Icon(
                            symbol = MaterialSymbols.BookmarkAdd,
                            contentDescription = stringRes(R.string.add_to_emoji_list),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        },
    )
}
