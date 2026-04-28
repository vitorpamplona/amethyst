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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.fabBottomBarPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun ListOfBookmarkGroupsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListOfBookmarkGroupsFeed(
        defaultBookmarks = accountViewModel.account.bookmarkState,
        oldBookmarks = accountViewModel.account.oldBookmarkState,
        pinnedNotes = accountViewModel.account.pinState,
        listSource = accountViewModel.account.labeledBookmarkLists.listFeedFlow,
        openDefaultBookmarks = { nav.nav(Route.Bookmarks) },
        openOldBookmarks = { nav.nav(Route.OldBookmarks) },
        openPinnedNotes = { nav.nav(Route.PinnedNotes) },
        addBookmarkGroup = { nav.nav(Route.BookmarkGroupMetadataEdit()) },
        openBookmarkGroup = { identifier, bookmarkType ->
            nav.nav(Route.BookmarkGroupView(identifier, bookmarkType))
        },
        renameBookmarkGroup = { bookmarkGroup ->
            nav.nav(Route.BookmarkGroupMetadataEdit(bookmarkGroup.identifier))
        },
        changeBookmarkGroupDescription = { bookmarkGroup ->
            nav.nav(Route.BookmarkGroupMetadataEdit(bookmarkGroup.identifier))
        },
        cloneBookmarkGroup = { bookmarkGroup, customName, customDesc ->
            accountViewModel.launchSigner {
                accountViewModel.account.labeledBookmarkLists.cloneBookmarkList(
                    currentBookmarkList = bookmarkGroup,
                    customCloneName = customName,
                    customCloneDescription = customDesc,
                    account = accountViewModel.account,
                )
            }
        },
        deleteBookmarkGroup = { bookmarkGroup ->
            accountViewModel.launchSigner {
                accountViewModel.account.labeledBookmarkLists.deleteBookmarkList(
                    bookmarkListIdentifier = bookmarkGroup.identifier,
                    account = accountViewModel.account,
                )
            }
        },
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ListOfBookmarkGroupsFeed(
    defaultBookmarks: BookmarkListState,
    oldBookmarks: OldBookmarkListState,
    pinnedNotes: PinListState,
    listSource: StateFlow<List<LabeledBookmarkList>>,
    openDefaultBookmarks: () -> Unit,
    openOldBookmarks: () -> Unit,
    openPinnedNotes: () -> Unit,
    addBookmarkGroup: () -> Unit,
    openBookmarkGroup: (identifier: String, bookmarkType: BookmarkType) -> Unit,
    renameBookmarkGroup: (bookmarkGroup: LabeledBookmarkList) -> Unit,
    changeBookmarkGroupDescription: (bookmarkGroup: LabeledBookmarkList) -> Unit,
    cloneBookmarkGroup: (bookmarkGroup: LabeledBookmarkList, customName: String?, customDesc: String?) -> Unit,
    deleteBookmarkGroup: (bookmarkGroup: LabeledBookmarkList) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.bookmark_lists), nav)
        },
        bottomBar = {
            AppBottomBar(Route.BookmarkGroups, nav, accountViewModel) { route ->
                if (route == Route.BookmarkGroups) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingActionButton = {
            BookmarkGroupFab(
                onAddGroup = addBookmarkGroup,
                modifier = Modifier.fabBottomBarPadding(nav),
            )
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ).fillMaxHeight(),
        ) {
            ListOfBookmarkGroupsFeedView(
                defaultBookmarks = defaultBookmarks,
                oldBookmarks = oldBookmarks,
                pinnedNotes = pinnedNotes,
                groupListFeedSource = listSource,
                openDefaultBookmarks = openDefaultBookmarks,
                openOldBookmarks = openOldBookmarks,
                openPinnedNotes = openPinnedNotes,
                onOpenItem = openBookmarkGroup,
                onRenameItem = renameBookmarkGroup,
                onItemDescriptionChange = changeBookmarkGroupDescription,
                onItemClone = cloneBookmarkGroup,
                onDeleteItem = deleteBookmarkGroup,
                listState = listState,
            )
        }
    }
}

@Composable
fun BookmarkGroupFab(
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(R.string.follow_set_create_btn_label))
        },
        icon = {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.PlaylistAdd,
                contentDescription = null,
            )
        },
        onClick = onAddGroup,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
