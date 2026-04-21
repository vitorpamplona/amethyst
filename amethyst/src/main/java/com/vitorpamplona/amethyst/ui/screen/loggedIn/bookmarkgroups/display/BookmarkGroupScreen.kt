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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.DeletedItemsBanner
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.quartz.nip01Core.core.Address
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
                bookmarkGroupViewModel.deleteBookmarkGroup(bookmarkIdentifier)
            }
            nav.popBack()
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
    Scaffold(
        topBar = {
            Column {
                ShorterTopAppBar(
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
                BookmarkGroupHeaderTabs(
                    bookmarkGroupViewModel,
                    bookmarkType,
                    pagerState,
                )
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
            DeletedBookmarksBanner(
                bookmarkGroupViewModel = bookmarkGroupViewModel,
                bookmarkType = bookmarkType,
                accountViewModel = accountViewModel,
            )
            when (bookmarkType) {
                BookmarkType.PostBookmark -> {
                    RenderPostList(
                        bookmarkGroupViewModel,
                        pagerState,
                        accountViewModel,
                        movePostBookmark = { postId, isPrivate ->
                            accountViewModel.launchSigner {
                                bookmarkGroupViewModel.movePostBookmark(
                                    groupIdentifier = bookmarkGroupViewModel.bookmarkGroupIdentifier,
                                    postId = postId,
                                    isCurrentlyPrivate = isPrivate,
                                )
                            }
                        },
                        deletePostBookmark = { postId, isPrivate ->
                            accountViewModel.launchSigner {
                                bookmarkGroupViewModel.removePostBookmark(
                                    bookmarkGroupViewModel.bookmarkGroupIdentifier,
                                    postId,
                                    isPrivate,
                                )
                            }
                        },
                        nav,
                    )
                }

                BookmarkType.ArticleBookmark -> {
                    RenderArticleList(
                        bookmarkGroupViewModel,
                        pagerState,
                        accountViewModel,
                        moveArticleBookmark = { articleAddress, isPrivate ->
                            accountViewModel.launchSigner {
                                bookmarkGroupViewModel.moveArticleBookmark(
                                    groupIdentifier = bookmarkGroupViewModel.bookmarkGroupIdentifier,
                                    articleAddress = articleAddress,
                                    isCurrentlyPrivate = isPrivate,
                                )
                            }
                        },
                        deleteArticleBookmark = { articleAddress, isPrivate ->
                            accountViewModel.launchSigner {
                                bookmarkGroupViewModel.removeArticleBookmark(
                                    bookmarkGroupViewModel.bookmarkGroupIdentifier,
                                    articleAddress,
                                    isPrivate,
                                )
                            }
                        },
                        nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeletedBookmarksBanner(
    bookmarkGroupViewModel: BookmarkGroupViewModel,
    bookmarkType: BookmarkType,
    accountViewModel: AccountViewModel,
) {
    val postsFlow = remember(bookmarkGroupViewModel) { bookmarkGroupViewModel.publicPosts() }
    val privatePostsFlow = remember(bookmarkGroupViewModel) { bookmarkGroupViewModel.privatePosts() }
    val articlesFlow = remember(bookmarkGroupViewModel) { bookmarkGroupViewModel.publicArticles() }
    val privateArticlesFlow = remember(bookmarkGroupViewModel) { bookmarkGroupViewModel.privateArticles() }

    val publicPosts by postsFlow.collectAsStateWithLifecycle()
    val privatePosts by privatePostsFlow.collectAsStateWithLifecycle()
    val publicArticles by articlesFlow.collectAsStateWithLifecycle()
    val privateArticles by privateArticlesFlow.collectAsStateWithLifecycle()

    val cache = accountViewModel.account.cache

    val deletedEventIds = remember(publicPosts, privatePosts, publicArticles, privateArticles, bookmarkType) { mutableSetOf<String>() }
    val deletedAddresses = remember(publicPosts, privatePosts, publicArticles, privateArticles, bookmarkType) { mutableSetOf<Address>() }
    val deletedCount =
        remember(publicPosts, privatePosts, publicArticles, privateArticles, bookmarkType) {
            deletedEventIds.clear()
            deletedAddresses.clear()
            val scope =
                when (bookmarkType) {
                    BookmarkType.PostBookmark -> publicPosts + privatePosts
                    BookmarkType.ArticleBookmark -> publicArticles + privateArticles
                }
            scope.forEach { note ->
                val event = note.event
                if (event != null && cache.hasBeenDeleted(event)) {
                    deletedEventIds.add(note.idHex)
                    if (note is AddressableNote) deletedAddresses.add(note.address)
                }
            }
            deletedEventIds.size + deletedAddresses.size
        }

    var bannerDismissed by remember(bookmarkType) { mutableStateOf(false) }
    LaunchedEffect(deletedCount) {
        if (deletedCount == 0) bannerDismissed = false
    }

    if (!bannerDismissed) {
        DeletedItemsBanner(
            count = deletedCount,
            onRemove = {
                accountViewModel.launchSigner {
                    bookmarkGroupViewModel.removeDeletedBookmarksFromGroup(
                        deletedEventIds = deletedEventIds.toSet(),
                        deletedAddresses = deletedAddresses.toSet(),
                    )
                }
                bannerDismissed = true
            },
            onDismiss = { bannerDismissed = true },
        )
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
fun BookmarkGroupHeaderTabs(
    bookmarkGroupViewModel: BookmarkGroupViewModel,
    bookmarkType: BookmarkType,
    pagerState: PagerState,
) {
    val bookmarkGroup by bookmarkGroupViewModel.selectedBookmarkGroupFlow.collectAsStateWithLifecycle()
    val privateItemTypeLabel =
        when (bookmarkType) {
            BookmarkType.PostBookmark -> {
                bookmarkGroup?.let {
                    stringRes(R.string.private_posts_count, it.privatePostBookmarks.size)
                } ?: stringRes(R.string.private_posts_label)
            }

            BookmarkType.ArticleBookmark -> {
                bookmarkGroup?.let {
                    stringRes(R.string.private_articles_count, it.privateArticleBookmarks.size)
                } ?: stringRes(R.string.private_posts_label)
            }
        }

    val publicItemTypeLabel =
        when (bookmarkType) {
            BookmarkType.PostBookmark -> {
                bookmarkGroup?.let {
                    stringRes(R.string.public_posts_count, it.publicPostBookmarks.size)
                } ?: stringRes(R.string.public_posts_label)
            }

            BookmarkType.ArticleBookmark -> {
                bookmarkGroup?.let {
                    stringRes(R.string.public_articles_count, it.publicArticleBookmarks.size)
                } ?: stringRes(R.string.public_articles_label)
            }
        }

    SecondaryTabRow(
        containerColor = MaterialTheme.colorScheme.background,
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

@Composable
fun BookmarkGroupActionsMenuButton(
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    val isActionListOpen = remember { mutableStateOf(false) }

    if (isActionListOpen.value) {
        M3ActionDialog(
            title = stringRes(R.string.list_actions_dialog_title),
            onDismiss = { isActionListOpen.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = Icons.Outlined.CellTower,
                    text = stringRes(R.string.bookmark_list_broadcast_btn_label),
                ) {
                    onBroadcastList()
                    isActionListOpen.value = false
                }
                M3ActionRow(
                    icon = Icons.Outlined.Delete,
                    text = stringRes(R.string.bookmark_list_delete_btn_label),
                    isDestructive = true,
                ) {
                    onDeleteList()
                    isActionListOpen.value = false
                }
            }
        }
    }

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
    }
}
