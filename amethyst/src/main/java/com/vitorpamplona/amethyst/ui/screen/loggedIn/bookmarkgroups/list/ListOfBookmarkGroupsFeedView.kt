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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.BookmarkType
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ListOfBookmarkGroupsFeedView(
    defaultBookmarks: BookmarkListState,
    groupListFeedSource: StateFlow<List<LabeledBookmarkList>>,
    openDefaultBookmarks: () -> Unit,
    onOpenItem: (String, BookmarkType) -> Unit,
    onRenameItem: (targetBookmarkGroup: LabeledBookmarkList) -> Unit,
    onItemDescriptionChange: (bookmarkGroup: LabeledBookmarkList) -> Unit,
    onItemClone: (bookmarkGroup: LabeledBookmarkList, customName: String?, customDesc: String?) -> Unit,
    onDeleteItem: (bookmarkGroup: LabeledBookmarkList) -> Unit,
) {
    val bookmarkGroupFeedState by groupListFeedSource.collectAsStateWithLifecycle()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = FeedPadding,
    ) {
        item {
            DefaultBookmarkList(defaultBookmarks, openDefaultBookmarks)
            HorizontalDivider(thickness = DividerThickness)
        }

        itemsIndexed(
            bookmarkGroupFeedState,
            key = { _: Int, item: LabeledBookmarkList -> item.identifier },
        ) { _, groupItem ->
            BookmarkGroupItem(
                modifier = Modifier.fillMaxSize().animateItem(),
                bookmarkList = groupItem,
                onClick = { bookmarkType -> onOpenItem(groupItem.identifier, bookmarkType) },
                onRename = { onRenameItem(groupItem) },
                onDescriptionChange = { onItemDescriptionChange(groupItem) },
                onClone = { cloneName, cloneDescription -> onItemClone(groupItem, cloneName, cloneDescription) },
                onDelete = { onDeleteItem(groupItem) },
            )
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
fun DefaultBookmarkList(
    defaultBookmarks: BookmarkListState,
    openDefaultBookmarks: () -> Unit,
) {
    val bookmarkState by defaultBookmarks.bookmarks.collectAsStateWithLifecycle()

    ListItem(
        modifier = Modifier.clickable(onClick = openDefaultBookmarks),
        headlineContent = {
            Text(stringRes(R.string.bookmarks_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringRes(R.string.bookmarks_explainer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
            }
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = stringRes(R.string.bookmark_list_icon_label),
                    modifier = Size40Modifier,
                )
                Spacer(StdVertSpacer)
                BookmarkMembershipStatusAndNumberDisplay(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    postBookmarksSize = bookmarkState.public.size + bookmarkState.private.size,
                    articleBookmarksSize = 0,
                )
            }
        },
    )
}
