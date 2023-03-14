package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.note.ChannelName
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel as CoroutineChannel

@Composable
fun SearchScreen(
    accountViewModel: AccountViewModel,
    feedViewModel: FeedViewModel,
    navController: NavController,
    scrollToTop: Boolean = false
) {
    val lifeCycleOwner = LocalLifecycleOwner.current
    val account = accountViewModel.accountLiveData.value?.account ?: return

    LaunchedEffect(accountViewModel) {
        GlobalFeedFilter.account = account
        NostrGlobalDataSource.resetFilters()
        feedViewModel.refresh()
    }

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Global Start")
                NostrGlobalDataSource.start()
                feedViewModel.refresh()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Global Stop")
                NostrGlobalDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            SearchBar(accountViewModel, navController)
            FeedView(feedViewModel, accountViewModel, navController, null, ScrollStateKeys.GLOBAL_SCREEN, scrollToTop)
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun SearchBar(accountViewModel: AccountViewModel, navController: NavController) {
    var searchValue by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateOf<List<User>>(emptyList()) }
    val searchResultsNotes = remember { mutableStateOf<List<Note>>(emptyList()) }
    val searchResultsChannels = remember { mutableStateOf<List<Channel>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val onlineSearch = NostrSearchEventOrUserDataSource

    val isTrailingIconVisible by remember {
        derivedStateOf {
            searchValue.isNotBlank()
        }
    }

    // Create a channel for processing search queries.
    val searchTextChanges = remember {
        CoroutineChannel<String>(CoroutineChannel.CONFLATED)
    }

    LaunchedEffect(Unit) {
        // Wait for text changes to stop for 300 ms before firing off search.
        withContext(Dispatchers.IO) {
            searchTextChanges.receiveAsFlow()
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest {
                    if (it.removePrefix("npub").removePrefix("note").length >= 4) {
                        onlineSearch.search(it.trim())
                    }

                    searchResults.value = LocalCache.findUsersStartingWith(it)
                    searchResultsNotes.value = LocalCache.findNotesStartingWith(it).sortedBy { it.createdAt() }.reversed()
                    searchResultsChannels.value = LocalCache.findChannelsStartingWith(it)
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NostrSearchEventOrUserDataSource.clear()
        }
    }

    // LAST ROW
    Row(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = searchValue,
            onValueChange = {
                searchValue = it
                scope.launch(Dispatchers.IO) {
                    searchTextChanges.trySend(it)
                }
            },
            shape = RoundedCornerShape(25.dp),
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
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
                .defaultMinSize(minHeight = 20.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.npub_hex_username),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                if (isTrailingIconVisible) {
                    IconButton(
                        onClick = {
                            searchValue = ""
                            searchResults.value = emptyList()
                            searchResultsChannels.value = emptyList()
                            searchResultsNotes.value = emptyList()

                            onlineSearch.clear()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }

    if (searchValue.isNotBlank()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 10.dp
            )
        ) {
            itemsIndexed(searchResults.value, key = { _, item -> "u" + item.pubkeyHex }) { _, item ->
                UserCompose(item, accountViewModel = accountViewModel, navController = navController)
            }

            itemsIndexed(searchResultsChannels.value, key = { _, item -> "c" + item.idHex }) { _, item ->
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

            itemsIndexed(searchResultsNotes.value, key = { _, item -> "n" + item.idHex }) { _, item ->
                NoteCompose(item, accountViewModel = accountViewModel, navController = navController)
            }
        }
    }
}

@Composable
fun UserLine(
    baseUser: User,
    account: Account,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp
                )
        ) {
            UserPicture(baseUser, account.userProfile(), 55.dp, Modifier, null)

            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                val userState by baseUser.live().metadata.observeAsState()
                val user = userState?.user ?: return

                Text(
                    user.info?.about?.take(100) ?: "",
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
