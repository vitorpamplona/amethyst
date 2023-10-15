package com.vitorpamplona.amethyst.ui.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier

@Composable
fun ChannelFabColumn(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var isOpen by remember {
        mutableStateOf(false)
    }

    var wantsToSendNewMessage by remember {
        mutableStateOf(false)
    }

    var wantsToCreateChannel by remember {
        mutableStateOf(false)
    }

    if (wantsToCreateChannel) {
        NewChannelView({ wantsToCreateChannel = false }, accountViewModel = accountViewModel)
    }

    if (wantsToSendNewMessage) {
        NewPostView({ wantsToSendNewMessage = false }, enableMessageInterface = true, accountViewModel = accountViewModel, nav = nav)
        // JoinUserOrChannelView({ wantsToJoinChannelOrUser = false }, accountViewModel = accountViewModel, nav = nav)
    }

    Column() {
        if (isOpen) {
            FloatingActionButton(
                onClick = { wantsToSendNewMessage = true; isOpen = false },
                modifier = Size55Modifier,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.messages_new_message),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = Font12SP
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            FloatingActionButton(
                onClick = { wantsToCreateChannel = true; isOpen = false },
                modifier = Size55Modifier,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.messages_create_public_chat),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = Font12SP
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        FloatingActionButton(
            onClick = { isOpen = !isOpen },
            modifier = Size55Modifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.messages_create_public_chat),
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }
}
