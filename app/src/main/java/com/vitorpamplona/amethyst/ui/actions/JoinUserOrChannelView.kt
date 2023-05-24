package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.note.ChannelName
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchBarViewModel
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
fun JoinUserOrChannelView(onClose: () -> Unit, account: Account, navController: NavController) {
    val searchBarViewModel: SearchBarViewModel = viewModel()
    searchBarViewModel.account = account

    Dialog(
        onDismissRequest = {
            NostrSearchEventOrUserDataSource.clear()
            searchBarViewModel.clean()
            onClose()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false
        )
    ) {
        Surface() {
            Column(
                modifier = Modifier.padding(10.dp).heightIn(min = 500.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = {
                        searchBarViewModel.clean()
                        NostrSearchEventOrUserDataSource.clear()
                        onClose()
                    })

                    Text(
                        text = stringResource(R.string.channel_list_join_conversation),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                RenderSeach(searchBarViewModel, account, navController)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RenderSeach(
    searchBarViewModel: SearchBarViewModel,
    account: Account,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val onlineSearch = NostrSearchEventOrUserDataSource

    val lifeCycleOwner = LocalLifecycleOwner.current

    // Create a channel for processing search queries.
    val searchTextChanges = remember {
        Channel<String>(Channel.CONFLATED)
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                if (searchBarViewModel.isSearching()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()

        // Wait for text changes to stop for 300 ms before firing off search.
        withContext(Dispatchers.IO) {
            searchTextChanges.receiveAsFlow()
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest {
                    if (it.length >= 2) {
                        onlineSearch.search(it.trim())
                    }

                    searchBarViewModel.invalidateData()

                    // makes sure to show the top of the search
                    scope.launch(Dispatchers.Main) {
                        listState.animateScrollToItem(0)
                    }
                }
        }
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
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
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // LAST ROW
    Row(
        modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            label = { Text(text = stringResource(R.string.channel_list_user_or_group_id)) },
            value = searchBarViewModel.searchValue,
            onValueChange = {
                searchBarViewModel.updateSearchValue(it)
                scope.launch(Dispatchers.IO) {
                    searchTextChanges.trySend(it)
                }
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
            },
            modifier = Modifier
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                if (searchBarViewModel.isTrailingIconVisible) {
                    IconButton(
                        onClick = {
                            searchBarViewModel.clean()
                            onlineSearch.clear()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            }
        )
    }

    if (searchBarViewModel.searchValue.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 10.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(
                    top = 10.dp,
                    bottom = 10.dp
                ),
                state = listState
            ) {
                itemsIndexed(
                    searchBarViewModel.searchResults.value,
                    key = { _, item -> "u" + item.pubkeyHex }
                ) { _, item ->
                    UserComposeForChat(
                        item,
                        account = account,
                        navController = navController
                    )
                }

                itemsIndexed(
                    searchBarViewModel.searchResultsChannels.value,
                    key = { _, item -> "c" + item.idHex }
                ) { _, item ->
                    ChannelName(
                        channelIdHex = item.idHex,
                        channelPicture = item.profilePicture(),
                        channelTitle = {
                            Text(
                                "${item.info.name}",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        channelLastTime = null,
                        channelLastContent = item.info.about,
                        false,
                        onClick = { navController.navigate("Channel/${item.idHex}") }
                    )
                }
            }
        }
    }
}

@Composable
fun UserComposeForChat(
    baseUser: User,
    account: Account,
    navController: NavController
) {
    Column(
        modifier =
        Modifier.clickable(
            onClick = { navController.navigate("Room/${baseUser.pubkeyHex}") }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserPicture(baseUser, navController, account.userProfile(), 55.dp)

            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                val baseUserState by baseUser.live().metadata.observeAsState()
                val user = baseUserState?.user ?: return

                Text(
                    user.info?.about ?: "",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}
