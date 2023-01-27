package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.ui.note.ChannelName
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun SearchScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrGlobalFeedViewModel = viewModel()

    LaunchedEffect(Unit) {
        feedViewModel.refresh()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            SearchBar(accountViewModel, navController)
            FeedView(feedViewModel, accountViewModel, navController, null)
        }
    }
}

@Composable
private fun SearchBar(accountViewModel: AccountViewModel, navController: NavController) {
    val searchValue = remember { mutableStateOf(TextFieldValue("")) }
    val searchResults = remember { mutableStateOf<List<User>>(emptyList()) }
    val searchResultsNotes = remember { mutableStateOf<List<Note>>(emptyList()) }
    val searchResultsChannels = remember { mutableStateOf<List<Channel>>(emptyList()) }

    val onlineSearch = NostrSearchEventOrUserDataSource

    val isTrailingIconVisible by remember {
        derivedStateOf {
            searchValue.value.text.isNotBlank()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NostrSearchEventOrUserDataSource.clear()
        }
    }

    //LAST ROW
    Row(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = searchValue.value,
            onValueChange = {
                searchValue.value = it

                if (it.text.removePrefix("npub").removePrefix("note").length >= 4)
                    onlineSearch.search(it.text)

                searchResults.value = LocalCache.findUsersStartingWith(it.text)
                searchResultsNotes.value = LocalCache.findNotesStartingWith(it.text)
                searchResultsChannels.value = LocalCache.findChannelsStartingWith(it.text)
            },
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
                    text = "npub, hex, username ",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            },
            trailingIcon = {
                if (isTrailingIconVisible) {
                    IconButton(
                        onClick = {
                            searchValue.value = TextFieldValue("")
                            searchResults.value = emptyList()
                            searchResultsChannels.value = emptyList()
                            searchResultsNotes.value = emptyList()

                            onlineSearch.clear()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            }
        )
    }

    if (searchValue.value.text.isNotBlank()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 10.dp
            )
        ) {
            itemsIndexed(searchResults.value, key = { _, item -> "u"+item.pubkeyHex }) { index, item ->
                UserCompose(item, accountViewModel = accountViewModel, navController = navController)
            }

            itemsIndexed(searchResultsChannels.value, key = { _, item -> "c"+item.idHex }) { index, item ->
                ChannelName(
                    channelPicture = item.profilePicture(),
                    channelPicturePlaceholder = null,
                    channelTitle = {
                        Text(
                            "${item.info.name}",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    channelLastTime = null,
                    channelLastContent = item.info.about,
                    false,
                    onClick = { navController.navigate("Channel/${item.idHex}") })
            }

            itemsIndexed(searchResultsNotes.value, key = { _, item -> "n"+item.idHex }) { index, item ->
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
    val userState by baseUser.live.observeAsState()
    val user = userState?.user ?: return

    Column(modifier = Modifier
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

            UserPicture(user, account.userProfile(), 55.dp, Modifier, null)

            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(user)
                }

                Text(
                    user.info.about?.take(100) ?: "",
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
