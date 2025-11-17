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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import kotlinx.coroutines.launch

@Composable
fun BookmarkGroupScreen(
    bookmarkIdentifier: String,
    bookmarkType: BookmarkType,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val bookmarkGroupViewModel: BookmarkGroupViewModel =
        viewModel(
            factory = BookmarkGroupViewModel.Initializer(accountViewModel.account, bookmarkIdentifier),
        )

    BookmarkGroupScreenView(
        bookmarkGroupViewModel,
        bookmarkType,
        broadcastBookmarkGroup = {
            accountViewModel.launchSigner {
                val groupNote = accountViewModel.account.labeledBookmarkLists.getLabeledBookmarkListNote(bookmarkIdentifier)
                groupNote?.let {
                    accountViewModel.broadcast(it)
                }
            }
        },
        deleteBookmarkGroup = {
            accountViewModel.launchSigner {
                bookmarkGroupViewModel.deleteBookmarkGroup()
            }
        },
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkGroupScreenView(
    bookmarkGroupViewModel: BookmarkGroupViewModel,
    bookmarkType: BookmarkType,
    broadcastBookmarkGroup: () -> Unit,
    deleteBookmarkGroup: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }
    val privateItemTypeLabel =
        when (bookmarkType) {
            BookmarkType.ArticleBookmark -> "Private Articles"
            BookmarkType.HashtagBookmark -> "Private Hashtags"
            BookmarkType.LinkBookmark -> "Private Links"
            BookmarkType.PostBookmark -> "Private Posts"
        }

    val publicItemTypeLabel =
        when (bookmarkType) {
            BookmarkType.ArticleBookmark -> "Public Articles"
            BookmarkType.HashtagBookmark -> "Public Hashtags"
            BookmarkType.LinkBookmark -> "Public Links"
            BookmarkType.PostBookmark -> "Public Posts"
        }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TitleAndDescription(bookmarkGroupViewModel)
                    },
                    navigationIcon = {
                        IconButton(nav::popBack) {
                            ArrowBackIcon()
                        }
                    },
                    actions = {
                        BookmarkGroupActionsMenuButton(
                            onBroadcastList = broadcastBookmarkGroup,
                            onDeleteList = deleteBookmarkGroup,
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                TabRow(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                ) {
                    val scope = rememberCoroutineScope()
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(text = publicItemTypeLabel) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(text = privateItemTypeLabel) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePadding(),
        ) {
            when (bookmarkType) {
                BookmarkType.PostBookmark ->
                    RenderPostList(
                        bookmarkGroupViewModel,
                        pagerState,
                        accountViewModel,
                        nav,
                    )
                BookmarkType.ArticleBookmark ->
                    RenderArticleList(
                        bookmarkGroupViewModel,
                        pagerState,
                        accountViewModel,
                        nav,
                    )
                BookmarkType.HashtagBookmark -> RenderHashtagList(bookmarkGroupViewModel, pagerState)
                BookmarkType.LinkBookmark -> RenderLinksList(bookmarkGroupViewModel, pagerState)
            }
        }
    }
}

@Composable
private fun TitleAndDescription(viewModel: BookmarkGroupViewModel) {
    val selectedSetState = viewModel.selectedBookmarkGroupFlow.collectAsStateWithLifecycle()
    selectedSetState.value?.let { bookmarkGroup ->
        ListItem(
            headlineContent = {
                Text(
                    text = bookmarkGroup.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                bookmarkGroup.description?.let { description ->
                    Text(
                        text = description,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
    }
}

@Composable
fun BookmarkGroupActionsMenuButton(
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    val isActionListOpen = remember { mutableStateOf(false) }

    ClickableBox(
        modifier =
            StdPadding
                .size(30.dp)
                .border(
                    width = Dp.Hairline,
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ).background(
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ),
        onClick = { isActionListOpen.value = true },
    ) {
        VerticalDotsIcon()
        BookmarkGroupActionsMenu(
            onCloseMenu = { isActionListOpen.value = false },
            isOpen = isActionListOpen.value,
            onBroadcastList = onBroadcastList,
            onDeleteList = onDeleteList,
        )
    }
}

@Composable
fun BookmarkGroupActionsMenu(
    onCloseMenu: () -> Unit,
    isOpen: Boolean,
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    DropdownMenu(
        expanded = isOpen,
        onDismissRequest = onCloseMenu,
    ) {
        DropdownMenuItem(
            text = {
                Text("Broadcast Bookmark Group")
            },
            onClick = {
                onBroadcastList()
                onCloseMenu()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        DropdownMenuItem(
            text = {
                Text("Delete Bookmark Group")
            },
            onClick = {
                onDeleteList()
                onCloseMenu()
            },
        )
    }
}
