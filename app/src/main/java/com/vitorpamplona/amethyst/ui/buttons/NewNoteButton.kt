package com.vitorpamplona.amethyst.ui.buttons

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier

@Composable
fun NewNoteButton(accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    if (wantsToPost) {
        NewPostView({ wantsToPost = false }, accountViewModel = accountViewModel, nav = nav)
    }

    FloatingActionButton(
        onClick = { wantsToPost = true },
        modifier = Size55Modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_compose),
            null,
            modifier = Modifier.size(26.dp),
            tint = Color.White
        )
    }
}
