package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.User

@Composable
fun ClickableUserTag(
    user: User,
    navController: NavController
) {
    val innerUserState by user.live().metadata.observeAsState()
    ClickableText(
        text = AnnotatedString("@${innerUserState?.user?.toBestDisplayName()} "),
        onClick = { navController.navigate("User/${innerUserState?.user?.pubkeyHex}") },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary)
    )
}
