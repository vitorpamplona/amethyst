package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayBriefInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconButtonModifier
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RelayBadges(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val relayList by baseNote.live().relayInfo.observeAsState(persistentListOf())

    Spacer(DoubleVertSpacer)

    if (expanded) {
        VerticalRelayPanelWithFlow(relayList, accountViewModel, nav)
    } else {
        val shortRelayList by remember {
            derivedStateOf {
                relayList.take(3).toImmutableList()
            }
        }

        VerticalRelayPanelWithFlow(shortRelayList, accountViewModel, nav)
    }

    if (relayList.size > 3 && !expanded) {
        ShowMoreRelaysButton {
            expanded = true
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Stable
private fun VerticalRelayPanelWithFlow(
    relays: ImmutableList<RelayBriefInfo>,
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
                tint = MaterialTheme.colorScheme.placeholderText
            )
        }
    }
}
