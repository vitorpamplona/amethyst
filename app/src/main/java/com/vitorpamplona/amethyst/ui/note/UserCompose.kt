package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@Composable
fun UserCompose(
    baseUser: User,
    overallModifier: Modifier = StdPadding,
    showDiviser: Boolean = true,
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
            UserPicture(baseUser, Size55dp, accountViewModel = accountViewModel, nav = nav)

            Column(modifier = remember { Modifier.padding(start = 10.dp).weight(1f) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                AboutDisplay(baseUser)
            }

            Column(modifier = remember { Modifier.padding(start = 10.dp) }) {
                UserActionOptions(baseUser, accountViewModel)
            }
        }

        if (showDiviser) {
            Divider(
                thickness = DividerThickness
            )
        }
    }
}
