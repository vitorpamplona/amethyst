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

import android.R.attr.onClick
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav.nav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BadgePictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

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
    val baseNote =
        produceState(
            LocalCache.getNoteIfExists(badgeAwardEvent),
            badgeAwardEvent,
        ) {
            val newValue = LocalCache.checkGetOrCreateNote(badgeAwardEvent)
            if (newValue != value) {
                value = newValue
            }
        }

    baseNote.value?.let {
        ObserveAndRenderBadge(it, accountViewModel, nav)
    }
}

@Composable
private fun ObserveAndRenderBadge(
    it: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val badgeAwardState by observeNoteEvent<BadgeAwardEvent>(it, accountViewModel)
    val badgeDefinitionId = badgeAwardState?.awardDefinition()?.firstOrNull()
    if (badgeDefinitionId != null) {
        LoadAddressableNote(badgeDefinitionId, accountViewModel) { badgeDefNote ->
            badgeDefNote?.let {
                BadgeThumb(it, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Box(
        modifier =
            Size35Modifier.clickable(
                onClick = {
                    nav.nav {
                        routeFor(baseNote, accountViewModel.account)
                    }
                },
            ),
    ) {
        WatchAndRenderBadgeImage(baseNote, accountViewModel)
    }
}

@Composable
private fun WatchAndRenderBadgeImage(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event by observeNoteEvent<BadgeDefinitionEvent>(baseNote, accountViewModel)

    event?.let {
        val image =
            remember(event) {
                event?.thumb()?.ifBlank { null } ?: event?.image()?.ifBlank { null }
            }
        RenderBadgeImage(it.id, it.name(), image, accountViewModel)
    }
}

@Composable
private fun RenderBadgeImage(
    id: String,
    name: String?,
    image: String?,
    accountViewModel: AccountViewModel,
) {
    val description =
        if (name != null) {
            stringRes(id = R.string.badge_award_image_for, name)
        } else {
            stringRes(id = R.string.badge_award_image)
        }

    if (image == null) {
        RobohashAsyncImage(
            robot = "badgenotfound",
            contentDescription = description,
            modifier = BadgePictureModifier,
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    } else {
        RobohashFallbackAsyncImage(
            robot = id,
            model = image,
            contentDescription = description,
            modifier = BadgePictureModifier,
            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    }
}
