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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ListOfBookmarkGroupsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListOfBookmarkGroupsFeed(
        listSource = accountViewModel.account.labeledBookmarkLists.listFeedFlow,
        addBookmarkGroup = { title, description ->
            accountViewModel.runIOCatching {
                accountViewModel.account.labeledBookmarkLists.addLabeledBookmarkList(
                    listName = title,
                    listDescription = description,
                    account = accountViewModel.account,
                )
            }
        },
        openBookmarkGroup = {
            nav.nav(Route.BookmarkGroupView(it))
        },
        renameBookmarkGroup = { bookmarkGroup, newName ->
            accountViewModel.runIOCatching {
                accountViewModel.account.labeledBookmarkLists.renameBookmarkList(
                    newName = newName,
                    bookmarkList = bookmarkGroup,
                    account = accountViewModel.account,
                )
            }
        },
        changeBookmarkGroupDescription = { bookmarkGroup, newDescription ->
            accountViewModel.runIOCatching {
                accountViewModel.account.labeledBookmarkLists.modifyListDescription(
                    newDescription = newDescription,
                    bookmarkList = bookmarkGroup,
                    account = accountViewModel.account,
                )
            }
        },
        cloneBookmarkGroup = { bookmarkGroup, customName, customDesc ->
            accountViewModel.runIOCatching {
                accountViewModel.account.labeledBookmarkLists.cloneBookmarkList(
                    currentBookmarkList = bookmarkGroup,
                    customCloneName = customName,
                    customCloneDescription = customDesc,
                    account = accountViewModel.account,
                )
            }
        },
        deleteBookmarkGroup = { bookmarkGroup ->
            accountViewModel.runIOCatching {
                accountViewModel.account.labeledBookmarkLists.deleteBookmarkList(
                    bookmarkList = bookmarkGroup,
                    account = accountViewModel.account,
                )
            }
        },
        nav,
    )
}

@Composable
fun ListOfBookmarkGroupsFeed(
    listSource: StateFlow<List<LabeledBookmarkList>>,
    addBookmarkGroup: (title: String, description: String?) -> Unit,
    openBookmarkGroup: (identifier: String) -> Unit,
    renameBookmarkGroup: (bookmarkGroup: LabeledBookmarkList, newName: String) -> Unit,
    changeBookmarkGroupDescription: (bookmarkGroup: LabeledBookmarkList, newDescription: String?) -> Unit,
    cloneBookmarkGroup: (bookmarkGroup: LabeledBookmarkList, customName: String?, customDesc: String?) -> Unit,
    deleteBookmarkGroup: (bookmarkGroup: LabeledBookmarkList) -> Unit,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = "Bookmark Groups", nav::popBack)
        },
        floatingActionButton = {
            BookmarkGroupFabAndMenu(onAddGroup = addBookmarkGroup)
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
                groupListFeedSource = listSource,
                onOpenItem = openBookmarkGroup,
                onRenameItem = renameBookmarkGroup,
                onItemDescriptionChange = changeBookmarkGroupDescription,
                onItemClone = cloneBookmarkGroup,
                onDeleteItem = deleteBookmarkGroup,
            )
        }
    }
}

@Composable
fun BookmarkGroupFabAndMenu(onAddGroup: (name: String, description: String?) -> Unit) {
    val isSetAdditionDialogOpen = remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(R.string.follow_set_create_btn_label))
        },
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
            )
        },
        onClick = {
            isSetAdditionDialogOpen.value = true
        },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    )

    if (isSetAdditionDialogOpen.value) {
        NewBookmarkGroupCreationDialog(
            onDismiss = {
                isSetAdditionDialogOpen.value = false
            },
            onCreateGroup = { name, description ->
                onAddGroup(name, description)
            },
        )
    }
}
