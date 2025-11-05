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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleList
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ListOfPeopleListsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListOfPeopleListsScreen(
        listFlow = accountViewModel.account.peopleLists.uiListFlow,
        addItem = { title: String, description: String? ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.addFollowList(
                    listName = title,
                    listDescription = description,
                    account = accountViewModel.account,
                )
            }
        },
        openItem = {
            nav.nav(Route.PeopleListView(it))
        },
        renameItem = { followSet, newValue ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.renameFollowList(
                    newName = newValue,
                    peopleList = followSet,
                    account = accountViewModel.account,
                )
            }
        },
        changeItemDescription = { followSet, newDescription ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.modifyFollowSetDescription(
                    newDescription = newDescription,
                    peopleList = followSet,
                    account = accountViewModel.account,
                )
            }
        },
        cloneItem = { followSet, customName, customDescription ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.cloneFollowSet(
                    currentPeopleList = followSet,
                    customCloneName = customName,
                    customCloneDescription = customDescription,
                    account = accountViewModel.account,
                )
            }
        },
        deleteItem = { followSet ->
            accountViewModel.runIOCatching {
                accountViewModel.account.peopleLists.deleteFollowSet(
                    identifierTag = followSet.identifierTag,
                    account = accountViewModel.account,
                )
            }
        },
        nav,
    )
}

@Composable
fun ListOfPeopleListsScreen(
    listFlow: StateFlow<List<PeopleList>>,
    addItem: (title: String, description: String?) -> Unit,
    openItem: (identifier: String) -> Unit,
    renameItem: (peopleList: PeopleList, newName: String) -> Unit,
    changeItemDescription: (peopleList: PeopleList, newDescription: String?) -> Unit,
    cloneItem: (peopleList: PeopleList, customName: String?, customDesc: String?) -> Unit,
    deleteItem: (peopleList: PeopleList) -> Unit,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(R.string.my_lists), nav::popBack)
        },
        floatingActionButton = {
            PeopleListFabsAndMenu(
                onAddSet = addItem,
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
            AllPeopleListFeedView(
                listFlow = listFlow,
                onOpenItem = openItem,
                onRenameItem = renameItem,
                onItemDescriptionChange = changeItemDescription,
                onItemClone = cloneItem,
                onDeleteItem = deleteItem,
            )
        }
    }
}

@Composable
private fun PeopleListFabsAndMenu(onAddSet: (name: String, description: String?) -> Unit) {
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
        NewPeopleListCreationDialog(
            onDismiss = {
                isSetAdditionDialogOpen.value = false
            },
            onCreateList = { name, description ->
                onAddSet(name, description)
            },
        )
    }
}

@Composable
fun NewPeopleListCreationDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCreateList: (name: String, description: String?) -> Unit,
) {
    val newListName = remember { mutableStateOf("") }
    val newListDescription = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringRes(R.string.follow_set_creation_dialog_title),
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // For the new list name
                TextField(
                    value = newListName.value,
                    onValueChange = { newListName.value = it },
                    label = {
                        Text(text = stringRes(R.string.follow_set_creation_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the set description
                TextField(
                    value =
                        (
                            if (newListDescription.value != null) newListDescription.value else ""
                        ).toString(),
                    onValueChange = { newListDescription.value = it },
                    label = {
                        Text(text = stringRes(R.string.follow_set_creation_desc_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateList(newListName.value, newListDescription.value)
                    onDismiss()
                },
            ) {
                Text(stringRes(R.string.follow_set_creation_action_btn_label))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
