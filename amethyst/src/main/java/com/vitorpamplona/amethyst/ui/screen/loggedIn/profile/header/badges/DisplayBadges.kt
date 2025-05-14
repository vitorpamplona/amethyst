/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.badges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DisplayBadges(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(
        BadgeProfilesEvent.createAddress(baseUser.pubkeyHex),
        accountViewModel,
    ) { note ->
        if (note != null) {
            WatchAndRenderBadgeList(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun WatchAndRenderBadgeList(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val badgeList by observeNoteEventAndMap(note, accountViewModel) { event: BadgeProfilesEvent ->
        event.badgeAwardEvents().toImmutableList()
    }

    badgeList?.let { list -> RenderBadgeList(list, accountViewModel, nav) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RenderBadgeList(
    list: ImmutableList<ETag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 5.dp),
    ) {
        list.forEach { badgeAwardEvent -> LoadAndRenderBadge(badgeAwardEvent, accountViewModel, nav) }
    }
}

@Composable
private fun LoadAndRenderBadge(
    badgeAwardEvent: ETag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var baseNote by remember(badgeAwardEvent) { mutableStateOf(LocalCache.getNoteIfExists(badgeAwardEvent)) }

    LaunchedEffect(key1 = badgeAwardEvent) {
        if (baseNote == null) {
            withContext(Dispatchers.IO) {
                baseNote = LocalCache.checkGetOrCreateNote(badgeAwardEvent)
            }
        }
    }

    baseNote?.let { ObserveAndRenderBadge(it, accountViewModel, nav) }
}

@Composable
private fun ObserveAndRenderBadge(
    it: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val badgeAwardState by observeNote(it, accountViewModel)
    val baseBadgeDefinition = badgeAwardState.note.replyTo?.firstOrNull()
    baseBadgeDefinition?.let { BadgeThumb(it, accountViewModel, nav, Size35dp) }
}

@Composable
fun BadgeThumb(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    size: Dp,
    pictureModifier: Modifier = Modifier,
) {
    BadgeThumb(note, accountViewModel, size, pictureModifier) { nav.nav(Route.Note(note.idHex)) }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null,
) {
    Box(
        remember {
            Modifier
                .width(size)
                .height(size)
        },
    ) {
        WatchAndRenderBadgeImage(baseNote, size, pictureModifier, accountViewModel, onClick)
    }
}

@Composable
private fun WatchAndRenderBadgeImage(
    baseNote: Note,
    size: Dp,
    pictureModifier: Modifier,
    accountViewModel: AccountViewModel,
    onClick: ((String) -> Unit)?,
) {
    val noteState by observeNote(baseNote, accountViewModel)
    val eventId = remember(noteState) { noteState?.note?.idHex } ?: return
    val image by
        remember(noteState) {
            derivedStateOf {
                val event = noteState.note.event as? BadgeDefinitionEvent
                event?.thumb()?.ifBlank { null } ?: event?.image()?.ifBlank { null }
            }
        }

    if (image == null) {
        RobohashAsyncImage(
            robot = "authornotfound",
            contentDescription = stringRes(R.string.unknown_author),
            modifier =
                remember {
                    pictureModifier
                        .width(size)
                        .height(size)
                },
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    } else {
        RobohashFallbackAsyncImage(
            robot = eventId,
            model = image!!,
            contentDescription = stringRes(id = R.string.profile_image),
            modifier =
                remember {
                    pictureModifier
                        .width(size)
                        .height(size)
                        .clip(shape = CutCornerShape(20))
                        .run {
                            if (onClick != null) {
                                this.clickable(onClick = { onClick(eventId) })
                            } else {
                                this
                            }
                        }
                },
            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    }
}
