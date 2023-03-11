package com.vitorpamplona.amethyst.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Account

@Composable
fun FabColumn(account: Account) {
    Column() {
        NewPollButton(account)
        Spacer(modifier = Modifier.height(20.dp))
        NewNoteButton(account)
    }
}
