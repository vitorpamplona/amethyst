package com.vitorpamplona.amethyst.ui.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun NewNoteButton(account: Account, accountViewModel: AccountViewModel, navController: NavController) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    if (wantsToPost) {
        NewPostView({ wantsToPost = false }, account = account, accountViewModel = accountViewModel, navController = navController)
    }

    OutlinedButton(
        onClick = { wantsToPost = true },
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
