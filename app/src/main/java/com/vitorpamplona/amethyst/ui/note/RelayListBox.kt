package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconButtonModifier
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
public fun RelayBadges(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showShowMore by remember { mutableStateOf(false) }

    var lazyRelayList by remember {
        val baseNumber = baseNote.relays.map {
            it.removePrefix("wss://").removePrefix("ws://")
        }.toImmutableList()

        mutableStateOf(baseNumber)
    }
    var shortRelayList by remember {
        mutableStateOf(lazyRelayList.take(3).toImmutableList())
    }

    val scope = rememberCoroutineScope()

    WatchRelayLists(baseNote) { relayList ->
        if (!equalImmutableLists(relayList, lazyRelayList)) {
            scope.launch(Dispatchers.Main) {
                lazyRelayList = relayList
                shortRelayList = relayList.take(3).toImmutableList()
            }
        }

        val nextShowMore = relayList.size > 3
        if (nextShowMore != showShowMore) {
            scope.launch(Dispatchers.Main) {
                // only triggers recomposition when actually different
                showShowMore = nextShowMore
            }
        }
    }

    Spacer(DoubleVertSpacer)

    if (expanded) {
        VerticalRelayPanelWithFlow(lazyRelayList, accountViewModel, nav)
    } else {
        VerticalRelayPanelWithFlow(shortRelayList, accountViewModel, nav)
    }

    if (showShowMore && !expanded) {
        ShowMoreRelaysButton {
            expanded = true
        }
    }
}

@Composable
private fun WatchRelayLists(baseNote: Note, onListChanges: (ImmutableList<String>) -> Unit) {
    val noteRelaysState by baseNote.live().relays.observeAsState()

    LaunchedEffect(key1 = noteRelaysState) {
        launch(Dispatchers.IO) {
            val relayList = noteRelaysState?.note?.relays?.map {
                it.removePrefix("wss://").removePrefix("ws://")
            } ?: emptyList()

            onListChanges(relayList.toImmutableList())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Stable
private fun VerticalRelayPanelWithFlow(
    relays: ImmutableList<String>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    // FlowRow Seems to be a lot faster than LazyVerticalGrid
    FlowRow() {
        relays.forEach { url ->
            RenderRelay(url, accountViewModel, nav)
        }
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    Row(
        modifier = ShowMoreRelaysButtonBoxModifer,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            modifier = ShowMoreRelaysButtonIconButtonModifier,
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = ShowMoreRelaysButtonIconModifier,
                tint = MaterialTheme.colors.placeholderText
            )
        }
    }
}
