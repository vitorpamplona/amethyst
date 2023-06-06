package com.vitorpamplona.amethyst.ui.note

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji

@Composable
fun NoteUsernameDisplay(baseNote: Note, weight: Modifier = Modifier) {
    val noteState by baseNote.live().metadata.observeAsState()
    val author = remember(noteState) {
        noteState?.note?.author
    } ?: return

    UsernameDisplay(author, weight)
}

@Composable
fun UsernameDisplay(baseUser: User, weight: Modifier = Modifier) {
    val userState by baseUser.live().metadata.observeAsState()
    val bestUserName = remember(userState) { userState?.user?.bestUsername() }
    val bestDisplayName = remember(userState) { userState?.user?.bestDisplayName() }
    val npubDisplay = remember { baseUser.pubkeyDisplayHex() }
    val tags = remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

    UserNameDisplay(bestUserName, bestDisplayName, npubDisplay, tags, weight)
}

@Composable
private fun UserNameDisplay(
    bestUserName: String?,
    bestDisplayName: String?,
    npubDisplay: String,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier
) {
    if (bestUserName != null && bestDisplayName != null) {
        CreateTextWithEmoji(
            text = bestDisplayName,
            tags = tags,
            fontWeight = FontWeight.Bold
        )
        CreateTextWithEmoji(
            text = remember { "@$bestUserName" },
            tags = tags,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else if (bestDisplayName != null) {
        CreateTextWithEmoji(
            text = bestDisplayName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else if (bestUserName != null) {
        CreateTextWithEmoji(
            text = remember { "@$bestUserName" },
            tags = tags,
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
