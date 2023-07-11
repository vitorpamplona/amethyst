package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.ui.actions.RelayInformationDialog
import com.vitorpamplona.amethyst.ui.actions.loadRelayInfo
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.ui.theme.Size13dp
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
public fun RelayBadgesHorizontal(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val expanded = remember { mutableStateOf(false) }

    RenderRelayList(baseNote, expanded, accountViewModel, nav)

    RenderExpandButton(baseNote, expanded) {
        ChatRelayExpandButton { expanded.value = true }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderRelayList(baseNote: Note, expanded: MutableState<Boolean>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteRelays by baseNote.live().relays.map {
        it.note.relays
    }.observeAsState(baseNote.relays)

    FlowRow(StdStartPadding) {
        val relaysToDisplay = remember(noteRelays, expanded.value) {
            if (expanded.value) noteRelays else noteRelays.take(3)
        }
        relaysToDisplay.forEach {
            RenderRelay(it, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderExpandButton(
    baseNote: Note,
    expanded: MutableState<Boolean>,
    content: @Composable () -> Unit
) {
    val showExpandButton by baseNote.live().relays.map {
        it.note.relays.size > 3
    }.observeAsState(baseNote.relays.size > 3)

    if (showExpandButton && !expanded.value) {
        content()
    }
}

@Composable
fun ChatRelayExpandButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size15Modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            null,
            modifier = Size15Modifier,
            tint = MaterialTheme.colors.placeholderText
        )
    }
}

@Composable
fun RenderRelay(dirtyUrl: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val iconUrl by remember(dirtyUrl) {
        derivedStateOf {
            val cleanUrl = dirtyUrl.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/")
            "https://$cleanUrl/favicon.ico"
        }
    }

    var relayInfo: RelayInformation? by remember { mutableStateOf(null) }

    if (relayInfo != null) {
        RelayInformationDialog(
            onClose = {
                relayInfo = null
            },
            relayInfo = relayInfo!!,
            accountViewModel,
            nav
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false, radius = Size15dp)

    val clickableModifier = remember(dirtyUrl) {
        Modifier
            .padding(1.dp)
            .size(Size15dp)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple,
                onClick = {
                    loadRelayInfo(dirtyUrl, context, scope) {
                        relayInfo = it
                    }
                }
            )
    }

    Box(
        modifier = clickableModifier
    ) {
        RenderRelayIcon(iconUrl)
    }
}

@Composable
private fun RenderRelayIcon(iconUrl: String) {
    val backgroundColor = MaterialTheme.colors.background

    val iconModifier = remember {
        Modifier
            .size(Size13dp)
            .clip(shape = CircleShape)
            .background(backgroundColor)
    }

    RobohashFallbackAsyncImage(
        robot = iconUrl,
        model = iconUrl,
        contentDescription = stringResource(id = R.string.relay_icon),
        colorFilter = RelayIconFilter,
        modifier = iconModifier
    )
}
