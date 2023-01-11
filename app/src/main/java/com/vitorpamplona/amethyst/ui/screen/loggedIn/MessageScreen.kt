package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun MessageScreen(accountViewModel: AccountViewModel) {
    val state = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Message Screen")
    }
}