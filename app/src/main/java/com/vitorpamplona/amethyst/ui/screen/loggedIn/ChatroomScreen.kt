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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatRoomDataSource
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChatroomScreen(userId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null && userId != null) {
        val newPost = remember { mutableStateOf(TextFieldValue("")) }

        NostrChatRoomDataSource.loadMessagesBetween(account, userId)

        val feedViewModel: NostrChatRoomFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

        Column(Modifier.fillMaxHeight()) {
            NostrChatRoomDataSource.withUser?.let {
                ChatroomHeader(
                    it,
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            }

            Column(
                modifier = Modifier.fillMaxHeight().padding(vertical = 0.dp).weight(1f, true)
            ) {
                ChatroomFeedView(feedViewModel, accountViewModel, navController, "Room/${userId}")
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
                    modifier = Modifier.weight(1f, true),
                    shape = RoundedCornerShape(25.dp),
                    placeholder = {
                        Text(
                            text = "reply here.. ",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    trailingIcon = {
                        PostButton(
                            onPost = {
                                account.sendPrivateMeesage(newPost.value.text, userId)
                                newPost.value = TextFieldValue("")
                            },
                            newPost.value.text.isNotBlank(),
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    },
                    colors = TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}


@Composable
fun ChatroomHeader(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val authorState by baseUser.live.observeAsState()
    val author = authorState?.user

    Column(modifier = Modifier.clickable(
            onClick = { navController.navigate("User/${author?.pubkeyHex}") }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                AsyncImage(
                    model = author?.profilePicture(),
                    placeholder = rememberAsyncImagePainter("https://robohash.org/${author?.pubkeyHex}.png"),
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .width(35.dp).height(35.dp)
                        .clip(shape = CircleShape)
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (author != null)
                            UsernameDisplay(author)
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