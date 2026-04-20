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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun EmojiPackItem(
    modifier: Modifier = Modifier,
    pack: OwnedEmojiPack,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(pack.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(
                            modifier = NoSoTinyBorders,
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End,
                        ) {
                            EmojiPackOptionsButton(
                                onEdit = onEdit,
                                onDelete = onDelete,
                            )
                        }
                    }
                },
                supportingContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        pack.description?.let {
                            Text(
                                it,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 2,
                            )
                        }
                        Spacer(StdVertSpacer)
                        EmojiPackPreviewThumbnails(pack)
                    }
                },
                leadingContent = {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!pack.image.isNullOrBlank()) {
                            AsyncImage(
                                model = pack.image,
                                contentDescription = pack.title,
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
                        Text(
                            text = stringRes(R.string.emoji_pack_count, pack.totalEmojis),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun EmojiPackPreviewThumbnails(pack: OwnedEmojiPack) {
    val first = remember(pack) { (pack.publicEmojis + pack.privateEmojis).take(6) }
    if (first.isEmpty()) return
    Row(
        horizontalArrangement = SpacedBy2dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        first.forEach { emoji ->
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

@Composable
private fun EmojiPackOptionsButton(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()
    }

    if (isMenuOpen.value) {
        M3ActionDialog(
            title = stringRes(R.string.emoji_pack_actions_dialog_title),
            onDismiss = { isMenuOpen.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.edit_emoji_pack)) {
                    onEdit()
                    isMenuOpen.value = false
                }
            }
            M3ActionSection {
                M3ActionRow(icon = Icons.Outlined.Delete, text = stringRes(R.string.quick_action_delete), isDestructive = true) {
                    onDelete()
                    isMenuOpen.value = false
                }
            }
        }
    }
}
