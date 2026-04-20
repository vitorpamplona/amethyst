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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis

// Screen that lets the logged-in user inspect the contents of their kind 10030 selection
// (a.k.a. "My Emoji List") and remove individual packs from it. Tapping a pack navigates to a
// viewer: the owner's pack-editor screen for self-authored packs, otherwise the generic note
// thread view (which already knows how to render a kind 30030 `EmojiPackEvent`).
//
// IMPORTANT: Removing a pack from the 10030 selection is observable end-to-end:
//   * `account.emoji.getEmojiPackSelectionFlow()` is shared by the dropdown bookmark toggle on
//     a 30030 note, and by the reaction menu custom-emoji picker
//     (`UpdateReactionTypeDialog.EmojiSelector` reads `EmojiPackSelectionEvent.emojiPacks()`).
//   * `EmojiPackState.myEmojis` is a derived flow that maps the selection -> per-pack note
//     flows -> merged, URL-deduped emoji list. This is what the `:` autocomplete
//     (`EmojiSuggestionState`) and the post-composer taggers consume. Removing a pack here
//     will immediately remove its emojis from that merged list.
// Reordering is NOT implemented: NIP-51 doesn't mandate an order, but both `myEmojis` and the
// reaction menu render packs in tag order. Adding drag-to-reorder would require a new
// `EmojiPackSelectionEvent.reorder(...)` builder in quartz (no such API exists) and a bespoke
// drag surface that doesn't conflict with tap-to-view / tap-to-delete. Skipped for this pass.
@Composable
fun MyEmojiListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(
        address = accountViewModel.account.emoji.getEmojiPackSelectionAddress(),
        accountViewModel = accountViewModel,
    ) { selectionNote ->
        selectionNote?.let {
            MyEmojiListView(
                selectionNote = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun MyEmojiListView(
    selectionNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.my_emoji_list_title), nav::popBack)
        },
    ) { contentPadding ->
        val packAddresses by observeNoteEventAndMap<EmojiPackSelectionEvent, List<Address>>(
            selectionNote,
            accountViewModel,
        ) { event ->
            event?.emojiPacks() ?: emptyList()
        }

        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .fillMaxHeight(),
        ) {
            MyEmojiListFeed(
                packAddresses = packAddresses,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun MyEmojiListFeed(
    packAddresses: List<Address>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = FeedPadding,
    ) {
        if (packAddresses.isEmpty()) {
            item {
                Text(
                    text = stringRes(R.string.my_emoji_list_empty),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            itemsIndexed(
                packAddresses,
                key = { _, address -> address.toValue() },
            ) { _, address ->
                LoadAddressableNote(
                    address = address,
                    accountViewModel = accountViewModel,
                ) { packNote ->
                    packNote?.let {
                        SelectedEmojiPackRow(
                            packNote = it,
                            accountViewModel = accountViewModel,
                            onOpen = {
                                val route =
                                    if (accountViewModel.isLoggedUser(it.author)) {
                                        Route.EmojiPackView(it.dTag())
                                    } else {
                                        Route.Note(it.idHex)
                                    }
                                nav.nav(route)
                            },
                            onRemove = {
                                // Publishes a replacement kind 10030 without this pack's `a` tag.
                                // Downstream consumers (reaction menu + `:` autocomplete) refresh
                                // automatically because they're all subscribed to the same flow.
                                accountViewModel.removeEmojiPack(it)
                            },
                        )
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedEmojiPackRow(
    packNote: AddressableNote,
    accountViewModel: AccountViewModel,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val packEvent by observeNoteEvent<EmojiPackEvent>(packNote, accountViewModel)

    val title = packEvent?.titleOrName()?.takeIf { it.isNotBlank() } ?: packNote.dTag()
    val image = packEvent?.image()
    val description = packEvent?.description()
    val emojiCount = packEvent?.taggedEmojis()?.size ?: 0
    val previewEmojis = remember(packEvent) { packEvent?.taggedEmojis()?.take(6).orEmpty() }

    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                packNote.author?.let { author ->
                    val authorName by observeUserName(author, accountViewModel)
                    Text(
                        text = stringRes(R.string.my_emoji_list_by_author, authorName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(StdVertSpacer)
                if (previewEmojis.isNotEmpty()) {
                    Row(
                        horizontalArrangement = SpacedBy2dp,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        previewEmojis.forEach { emoji ->
                            Box(
                                modifier = Size40Modifier,
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = emoji.url,
                                    contentDescription = emoji.code,
                                    modifier = Size40Modifier,
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!image.isNullOrBlank()) {
                    AsyncImage(
                        model = image,
                        contentDescription = title,
                        modifier = Size40Modifier,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = null,
                        modifier = Size40Modifier,
                    )
                }
                Spacer(StdVertSpacer)
                Text(text = stringRes(R.string.emoji_pack_count, emojiCount))
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringRes(R.string.remove_from_emoji_list),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
