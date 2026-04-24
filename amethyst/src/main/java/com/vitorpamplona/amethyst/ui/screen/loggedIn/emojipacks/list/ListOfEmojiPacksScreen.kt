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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.common.EmojiPackCard
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ListOfEmojiPacksScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListOfEmojiPacksFeed(
        listSource = accountViewModel.account.ownedEmojiPacks.listFeedFlow,
        selectedPacksFlow = accountViewModel.account.emoji.flow,
        openMyEmojiList = { nav.nav(Route.MyEmojiList) },
        addEmojiPack = { nav.nav(Route.EmojiPackMetadataEdit()) },
        openEmojiPack = { pack -> nav.nav(Route.EmojiPackView(pack.identifier)) },
        editEmojiPack = { pack -> nav.nav(Route.EmojiPackMetadataEdit(pack.identifier)) },
        deleteEmojiPack = { pack ->
            accountViewModel.launchSigner {
                accountViewModel.account.deleteOwnedEmojiPack(pack.identifier)
            }
        },
        nav,
    )
}

@Composable
fun ListOfEmojiPacksFeed(
    listSource: StateFlow<List<OwnedEmojiPack>>,
    selectedPacksFlow: StateFlow<List<StateFlow<NoteState>>?>,
    openMyEmojiList: () -> Unit,
    addEmojiPack: () -> Unit,
    openEmojiPack: (OwnedEmojiPack) -> Unit,
    editEmojiPack: (OwnedEmojiPack) -> Unit,
    deleteEmojiPack: (OwnedEmojiPack) -> Unit,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.emoji_packs_title), nav::popBack)
        },
        floatingActionButton = {
            EmojiPackFab(onAddPack = addEmojiPack)
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ).fillMaxHeight(),
        ) {
            ListOfEmojiPacksFeedView(
                listSource = listSource,
                selectedPacksFlow = selectedPacksFlow,
                openMyEmojiList = openMyEmojiList,
                openItem = openEmojiPack,
                editItem = editEmojiPack,
                deleteItem = deleteEmojiPack,
            )
        }
    }
}

@Composable
fun ListOfEmojiPacksFeedView(
    listSource: StateFlow<List<OwnedEmojiPack>>,
    selectedPacksFlow: StateFlow<List<StateFlow<NoteState>>?>,
    openMyEmojiList: () -> Unit,
    openItem: (OwnedEmojiPack) -> Unit,
    editItem: (OwnedEmojiPack) -> Unit,
    deleteItem: (OwnedEmojiPack) -> Unit,
) {
    val feedState by listSource.collectAsStateWithLifecycle()
    val selectedPacks by selectedPacksFlow.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MyEmojiListRow(
            selectedPackCount = selectedPacks?.size ?: 0,
            onClick = openMyEmojiList,
        )
        HorizontalDivider(thickness = DividerThickness)

        if (feedState.isEmpty()) {
            Text(
                text = stringRes(R.string.no_emoji_packs),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = rememberLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    feedState,
                    key = { item: OwnedEmojiPack -> item.identifier },
                ) { pack ->
                    OwnedEmojiPackCard(
                        pack = pack,
                        modifier = Modifier.animateItem(),
                        onClick = { openItem(pack) },
                        onEdit = { editItem(pack) },
                        onDelete = { deleteItem(pack) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnedEmojiPackCard(
    pack: OwnedEmojiPack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val emojiUrls =
        remember(pack) {
            (pack.publicEmojis + pack.privateEmojis).map { it.url }
        }

    Box(modifier = modifier.fillMaxWidth()) {
        // Owned packs omit the cover badge: the top-right corner is reserved for the
        // edit/delete overflow menu, which the user needs more than a reminder of the
        // cover they themselves uploaded.
        EmojiPackCard(
            title = pack.title,
            emojiUrls = emojiUrls,
            coverImage = null,
            onClick = onClick,
        )
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            EmojiPackOptionsButton(
                onEdit = onEdit,
                onDelete = onDelete,
            )
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
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(R.string.edit_emoji_pack)) {
                    onEdit()
                    isMenuOpen.value = false
                }
            }
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Delete, text = stringRes(R.string.quick_action_delete), isDestructive = true) {
                    onDelete()
                    isMenuOpen.value = false
                }
            }
        }
    }
}

@Composable
private fun MyEmojiListRow(
    selectedPackCount: Int,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = stringRes(R.string.my_emoji_list_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringRes(R.string.my_emoji_list_explainer),
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
                Text(text = stringRes(R.string.emoji_pack_count, selectedPackCount))
            }
        },
    )
}

@Composable
fun EmojiPackFab(onAddPack: () -> Unit) {
    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(R.string.new_emoji_pack))
        },
        icon = {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = null,
            )
        },
        onClick = onAddPack,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    )
}
