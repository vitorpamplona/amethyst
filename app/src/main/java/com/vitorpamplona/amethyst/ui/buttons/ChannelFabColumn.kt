package com.vitorpamplona.amethyst.ui.buttons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.JoinUserOrChannelView
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun ChannelFabColumn(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var isOpen by remember {
        mutableStateOf(false)
    }

    var wantsToJoinChannelOrUser by remember {
        mutableStateOf(false)
    }

    var wantsToCreateChannel by remember {
        mutableStateOf(false)
    }

    if (wantsToCreateChannel) {
        NewChannelView({ wantsToCreateChannel = false }, accountViewModel = accountViewModel)
    }

    if (wantsToJoinChannelOrUser) {
        JoinUserOrChannelView({ wantsToJoinChannelOrUser = false }, accountViewModel = accountViewModel, nav = nav)
    }

    Column() {
        if (isOpen) {
            OutlinedButton(
                onClick = { wantsToJoinChannelOrUser = true; isOpen = false },
                modifier = Modifier.size(55.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
                contentPadding = PaddingValues(bottom = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.channel_list_join_channel),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = { wantsToCreateChannel = true; isOpen = false },
                modifier = Modifier.size(55.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
                contentPadding = PaddingValues(bottom = 3.dp)
            ) {
                Text(
                    text = stringResource(R.string.channel_list_create_channel),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        OutlinedButton(
            onClick = { isOpen = !isOpen },
            modifier = Modifier.size(55.dp),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.new_channel),
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }
}
