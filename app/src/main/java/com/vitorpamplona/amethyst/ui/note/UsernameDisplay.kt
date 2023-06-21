package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.LifecycleOwner
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.tts.TextToSpeechHelper
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.theme.StdButtonSizeModifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText

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
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (bestDisplayName != bestUserName) {
            CreateTextWithEmoji(
                text = remember { "@$bestUserName" },
                tags = tags,
                color = MaterialTheme.colors.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        Spacer(StdHorzSpacer)
        DrawPlayName(bestDisplayName)
    } else if (bestDisplayName != null) {
        CreateTextWithEmoji(
            text = bestDisplayName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        Spacer(StdHorzSpacer)
        DrawPlayName(bestDisplayName)
    } else if (bestUserName != null) {
        CreateTextWithEmoji(
            text = bestUserName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        Spacer(StdHorzSpacer)
        DrawPlayName(bestUserName)
    } else {
        Text(
            text = npubDisplay,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
fun DrawPlayName(name: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DrawPlayNameIcon {
        speak(name, context, lifecycleOwner)
    }
}

@Composable
fun DrawPlayNameIcon(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = StdButtonSizeModifier
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            modifier = StdButtonSizeModifier,
            tint = MaterialTheme.colors.placeholderText
        )
    }
}

private fun speak(
    message: String,
    context: Context,
    owner: LifecycleOwner
) {
    TextToSpeechHelper
        .getInstance(context)
        .registerLifecycle(owner)
        .speak(message)
        .highlight()
        .onDone {
            Log.d("TextToSpeak", "speak: done")
        }
        .onError {
            Log.d("TextToSpeak", "speak error: $it")
        }
}
