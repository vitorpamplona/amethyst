/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.ChannelName
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchBarViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JoinUserOrChannelView(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val searchBarViewModel: SearchBarViewModel =
        viewModel(
            key = "SearchBarViewModel",
            factory =
                SearchBarViewModel.Factory(
                    accountViewModel.account,
                ),
        )

    JoinUserOrChannelView(
        searchBarViewModel = searchBarViewModel,
        onClose = onClose,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun JoinUserOrChannelView(
    searchBarViewModel: SearchBarViewModel,
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = {
            NostrSearchEventOrUserDataSource.clear()
            searchBarViewModel.clear()
            onClose()
        },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            Column(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .heightIn(min = 500.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onPress = {
                            searchBarViewModel.clear()
                            NostrSearchEventOrUserDataSource.clear()
                            onClose()
                        },
                    )

                    Text(
                        text = stringResource(R.string.channel_list_join_conversation),
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = "",
                        color = MaterialTheme.colorScheme.placeholderText,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                RenderSearch(searchBarViewModel, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun RenderSearch(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    val lifeCycleOwner = LocalLifecycleOwner.current

    // Create a channel for processing search queries.
    val searchTextChanges = remember { Channel<String>(Channel.CONFLATED) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                checkNotInMainThread()
                if (searchBarViewModel.isSearchingFun()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Wait for text changes to stop for 300 ms before firing off search.
        withContext(Dispatchers.IO) {
            searchTextChanges
                .receiveAsFlow()
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest {
                    if (it.length >= 2) {
                        NostrSearchEventOrUserDataSource.search(it.trim())
                    }

                    searchBarViewModel.invalidateData()

                    // makes sure to show the top of the search
                    launch(Dispatchers.Main) { listState.animateScrollToItem(0) }
                }
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Join Start")
                    NostrSearchEventOrUserDataSource.start()
                    searchBarViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Join Stop")
                    NostrSearchEventOrUserDataSource.clear()
                    NostrSearchEventOrUserDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    // LAST ROW
    SearchEditTextForJoin(searchBarViewModel, searchTextChanges)

    RenderSearchResults(searchBarViewModel, listState, accountViewModel, nav)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SearchEditTextForJoin(
    searchBarViewModel: SearchBarViewModel,
    searchTextChanges: Channel<String>,
) {
    val scope = rememberCoroutineScope()

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier =
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            label = { Text(text = stringResource(R.string.channel_list_user_or_group_id)) },
            value = searchBarViewModel.searchValue,
            onValueChange = {
                searchBarViewModel.updateSearchValue(it)
                scope.launch(Dispatchers.IO) { searchTextChanges.trySend(it) }
            },
            leadingIcon = { SearchIcon(modifier = Size20Modifier, Color.Unspecified) },
            modifier =
                Modifier
                    .weight(1f, true)
                    .defaultMinSize(minHeight = 20.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            keyboardController?.show()
                        }
                    },
            placeholder = {
                Text(
                    text = stringResource(R.string.channel_list_user_or_group_id_demo),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                if (searchBarViewModel.isSearching) {
                    IconButton(
                        onClick = {
                            searchBarViewModel.clear()
                            NostrSearchEventOrUserDataSource.clear()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun RenderSearchResults(
    searchBarViewModel: SearchBarViewModel,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (searchBarViewModel.isSearching) {
        val users by searchBarViewModel.searchResultsUsers.collectAsStateWithLifecycle()
        val channels by searchBarViewModel.searchResultsChannels.collectAsStateWithLifecycle()

        val automaticallyShowProfilePicture =
            remember {
                accountViewModel.settings.showProfilePictures.value
            }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = FeedPadding,
                state = listState,
            ) {
                itemsIndexed(
                    users,
                    key = { _, item -> "u" + item.pubkeyHex },
                ) { _, item ->
                    UserComposeForChat(item, accountViewModel) {
                        accountViewModel.createChatRoomFor(item) { nav("Room/$it") }

                        searchBarViewModel.clear()
                    }

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }

                itemsIndexed(
                    channels,
                    key = { _, item -> "c" + item.idHex },
                ) { _, item ->
                    RenderChannel(item, automaticallyShowProfilePicture) {
                        nav("Channel/${item.idHex}")
                        searchBarViewModel.clear()
                    }

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderChannel(
    item: com.vitorpamplona.amethyst.model.Channel,
    loadProfilePicture: Boolean,
    onClick: () -> Unit,
) {
    val hasNewMessages = remember { mutableStateOf(false) }

    ChannelName(
        channelIdHex = item.idHex,
        channelPicture = item.profilePicture(),
        channelTitle = {
            Text(
                item.toBestDisplayName(),
                fontWeight = FontWeight.Bold,
            )
        },
        channelLastTime = null,
        channelLastContent = item.summary(),
        hasNewMessages,
        onClick = onClick,
        loadProfilePicture = loadProfilePicture,
    )
}

@Composable
fun UserComposeForChat(
    baseUser: User,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.clickable(
                onClick = onClick,
            ).padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClickableUserPicture(baseUser, Size55dp, accountViewModel)

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { UsernameDisplay(baseUser) }

            DisplayUserAboutInfo(baseUser)
        }
    }
}

@Composable
private fun DisplayUserAboutInfo(baseUser: User) {
    val baseUserState by baseUser.live().metadata.observeAsState()
    val about by remember(baseUserState) { derivedStateOf { baseUserState?.user?.info?.about ?: "" } }

    Text(
        text = about,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
