package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent

@Composable
fun LoadRedirectScreen(eventId: String?, navController: NavController) {
    if (eventId == null) return

    val baseNote = LocalCache.checkGetOrCreateNote(eventId) ?: return

    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    LaunchedEffect(key1 = noteState) {
        val event = note?.event
        val channelHex = note?.channelHex()

        if (event == null) {
            // stay here, loading
        } else if (event is ChannelCreateEvent) {
            navController.backQueue.removeLast()
            navController.navigate("Channel/${note.idHex}")
        } else if (event is PrivateDmEvent) {
            navController.backQueue.removeLast()
            navController.navigate("Room/${note.author?.pubkeyHex}")
        } else if (channelHex != null) {
            navController.backQueue.removeLast()
            navController.navigate("Channel/$channelHex")
        } else {
            navController.backQueue.removeLast()
            navController.navigate("Note/${note.idHex}")
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(horizontal = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.looking_for_event, eventId))
    }
}
