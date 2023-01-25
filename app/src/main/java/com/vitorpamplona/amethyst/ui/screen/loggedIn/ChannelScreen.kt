package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChannelScreen(channelId: String?, accountViewModel: AccountViewModel, accountStateViewModel: AccountStateViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null && channelId != null) {
        val newPost = remember { mutableStateOf(TextFieldValue("")) }

        NostrChannelDataSource.loadMessagesBetween(channelId)

        val channelState by NostrChannelDataSource.channel!!.live.observeAsState()
        val channel = channelState?.channel ?: return

        val feedViewModel: NostrChannelFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

        Column(Modifier.fillMaxHeight()) {
            ChannelHeader(
                channel, account,
                accountStateViewModel = accountStateViewModel,
                navController = navController
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true)
            ) {
                ChatroomFeedView(feedViewModel, accountViewModel, navController)
            }

            //LAST ROW
            Row(modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newPost.value,
                    onValueChange = { newPost.value = it },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier
                        .weight(1f, true)
                        .padding(end = 10.dp),
                    placeholder = {
                        Text(
                            text = "reply here.. ",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    textStyle = TextStyle(textDirection = TextDirection.Content),
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
fun ChannelHeader(baseChannel: Channel, account: Account, accountStateViewModel: AccountStateViewModel, navController: NavController) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel

    Column() {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                AsyncImage(
                    model = channel?.profilePicture(),
                    placeholder = null,
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .width(35.dp)
                        .height(35.dp)
                        .clip(shape = CircleShape)
                )

                Column(modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)) {
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(modifier = Modifier.height(35.dp).padding(bottom = 3.dp)) {
                    channel?.let { NoteCopyButton(it) }

                    channel?.let {
                        if (channel.creator == account.userProfile()) {
                            EditButton(account, it)
                        } else {
                            if (account.followingChannels.contains(channel.idHex)) {
                                LeaveButton(account, channel, navController)
                            } else {
                                JoinButton(account, channel, navController)
                            }
                        }
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


@Composable
private fun NoteCopyButton(
    note: Channel
) {
    val clipboardManager = LocalClipboardManager.current

    Button(
        modifier = Modifier.padding(horizontal = 3.dp).width(50.dp),
        onClick = { clipboardManager.setText(AnnotatedString(note.id.toNote())) },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = "Copies the Note ID to the clipboard for sharing"
        )
    }
}

@Composable
private fun EditButton(account: Account, channel: Channel) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    if (wantsToPost)
        NewChannelView({ wantsToPost = false }, account = account, channel)

    Button(
        modifier = Modifier.padding(horizontal = 3.dp).width(50.dp),
        onClick = { wantsToPost = true },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = "Edits the Channel Metadata"
        )
    }
}

@Composable
private fun JoinButton(account: Account, channel: Channel, navController: NavController) {
    val context = LocalContext.current.applicationContext

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            account.joinChannel(channel.idHex)
            LocalPreferences(context).saveToEncryptedStorage(account)
            navController.navigate(Route.Message.route)
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Join", color = Color.White)
    }
}

@Composable
private fun LeaveButton(account: Account, channel: Channel, navController: NavController) {
    val context = LocalContext.current.applicationContext

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            account.leaveChannel(channel.idHex)
            LocalPreferences(context).saveToEncryptedStorage(account)
            navController.navigate(Route.Message.route)
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Leave", color = Color.White)
    }
}