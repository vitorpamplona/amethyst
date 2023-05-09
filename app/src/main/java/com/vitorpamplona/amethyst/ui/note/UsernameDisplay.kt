package com.vitorpamplona.amethyst.ui.note

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

@Composable
fun NoteUsernameDisplay(baseNote: Note, weight: Modifier = Modifier) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note ?: return

    val author = note.author

    if (author != null) {
        UsernameDisplay(author, weight)
    }
}

@Composable
fun UsernameDisplay(baseUser: User, weight: Modifier = Modifier) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val bestUserName = user.bestUsername()
    val bestDisplayName = user.bestDisplayName()
    val npubDisplay = user.pubkeyDisplayHex()

    UserNameDisplay(bestUserName, bestDisplayName, npubDisplay, weight)
}

@Composable
private fun UserNameDisplay(
    bestUserName: String?,
    bestDisplayName: String?,
    npubDisplay: String,
    modifier: Modifier
) {
    if (bestUserName != null && bestDisplayName != null) {
        Text(
            bestDisplayName,
            fontWeight = FontWeight.Bold
        )
        Text(
            "@$bestUserName",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else if (bestDisplayName != null) {
        Text(
            bestDisplayName,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else if (bestUserName != null) {
        Text(
            "@$bestUserName",
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else {
        Text(
            npubDisplay,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}
