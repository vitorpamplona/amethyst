package com.vitorpamplona.amethyst.ui.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent

@Composable
fun DisplayFollowingCommunityInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(HalfStartPadding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DisplayCommunity(baseNote, nav)
        }
    }
}

@Composable
private fun DisplayCommunity(note: Note, nav: (String) -> Unit) {
    val communityTag = remember(note) {
        note.event?.getTagOfAddressableKind(CommunityDefinitionEvent.kind)
    } ?: return

    val displayTag = remember(note) { AnnotatedString(getCommunityShortName(communityTag)) }
    val route = remember(note) { "Community/${communityTag.toTag()}" }

    ClickableText(
        text = displayTag,
        onClick = { nav(route) },
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.52f
            )
        ),
        maxLines = 1
    )
}

private fun getCommunityShortName(communityTag: ATag): String {
    val name = if (communityTag.dTag.length > 10) {
        communityTag.dTag.take(10) + "..."
    } else {
        communityTag.dTag.take(10)
    }

    return "/n/$name"
}
