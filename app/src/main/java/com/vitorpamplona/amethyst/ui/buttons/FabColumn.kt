package com.vitorpamplona.amethyst.ui.buttons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.NewPollView

@Composable
fun FabColumn(account: Account) {
    var isOpen by remember {
        mutableStateOf(false)
    }
    var wantsToPoll by remember {
        mutableStateOf(false)
    }
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    Column() {
        if (isOpen) {
            OutlinedButton(
                onClick = {
                    wantsToPoll = true
                    isOpen = false
                },
                modifier = Modifier.size(45.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_poll),
                    null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    wantsToPost = true
                    isOpen = false
                },
                modifier = Modifier.size(45.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = MaterialTheme.colors.primary),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lists),
                    null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.White
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
                painter = painterResource(R.drawable.ic_compose),
                null,
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }

    if (wantsToPost) {
        // NewPostView({ wantsToPost = false }, account = NostrAccountDataSource.account)
    }

    if (wantsToPoll) {
        NewPollView({ wantsToPoll = false }, account = account)
    }
}
