package com.vitorpamplona.amethyst.ui.note

import android.content.Context
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.tts.TextToSpeechHelper
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.theme.StdButtonSizeModifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NoteUsernameDisplay(baseNote: Note, weight: Modifier = Modifier, showPlayButton: Boolean = true) {
    val authorState by baseNote.live().metadata.map {
        it.note.author
    }.observeAsState(baseNote.author)

    Crossfade(targetState = authorState, modifier = weight) {
        it?.let {
            UsernameDisplay(it, weight, showPlayButton)
        }
    }
}

@Composable
fun UsernameDisplay(baseUser: User, weight: Modifier = Modifier, showPlayButton: Boolean = true) {
    val npubDisplay by remember {
        derivedStateOf {
            baseUser.pubkeyDisplayHex()
        }
    }

    val userMetadata by baseUser.live().metadata.map {
        it.user.info
    }.observeAsState(baseUser.info)

    Crossfade(targetState = userMetadata, modifier = weight) {
        if (it != null) {
            UserNameDisplay(it.bestUsername(), it.bestDisplayName(), npubDisplay, it.tags, weight, showPlayButton)
        } else {
            NPubDisplay(npubDisplay, weight)
        }
    }
}

@Composable
private fun UserNameDisplay(
    bestUserName: String?,
    bestDisplayName: String?,
    npubDisplay: String,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier,
    showPlayButton: Boolean = true
) {
    if (bestUserName != null && bestDisplayName != null && bestDisplayName != bestUserName) {
        UserAndUsernameDisplay(bestDisplayName, tags, bestUserName, modifier, showPlayButton)
    } else if (bestDisplayName != null) {
        UserDisplay(bestDisplayName, tags, modifier, showPlayButton)
    } else if (bestUserName != null) {
        UserDisplay(bestUserName, tags, modifier, showPlayButton)
    } else {
        NPubDisplay(npubDisplay, modifier)
    }
}

@Composable
fun NPubDisplay(npubDisplay: String, modifier: Modifier) {
    Text(
        text = npubDisplay,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun UserDisplay(
    bestDisplayName: String,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier,
    showPlayButton: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CreateTextWithEmoji(
            text = bestDisplayName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        if (showPlayButton) {
            Spacer(StdHorzSpacer)
            DrawPlayName(bestDisplayName)
        }
    }
}

@Composable
private fun UserAndUsernameDisplay(
    bestDisplayName: String,
    tags: ImmutableListOfLists<String>?,
    bestUserName: String,
    modifier: Modifier,
    showPlayButton: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CreateTextWithEmoji(
            text = bestDisplayName,
            tags = tags,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        CreateTextWithEmoji(
            text = remember { "@$bestUserName" },
            tags = tags,
            color = MaterialTheme.colors.placeholderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        if (showPlayButton) {
            Spacer(StdHorzSpacer)
            DrawPlayName(bestDisplayName)
        }
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
    IconButton(onClick = onClick, modifier = StdButtonSizeModifier) {
        PlayIcon(modifier = StdButtonSizeModifier, tint = MaterialTheme.colors.placeholderText)
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
