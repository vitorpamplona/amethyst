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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.FollowSet
import com.vitorpamplona.amethyst.model.nip51Lists.followSets.SetVisibility
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.launch

@Composable
fun ListsAndSetsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followSetsViewModel: FollowSetFeedViewModel =
        viewModel(
            key = "FollowSetFeedViewModel",
            factory = FollowSetFeedViewModel.Factory(accountViewModel.account),
        )

    ListsAndSetsScreen(
        followSetsViewModel,
        accountViewModel,
        nav,
    )
}

@Composable
fun ListsAndSetsScreen(
    followSetsViewModel: FollowSetFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Custom Lists Start")
                    followSetsViewModel.invalidateData()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    val followSetsFlow by followSetsViewModel.feedContent.collectAsStateWithLifecycle()

    CustomListsScreen(
        followSetsFlow,
        refresh = {
            followSetsViewModel.invalidateData()
        },
        addItem = { title: String, description: String? ->

            followSetsViewModel.addFollowSet(
                setName = title,
                setDescription = description,
                account = accountViewModel.account,
            )
        },
        openItem = {
            nav.nav(Route.FollowSetRoute(it))
        },
        renameItem = { followSet, newValue ->
            followSetsViewModel.renameFollowSet(
                newName = newValue,
                followSet = followSet,
                account = accountViewModel.account,
            )
        },
        deleteItem = { followSet ->
            followSetsViewModel.deleteFollowSet(
                followSet = followSet,
                account = accountViewModel.account,
            )
        },
        accountViewModel,
        nav,
    )
}

@Composable
fun CustomListsScreen(
    followSetFeedState: FollowSetFeedState,
    refresh: () -> Unit,
    addItem: (title: String, description: String?) -> Unit,
    openItem: (identifier: String) -> Unit,
    renameItem: (followSet: FollowSet, newName: String) -> Unit,
    deleteItem: (followSet: FollowSet) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()

    DisappearingScaffold(
        isInvertedLayout = false,
        accountViewModel = accountViewModel,
        topBar = {
            Column {
                TopBarWithBackButton(stringRes(R.string.my_lists_and_sets), nav::popBack)
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = TabRowHeight,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(text = stringRes(R.string.follow_sets), overflow = TextOverflow.Visible) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(text = stringRes(R.string.labeled_bookmarks), overflow = TextOverflow.Visible) },
                    )
                }
            }
        },
        floatingButton = {
            // TODO: Show components based on current tab
            FollowSetFabsAndMenu(
                onAddSet = { name: String, description: String? ->
                    addItem(name, description)
                },
            )
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 10.dp,
                    end = 10.dp,
                ).fillMaxHeight(),
        ) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 ->
                        FollowSetFeedView(
                            followSetFeedState = followSetFeedState,
                            onRefresh = refresh,
                            onOpenItem = openItem,
                            onRenameItem = renameItem,
                            onDeleteItem = deleteItem,
                        )

                    1 -> LabeledBookmarksFeedView()
//                    2 -> GeneralBookmarksFeedView()
                }
            }
        }
    }
}

@Composable
private fun FollowSetFabsAndMenu(
    modifier: Modifier = Modifier,
    onAddSet: (name: String, description: String?) -> Unit,
) {
    val isSetAdditionDialogOpen = remember { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        text = {
            Text(text = "New")
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
        NewSetCreationDialog(
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
fun NewSetCreationDialog(
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

@Composable
fun LabeledBookmarksFeedView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Not implemented yet.")
        Spacer(modifier = StdVertSpacer)
    }
}

@Composable
fun GeneralBookmarksFeedView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Not implemented yet.")
        Spacer(modifier = StdVertSpacer)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SetItemPreview() {
    val sampleFollowSet =
        FollowSet(
            identifierTag = "00001-2222",
            title = "Sample List Title",
            description = "Sample List Description",
            visibility = SetVisibility.Mixed,
            emptySet(),
        )
    ThemeComparisonColumn {
        CustomSetItem(
            modifier = Modifier,
            sampleFollowSet,
            onFollowSetClick = {
                println("follow set: ${sampleFollowSet.identifierTag}")
            },
            onFollowSetRename = {
                println("Follow set new name: $it")
            },
            onFollowSetDelete = {
                println(" The follow set ${sampleFollowSet.title} has been deleted.")
            },
        )
    }
}
