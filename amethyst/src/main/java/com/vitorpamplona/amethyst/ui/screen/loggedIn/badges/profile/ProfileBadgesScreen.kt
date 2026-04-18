/*
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent

@Composable
fun ProfileBadgesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val myPubkey = accountViewModel.userProfile().pubkeyHex

    val newNote = accountViewModel.getOrCreateAddressableNote(ProfileBadgesEvent.createAddress(myPubkey))
    val oldNote = accountViewModel.getOrCreateAddressableNote(AcceptedBadgeSetEvent.createAddress(myPubkey))

    val newState by newNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val oldState by oldNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    val acceptedAwardIds =
        remember(newState, oldState) {
            val newEvent = newState.note.event as? ProfileBadgesEvent
            val oldEvent = oldState.note.event as? AcceptedBadgeSetEvent
            (newEvent?.badgeAwardEvents()?.map { it.eventId } ?: oldEvent?.badgeAwardEvents()?.map { it.eventId } ?: emptyList())
                .toSet()
        }

    val receivedAwards =
        remember(myPubkey, newState, oldState) {
            LocalCache.notes
                .filterIntoSet { _, it ->
                    val event = it.event
                    event is BadgeAwardEvent && event.awardeeIds().contains(myPubkey)
                }.mapNotNull { it.event as? BadgeAwardEvent }
                .sortedByDescending { it.createdAt }
        }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.profile_badges_title), nav::popBack)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                text = stringRes(R.string.profile_badges_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            if (receivedAwards.isEmpty()) {
                Text(
                    text = stringRes(R.string.profile_badges_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = receivedAwards,
                        key = { it.id },
                    ) { award ->
                        AwardRow(
                            award = award,
                            isAccepted = acceptedAwardIds.contains(award.id),
                            accountViewModel = accountViewModel,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AwardRow(
    award: BadgeAwardEvent,
    isAccepted: Boolean,
    accountViewModel: AccountViewModel,
) {
    val defAddr = award.awardDefinition().firstOrNull()
    val definition =
        remember(award.id) {
            defAddr?.let { LocalCache.getAddressableNoteIfExists(it)?.event as? BadgeDefinitionEvent }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeThumb(definition)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = definition?.name()?.ifBlank { null } ?: stringRes(R.string.badge_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            definition?.description()?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Switch(
            checked = isAccepted,
            onCheckedChange = { checked ->
                accountViewModel.launchSigner {
                    if (checked) {
                        val defEvent = definition ?: return@launchSigner
                        accountViewModel.account.addAcceptedBadge(award, defEvent)
                    } else {
                        accountViewModel.account.removeAcceptedBadge(award)
                    }
                }
            },
        )
    }
}

@Composable
private fun BadgeThumb(definition: BadgeDefinitionEvent?) {
    val imageUrl = definition?.thumb()?.ifBlank { null } ?: definition?.image()?.ifBlank { null }
    val thumbModifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))

    if (imageUrl.isNullOrBlank()) {
        RobohashAsyncImage(
            robot = definition?.id ?: "badgenotfound",
            contentDescription = null,
            modifier = thumbModifier,
            loadRobohash = true,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = thumbModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
