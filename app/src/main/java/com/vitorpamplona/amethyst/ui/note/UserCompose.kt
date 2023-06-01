package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun UserCompose(
    baseUser: User,
    overallModifier: Modifier = Modifier
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp
        ),
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(
        modifier =
        Modifier.clickable(
            onClick = { nav("User/${baseUser.pubkeyHex}") }
        )
    ) {
        Row(
            modifier = overallModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserPicture(baseUser, nav, accountViewModel, 55.dp)

            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                AboutDisplay(baseUser)
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                UserActionOptions(baseUser, accountViewModel)
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}
