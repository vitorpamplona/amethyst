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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.BookmarkGroupsFeedEmpty
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.NewListButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark

@Composable
fun ArticleBookmarkListManagementScreen(
    articleAddress: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address = articleAddress, accountViewModel = accountViewModel) {
        it?.let {
            ListManagementView(
                modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun ListManagementView(
    modifier: Modifier = Modifier,
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val bookmarkGroups by accountViewModel.account.labeledBookmarkLists.listFeedFlow
        .collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopBarWithBackButton(caption = stringRes(R.string.article_bookmark_management_title), nav::popBack)
        },
        floatingActionButton = {
            NewListButton { nav.nav(Route.BookmarkGroupMetadataEdit()) }
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
            if (bookmarkGroups.isEmpty()) {
                BookmarkGroupsFeedEmpty(message = stringRes(R.string.bookmark_list_feed_empty_msg))
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(items = bookmarkGroups, key = { _: Int, item: LabeledBookmarkList -> item.identifier }) { _, bookmarkList ->
                        val maybePublicBookmark = bookmarkList.publicArticleBookmarks.firstOrNull { it.address == note.address }
                        val maybePrivateBookmark = bookmarkList.privateArticleBookmarks.firstOrNull { it.address == note.address }
                        BookmarkGroupManagementItem(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            listTitle = bookmarkList.title,
                            isPublicMemberBookmark = maybePublicBookmark != null,
                            isPrivateMemberBookmark = maybePrivateBookmark != null,
                            onClick = { nav.nav(Route.BookmarkGroupView(bookmarkList.identifier, BookmarkType.ArticleBookmark)) },
                            onAddBookmarkToGroup = { shouldBePrivate ->
                                accountViewModel.launchSigner {
                                    accountViewModel.account.labeledBookmarkLists.addBookmarkToList(
                                        bookmark = AddressBookmark(address = note.address, relayHint = note.relayHintUrl()),
                                        bookmarkListIdentifier = bookmarkList.identifier,
                                        isBookmarkPrivate = shouldBePrivate,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                            onRemoveBookmarkFromGroup = {
                                accountViewModel.launchSigner {
                                    accountViewModel.account.labeledBookmarkLists.removeBookmarkFromList(
                                        bookmark = AddressBookmark(address = note.address),
                                        bookmarkListIdentifier = bookmarkList.identifier,
                                        isBookmarkPrivate = maybePrivateBookmark != null,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
