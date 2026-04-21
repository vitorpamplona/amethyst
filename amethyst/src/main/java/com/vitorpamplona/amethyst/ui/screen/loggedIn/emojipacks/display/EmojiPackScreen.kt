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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

@Composable
fun EmojiPackScreen(
    packIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: EmojiPackViewModel =
        viewModel(
            factory = EmojiPackViewModel.Initializer(accountViewModel.account, packIdentifier),
        )
    EmojiPackScreenView(viewModel, accountViewModel, nav)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPackScreenView(
    viewModel: EmojiPackViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pack by viewModel.selectedPackFlow.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EmojiDeleteTarget?>(null) }

    Scaffold(
        topBar = {
            ShorterTopAppBar(
                title = {
                    pack?.let {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = it.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                it.description?.let { description ->
                                    Text(
                                        text = description,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(nav::popBack) {
                        ArrowBackIcon()
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(text = stringRes(R.string.add_emoji_fab)) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                    )
                },
                onClick = { showAddDialog = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding),
        ) {
            pack?.let { currentPack ->
                if (currentPack.publicEmojis.isNotEmpty() || currentPack.privateEmojis.isNotEmpty()) {
                    Text(
                        text = stringRes(R.string.emoji_long_press_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                EmojiGrid(
                    pack = currentPack,
                    onLongPress = { emoji, isPrivate -> pendingDelete = EmojiDeleteTarget(emoji, isPrivate) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddEmojiDialog(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { tag, isPrivate ->
                accountViewModel.launchSigner {
                    viewModel.addEmoji(tag, isPrivate)
                }
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { target ->
        M3ActionDialog(
            title = stringRes(R.string.emoji_remove_dialog_title, target.emoji.code),
            onDismiss = { pendingDelete = null },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = Icons.Outlined.Delete,
                    text = stringRes(R.string.quick_action_delete),
                    isDestructive = true,
                ) {
                    accountViewModel.launchSigner {
                        // removeEmoji must be called with the matching isPrivate flag
                        // so we remove from the encrypted `.content` rather than the
                        // public tag array (or vice-versa).
                        viewModel.removeEmoji(target.emoji.code, target.isPrivate)
                    }
                    pendingDelete = null
                }
            }
        }
    }
}

private data class EmojiDeleteTarget(
    val emoji: EmojiUrlTag,
    val isPrivate: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiGrid(
    pack: OwnedEmojiPack,
    onLongPress: (EmojiUrlTag, Boolean) -> Unit,
) {
    val allEmojis =
        remember(pack) {
            pack.publicEmojis.map { it to false } + pack.privateEmojis.map { it to true }
        }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 56.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(allEmojis, key = { (emoji, isPrivate) -> "${emoji.code}-${if (isPrivate) "priv" else "pub"}" }) { (emoji, isPrivate) ->
            EmojiCell(
                emoji = emoji,
                isPrivate = isPrivate,
                onLongClick = { onLongPress(emoji, isPrivate) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    emoji: EmojiUrlTag,
    isPrivate: Boolean,
    onLongClick: () -> Unit,
) {
    val privateLabel = stringRes(R.string.emoji_private_badge)
    Box(
        modifier =
            Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = emoji.url,
            contentDescription = if (isPrivate) "${emoji.code} ($privateLabel)" else emoji.code,
            modifier = Size35Modifier,
            contentScale = ContentScale.Fit,
        )
        if (isPrivate) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = privateLabel,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}
