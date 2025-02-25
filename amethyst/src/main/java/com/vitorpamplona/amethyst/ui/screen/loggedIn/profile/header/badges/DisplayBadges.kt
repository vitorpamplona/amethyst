/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.INav
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
            WatchAndRenderBadgeList(
                note = note,
                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                nav = nav,
            )
        }
    }
}

@Composable
private fun WatchAndRenderBadgeList(
    note: AddressableNote,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    nav: INav,
) {
    val badgeList by
        note
            .live()
            .metadata
            .map { (it.note.event as? BadgeProfilesEvent)?.badgeAwardEvents()?.toImmutableList() }
            .distinctUntilChanged()
            .observeAsState()

    badgeList?.let { list -> RenderBadgeList(list, loadProfilePicture, loadRobohash, nav) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RenderBadgeList(
    list: ImmutableList<ETag>,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    nav: INav,
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 5.dp),
    ) {
        list.forEach { badgeAwardEvent -> LoadAndRenderBadge(badgeAwardEvent, loadProfilePicture, loadRobohash, nav) }
    }
}

@Composable
private fun LoadAndRenderBadge(
    badgeAwardEvent: ETag,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
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

    baseNote?.let { ObserveAndRenderBadge(it, loadProfilePicture, loadRobohash, nav) }
}

@Composable
private fun ObserveAndRenderBadge(
    it: Note,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    nav: INav,
) {
    val badgeAwardState by it.live().metadata.observeAsState()
    val baseBadgeDefinition by
        remember(badgeAwardState) { derivedStateOf { badgeAwardState?.note?.replyTo?.firstOrNull() } }

    baseBadgeDefinition?.let { BadgeThumb(it, loadProfilePicture, loadRobohash, nav, Size35dp) }
}

@Composable
fun BadgeThumb(
    note: Note,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    nav: INav,
    size: Dp,
    pictureModifier: Modifier = Modifier,
) {
    BadgeThumb(note, loadProfilePicture, loadRobohash, size, pictureModifier) { nav.nav("Note/${note.idHex}") }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
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
        WatchAndRenderBadgeImage(baseNote, loadProfilePicture, loadRobohash, size, pictureModifier, onClick)
    }
}

@Composable
private fun WatchAndRenderBadgeImage(
    baseNote: Note,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    size: Dp,
    pictureModifier: Modifier,
    onClick: ((String) -> Unit)?,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val eventId = remember(noteState) { noteState?.note?.idHex } ?: return
    val image by
        remember(noteState) {
            derivedStateOf {
                val event = noteState?.note?.event as? BadgeDefinitionEvent
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
            loadRobohash = loadRobohash,
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
            loadProfilePicture = loadProfilePicture,
            loadRobohash = loadRobohash,
        )
    }
}
