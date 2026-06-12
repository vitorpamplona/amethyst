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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.apps.recommendations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.apps.recommendations.datasource.ProfileAppRecommendationsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ProfileAppRecommendationsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val myPubkey = accountViewModel.userProfile().pubkeyHex

    // Pull my kind 31989 events plus recent kind 31990 app definitions from
    // relays so the list below has candidates while this screen is open.
    ProfileAppRecommendationsFilterAssemblerSubscription(accountViewModel)

    // Ticks whenever LocalCache emits a bundle that touches my recommendations
    // or any app definition, so the snapshots below recompute.
    var recommendationsTick by remember { mutableIntStateOf(0) }
    var appDefinitionsTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(myPubkey) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { bundle ->
                var newRecommendations = false
                var newDefinitions = false
                bundle.forEach { note ->
                    val event = note.event
                    if (event is AppRecommendationEvent && event.pubKey == myPubkey) newRecommendations = true
                    if (event is AppDefinitionEvent) newDefinitions = true
                }
                if (newRecommendations) recommendationsTick++
                if (newDefinitions) appDefinitionsTick++
            }
        }
    }

    val recommendedAddresses =
        remember(recommendationsTick) {
            accountViewModel.account
                .myAppRecommendationEvents()
                .flatMapTo(mutableSetOf()) { event -> event.recommendations().map { it.address } }
        }

    // The recommended set used for ORDERING only. It tracks recommendedAddresses
    // while my 31989s stream in from relays, but freezes at the first toggle so
    // rows don't jump around mid-edit. The next visit re-sorts with fresh data.
    var userHasEdited by remember { mutableStateOf(false) }
    var pinnedRecommended by remember { mutableStateOf(setOf<Address>()) }
    LaunchedEffect(recommendedAddresses) {
        if (!userHasEdited) pinnedRecommended = recommendedAddresses
    }

    // Tracks the follow list as it loads (it may not be ready when the screen
    // opens), then freezes with the first toggle like pinnedRecommended.
    val followsState by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()
    var pinnedFollows by remember { mutableStateOf(setOf<HexKey>()) }
    LaunchedEffect(followsState) {
        if (!userHasEdited) pinnedFollows = followsState.authors
    }

    val apps =
        remember(appDefinitionsTick, pinnedRecommended, pinnedFollows) {
            LocalCache.addressables
                .filterIntoSet(AppDefinitionEvent.KIND) { _, note ->
                    val event = note.event as? AppDefinitionEvent ?: return@filterIntoSet false
                    // Unnamed apps are poor recommendation candidates; keep them
                    // only when already recommended, so they can be turned off.
                    note.address in pinnedRecommended ||
                        event
                            .appMetaData()
                            ?.anyName()
                            ?.isNotBlank() == true
                }.sortedWith(
                    // Apps I recommend on top, then apps authored by people I
                    // follow, then the rest; most recent first within each tier.
                    compareByDescending<AddressableNote> { it.address in pinnedRecommended }
                        .thenByDescending { it.address.pubKeyHex in pinnedFollows }
                        .thenByDescending { it.event?.createdAt ?: 0 },
                )
        }

    // Apps I recommend whose kind 31990 definition hasn't arrived yet: still
    // listed so they can be turned off, while EventFinder fetches the details.
    // Derived from the pinned set so a deselected row stays visible (switched
    // off) instead of vanishing mid-edit.
    val missingRecommended =
        remember(pinnedRecommended, apps) {
            val known = apps.mapTo(mutableSetOf()) { it.address }
            pinnedRecommended
                .filterNot { it in known }
                .map { LocalCache.getOrCreateAddressableNote(it) }
        }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.profile_app_recommendations_title), nav)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                text = stringRes(R.string.profile_app_recommendations_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            if (apps.isEmpty() && missingRecommended.isEmpty()) {
                Text(
                    text = stringRes(R.string.profile_app_recommendations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = missingRecommended + apps,
                        key = { it.idHex },
                    ) { appNote ->
                        AppRow(
                            appNote = appNote,
                            isRecommended = appNote.address in recommendedAddresses,
                            onUserEdited = { userHasEdited = true },
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    appNote: AddressableNote,
    isRecommended: Boolean,
    onUserEdited: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Ask relays for the definition only if we don't have it yet, then watch.
    // Subscribing for every visible row floods relays for nothing.
    if (appNote.event == null) {
        EventFinderFilterAssemblerSubscription(appNote, accountViewModel)
    }
    val definition by observeNoteEvent<AppDefinitionEvent>(appNote, accountViewModel)

    val metadata = definition?.appMetaData()
    val supportedKinds = definition?.supportedKinds() ?: emptyList()
    val canToggle = definition != null && (isRecommended || supportedKinds.isNotEmpty())

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    routeFor(appNote, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(definition)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata?.anyName()?.ifBlank { null } ?: stringRes(R.string.app_definition_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metadata?.about?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (definition != null && supportedKinds.isEmpty()) {
                Text(
                    text = stringRes(R.string.app_definition_no_supported_kinds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (supportedKinds.isNotEmpty()) {
                Text(
                    text = supportedKindsLabel(supportedKinds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Switch(
            checked = isRecommended,
            enabled = canToggle,
            onCheckedChange = { checked ->
                onUserEdited()
                accountViewModel.launchSigner {
                    if (checked) {
                        val event = definition ?: return@launchSigner
                        accountViewModel.account.recommendApp(event, appNote.relayHintUrl())
                    } else {
                        accountViewModel.account.unrecommendApp(appNote.address)
                    }
                }
            },
        )
    }
}

private const val VISIBLE_KIND_NAMES = 3

@Composable
private fun supportedKindsLabel(kinds: List<Int>): String {
    val names =
        kinds.take(VISIBLE_KIND_NAMES).map { kind ->
            val nameRes = kindDisplayName(kind)
            if (nameRes != -1) stringRes(nameRes) else "k$kind"
        }
    val overflow = kinds.size - VISIBLE_KIND_NAMES
    val suffix = if (overflow > 0) " +$overflow" else ""
    return stringRes(R.string.app_definition_handles) + ": " + names.joinToString(" · ") + suffix
}

@Composable
private fun AppLogo(definition: AppDefinitionEvent?) {
    val imageUrl = definition?.appMetaData()?.profilePicture()?.ifBlank { null }
    val logoModifier = Modifier.size(48.dp).clip(CircleShape)

    if (imageUrl.isNullOrBlank()) {
        RobohashAsyncImage(
            robot = definition?.id ?: "appnotfound",
            contentDescription = null,
            modifier = logoModifier,
            loadRobohash = true,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = logoModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
