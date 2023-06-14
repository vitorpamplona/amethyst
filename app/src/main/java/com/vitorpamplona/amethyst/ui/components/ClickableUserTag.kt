package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.model.User

@Composable
fun ClickableUserTag(
    user: User,
    nav: (String) -> Unit
) {
    val route = remember {
        "User/${user.pubkeyHex}"
    }

    val innerUserState by user.live().metadata.observeAsState()

    val userName = remember(innerUserState) {
        AnnotatedString("@${innerUserState?.user?.toBestDisplayName()}")
    }

    ClickableText(
        text = userName,
        onClick = { nav(route) },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}
