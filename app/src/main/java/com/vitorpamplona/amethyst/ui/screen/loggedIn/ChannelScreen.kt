package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatRoomDataSource
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.note.UserDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChannelScreen(channelId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null && channelId != null) {
        val newPost = remember { mutableStateOf(TextFieldValue("")) }

        NostrChannelDataSource.loadMessagesBetween(channelId)

        val channelState by NostrChannelDataSource.channel!!.live.observeAsState()
        val channel = channelState?.channel ?: return

        val feedViewModel: FeedViewModel = viewModel { FeedViewModel( NostrChannelDataSource ) }

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

        Column(Modifier.fillMaxHeight()) {
            ChannelHeader(
                channel,
                accountViewModel = accountViewModel,
                navController = navController
            )

            Column(
                modifier = Modifier.fillMaxHeight().padding(vertical = 0.dp).weight(1f, true)
            ) {
                ChatroomFeedView(feedViewModel, accountViewModel, navController)
            }

            //LAST ROW
            Row(modifier = Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newPost.value,
                    onValueChange = { newPost.value = it },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.weight(1f, true).padding(end = 10.dp),
                    placeholder = {
                        Text(
                            text = "reply here.. ",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    trailingIcon = {
                        PostButton(
                            onPost = {
                                account.sendChannelMeesage(newPost.value.text, channel.idHex)
                                newPost.value = TextFieldValue("")
                            },
                            newPost.value.text.isNotBlank(),
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ChannelHeader(baseChannel: Channel, accountViewModel: AccountViewModel, navController: NavController) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel

    Column() {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                AsyncImage(
                    model = channel?.profilePicture(),
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .width(35.dp).height(35.dp)
                        .clip(shape = CircleShape)
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${channel?.info?.name}",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${channel?.info?.about}",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            thickness = 0.25.dp
        )
    }
}