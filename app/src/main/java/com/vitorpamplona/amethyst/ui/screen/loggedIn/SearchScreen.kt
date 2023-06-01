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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.AboutDisplay
import com.vitorpamplona.amethyst.ui.note.ChannelName
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableView
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
import java.util.regex.Pattern
import kotlinx.coroutines.channels.Channel as CoroutineChannel

@Composable
fun SearchScreen(
    searchFeedViewModel: NostrGlobalFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val lifeCycleOwner = LocalLifecycleOwner.current

    WatchAccountForSearchScreen(searchFeedViewModel, accountViewModel)

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Global Start")
                NostrGlobalDataSource.start()
                NostrSearchEventOrUserDataSource.start()
                searchFeedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Global Stop")
                NostrSearchEventOrUserDataSource.clear()
                NostrSearchEventOrUserDataSource.stop()
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
            SearchBar(accountViewModel, nav)
            RefresheableView(searchFeedViewModel, accountViewModel, nav, null, ScrollStateKeys.GLOBAL_SCREEN)
        }
    }
}

@Composable
fun WatchAccountForSearchScreen(searchFeedViewModel: NostrGlobalFeedViewModel, accountViewModel: AccountViewModel) {
    LaunchedEffect(accountViewModel) {
        NostrGlobalDataSource.resetFilters()
        NostrSearchEventOrUserDataSource.start()
        searchFeedViewModel.invalidateData(true)
    }
}

class SearchBarViewModel : ViewModel() {
    var account: Account? = null

    var searchValue by mutableStateOf("")
    val searchResultsUsers = mutableStateOf<List<User>>(emptyList())
    val searchResultsNotes = mutableStateOf<List<Note>>(emptyList())
    val searchResultsChannels = mutableStateOf<List<Channel>>(emptyList())
    val hashtagResults = mutableStateOf<List<String>>(emptyList())

    val isTrailingIconVisible by
    derivedStateOf {
        searchValue.isNotBlank()
    }

    fun updateSearchValue(newValue: String) {
        searchValue = newValue
    }

    private fun runSearch() {
        if (searchValue.isBlank()) {
            hashtagResults.value = emptyList()
            searchResultsUsers.value = emptyList()
            searchResultsChannels.value = emptyList()
            searchResultsNotes.value = emptyList()
            return
        }

        hashtagResults.value = findHashtags(searchValue)
        searchResultsUsers.value = LocalCache.findUsersStartingWith(searchValue).sortedWith(compareBy({ account?.isFollowing(it) }, { it.toBestDisplayName() })).reversed()
        searchResultsNotes.value = LocalCache.findNotesStartingWith(searchValue).sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
        searchResultsChannels.value = LocalCache.findChannelsStartingWith(searchValue)
    }

    fun clean() {
        searchValue = ""
        searchResultsUsers.value = emptyList()
        searchResultsChannels.value = emptyList()
        searchResultsNotes.value = emptyList()
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            runSearch()
        }
    }

    fun isSearching() = searchValue.isNotBlank()
}

@OptIn(FlowPreview::class)
@Composable
private fun SearchBar(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val searchBarViewModel: SearchBarViewModel = viewModel()
    searchBarViewModel.account = accountViewModel.account

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val onlineSearch = NostrSearchEventOrUserDataSource

    // Create a channel for processing search queries.
    val searchTextChanges = remember {
        CoroutineChannel<String>(CoroutineChannel.CONFLATED)
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                if (searchBarViewModel.isSearching()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
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
                    scope.launch(Dispatchers.Main) { listState.animateScrollToItem(0) }
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
            value = searchBarViewModel.searchValue,
            onValueChange = {
                searchBarViewModel.updateSearchValue(it)
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
            },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }

    if (searchBarViewModel.isSearching()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 10.dp
            ),
            state = listState
        ) {
            itemsIndexed(searchBarViewModel.hashtagResults.value, key = { _, item -> "#" + item }) { _, item ->
                HashtagLine(item) {
                    nav("Hashtag/$item")
                }
            }

            itemsIndexed(searchBarViewModel.searchResultsUsers.value, key = { _, item -> "u" + item.pubkeyHex }) { _, item ->
                UserCompose(item, accountViewModel = accountViewModel, nav = nav)
            }

            itemsIndexed(searchBarViewModel.searchResultsChannels.value, key = { _, item -> "c" + item.idHex }) { _, item ->
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
                    onClick = { nav("Channel/${item.idHex}") }
                )
            }

            itemsIndexed(searchBarViewModel.searchResultsNotes.value, key = { _, item -> "n" + item.idHex }) { _, item ->
                NoteCompose(item, accountViewModel = accountViewModel, nav = nav)
            }
        }
    }
}

val hashtagSearch = Pattern.compile("(?:\\s|\\A)#([^\\s!@#\$%^&*()=+./,\\[{\\]};:'\"?><]+)")

fun findHashtags(content: String): List<String> {
    val matcher = hashtagSearch.matcher(content)
    val returningList = mutableSetOf<String>()
    while (matcher.find()) {
        try {
            val tag = matcher.group(1)
            if (tag != null && tag.isNotBlank()) {
                returningList.add(tag)
            }
        } catch (e: Exception) {
        }
    }
    return returningList.toList()
}

@Composable
fun HashtagLine(tag: String, onClick: () -> Unit) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Search hashtag: #$tag",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}

@Composable
fun UserLine(
    baseUser: User,
    accountViewModel: AccountViewModel,
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
            UserPicture(baseUser, 55.dp, accountViewModel, Modifier, null)

            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                AboutDisplay(baseUser)
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}
