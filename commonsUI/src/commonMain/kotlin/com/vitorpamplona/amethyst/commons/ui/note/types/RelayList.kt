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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip51Lists.relayLists.GenericRelayListCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserRelayIntoList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blocked_relays_title
import com.vitorpamplona.amethyst.commons.resources.broadcast_relays_title
import com.vitorpamplona.amethyst.commons.resources.dm_relays_title
import com.vitorpamplona.amethyst.commons.resources.favorite_relays_title
import com.vitorpamplona.amethyst.commons.resources.indexer_relays_title
import com.vitorpamplona.amethyst.commons.resources.keypackage_section
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_relays_hidden
import com.vitorpamplona.amethyst.commons.resources.kind_good_wiki_relays
import com.vitorpamplona.amethyst.commons.resources.nip51_list_has_private_members
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section
import com.vitorpamplona.amethyst.commons.resources.proxy_relays_title
import com.vitorpamplona.amethyst.commons.resources.public_home_section
import com.vitorpamplona.amethyst.commons.resources.public_notif_section
import com.vitorpamplona.amethyst.commons.resources.relay_list_show_all
import com.vitorpamplona.amethyst.commons.resources.search_relays_title
import com.vitorpamplona.amethyst.commons.resources.trusted_relays_title
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.AddRelayButton
import com.vitorpamplona.amethyst.commons.ui.note.RemoveRelayButton
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.hidesPrivateMembers
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.loadPrivateItems
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueFor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiRelayList.GoodWikiRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.launch

/** How many relays a feed card shows before it points to [Route.RelayListMembers] for the rest. */
private const val COLLAPSED_RELAYS = 3

/**
 * One named group of relays inside a relay-list event. Most kinds carry one; NIP-65 carries two
 * (outbox and inbox).
 */
@Immutable
class RelayListSection(
    val title: String,
    val description: String?,
    val relays: List<NormalizedRelayUrl>,
)

/**
 * The relays [event] lists, grouped as the card and [Route.RelayListMembers] show them. Private
 * halves are decrypted when the viewer is the author. Empty for an event that is not a relay list.
 *
 * Shared by the feed card and the full screen so both always agree on what a list contains.
 */
@Composable
fun observeRelayListSections(
    baseNote: Note,
    event: Event?,
    accountViewModel: AccountViewModel,
): List<RelayListSection> {
    val account = accountViewModel.account
    return when (event) {
        is RelaySetEvent -> {
            val title = remember(event) { event.tags.firstTagValueFor("title", "name") ?: "#${event.dTag()}" }
            remember(event) { listOf(RelayListSection(title, event.description(), event.relays())) }
        }

        is DmRelayListEvent -> {
            singleSection(event, stringRes(Res.string.dm_relays_title), null) { event.relays() }
        }

        is AdvertisedRelayListEvent -> {
            val outbox = stringRes(Res.string.public_home_section)
            val inbox = stringRes(Res.string.public_notif_section)
            remember(event, outbox, inbox) {
                listOf(
                    RelayListSection(outbox, null, event.writeRelaysNorm() ?: emptyList()),
                    RelayListSection(inbox, null, event.readRelaysNorm() ?: emptyList()),
                )
            }
        }

        is SearchRelayListEvent -> decryptedSection(baseNote, account.searchRelayListDecryptionCache, stringRes(Res.string.search_relays_title))
        is BlockedRelayListEvent -> decryptedSection(baseNote, account.blockedRelayListDecryptionCache, stringRes(Res.string.blocked_relays_title))
        is TrustedRelayListEvent -> decryptedSection(baseNote, account.trustedRelayListDecryptionCache, stringRes(Res.string.trusted_relays_title))
        is FavoriteRelayListEvent -> decryptedSection(baseNote, account.favoriteRelayListDecryptionCache, stringRes(Res.string.favorite_relays_title))
        is IndexerRelayListEvent -> decryptedSection(baseNote, account.indexerRelayListDecryptionCache, stringRes(Res.string.indexer_relays_title))
        is ProxyRelayListEvent -> decryptedSection(baseNote, account.proxyRelayListDecryptionCache, stringRes(Res.string.proxy_relays_title))
        is BroadcastRelayListEvent -> decryptedSection(baseNote, account.broadcastRelayListDecryptionCache, stringRes(Res.string.broadcast_relays_title))

        is KeyPackageRelayListEvent -> {
            singleSection(event, stringRes(Res.string.keypackage_section), null) { event.relays() }
        }

        is PrivateOutboxRelayListEvent -> {
            // The private half stays encrypted and is only noted.
            val hidden = if (event.content.isNotEmpty()) stringRes(Res.string.kind_card_private_relays_hidden) else null
            singleSection(event, stringRes(Res.string.private_outbox_section), hidden) { event.publicRelays() }
        }

        is GoodWikiRelayListEvent -> {
            val private by loadPrivateItems(event, accountViewModel) { event.privateRelays(it) }
            val title = stringRes(Res.string.kind_good_wiki_relays)
            // Only said when there is nothing else to show: an all-private list should read as closed, not broken.
            val hidden = stringRes(Res.string.nip51_list_has_private_members)
            remember(event, private, title, hidden) {
                val all = private?.let { event.publicRelays() + it } ?: event.publicRelays()
                val description = if (event.hidesPrivateMembers(private) && all.isEmpty()) hidden else null
                listOf(RelayListSection(title, description, all))
            }
        }

        else -> {
            emptyList()
        }
    }
}

@Composable
private fun singleSection(
    event: Event,
    title: String,
    description: String?,
    relays: () -> List<NormalizedRelayUrl>,
): List<RelayListSection> = remember(event, title, description) { listOf(RelayListSection(title, description, relays())) }

@Composable
private fun decryptedSection(
    baseNote: Note,
    cache: GenericRelayListCache<*>,
    title: String,
): List<RelayListSection> {
    val flow = remember(baseNote, cache) { cache.observeDecryptedRelayList(baseNote) }
    val relays by flow.collectAsStateWithLifecycle(remember(baseNote, cache) { cache.fastStartValueForRelayList(baseNote) })
    return remember(relays, title) { listOf(RelayListSection(title, null, relays.relays)) }
}

/**
 * The feed card of any relay-list kind: each section's first [COLLAPSED_RELAYS] relays, and a
 * button to [Route.RelayListMembers] when there are more. The full list never renders inline: a
 * list can hold tens of thousands of relays, and a feed row that composed them all would crash.
 */
@Composable
fun DisplayRelayList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sections = observeRelayListSections(baseNote, baseNote.event, accountViewModel)

    sections.forEach { section ->
        DisplayRelaySetPreview(section, backgroundColor, accountViewModel, nav) {
            nav.nav(Route.RelayListMembers(baseNote.idHex))
        }
    }
}

@Composable
private fun DisplayRelaySetPreview(
    section: RelayListSection,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onShowAll: () -> Unit,
) {
    RelayListSectionHeader(section)

    Box {
        Column(modifier = Modifier.padding(top = 5.dp)) {
            section.relays.take(COLLAPSED_RELAYS).forEach { relay ->
                RelayListRow(relay, accountViewModel, nav, Modifier.padding(bottom = 5.dp))
            }
        }

        if (section.relays.size > COLLAPSED_RELAYS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton(
                    text = pluralStringRes(Res.plurals.relay_list_show_all, section.relays.size, section.relays.size),
                    onClick = onShowAll,
                )
            }
        }
    }
}

/** A section's name and, when it has one, its description, centered above its relays. */
@Composable
fun RelayListSectionHeader(section: RelayListSection) {
    Text(
        text = section.title,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
        textAlign = TextAlign.Center,
    )

    section.description?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
        )
    }
}

/** One relay of a list: its address and a button to add it to, or manage it in, the viewer's relays. */
@Composable
fun RelayListRow(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
    textModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = relay.displayUrl(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                textModifier
                    .padding(start = 10.dp)
                    .weight(1f),
        )

        Column(modifier = Modifier.padding(start = 10.dp)) {
            RelayOptionsAction(relay, accountViewModel, nav)
        }
    }
}

@Composable
private fun RelayOptionsAction(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isCurrentlyOnTheUsersList by observeUserRelayIntoList(relay, accountViewModel)
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    if (isCurrentlyOnTheUsersList) {
        RemoveRelayButton {
            nav.nav(Route.EditRelays)
        }
    } else {
        AddRelayButton {
            scope.launch {
                clipboardManager.setText(relay.url)
                nav.nav(Route.EditRelays)
            }
        }
    }
}
