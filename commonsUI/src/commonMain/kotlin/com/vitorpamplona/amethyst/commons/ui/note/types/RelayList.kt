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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MyRelayList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.addRelayTo
import com.vitorpamplona.amethyst.commons.model.listsContaining
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.nip51Lists.relayLists.GenericRelayListCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserRelayIntoList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.auth_required
import com.vitorpamplona.amethyst.commons.resources.blocked_relays_title
import com.vitorpamplona.amethyst.commons.resources.broadcast_relays_title
import com.vitorpamplona.amethyst.commons.resources.dm_relays_title
import com.vitorpamplona.amethyst.commons.resources.favorite_relays_title
import com.vitorpamplona.amethyst.commons.resources.indexer_relays_title
import com.vitorpamplona.amethyst.commons.resources.keypackage_section
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_relays_hidden
import com.vitorpamplona.amethyst.commons.resources.kind_good_wiki_relays
import com.vitorpamplona.amethyst.commons.resources.nip51_list_has_private_members
import com.vitorpamplona.amethyst.commons.resources.paid_relay
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section
import com.vitorpamplona.amethyst.commons.resources.proxy_relays_title
import com.vitorpamplona.amethyst.commons.resources.public_home_section
import com.vitorpamplona.amethyst.commons.resources.public_notif_section
import com.vitorpamplona.amethyst.commons.resources.relay_list_add_to_my_relays
import com.vitorpamplona.amethyst.commons.resources.relay_list_in_my_relays
import com.vitorpamplona.amethyst.commons.resources.relay_list_section_count
import com.vitorpamplona.amethyst.commons.resources.relay_list_show_all
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.resources.search_relays_title
import com.vitorpamplona.amethyst.commons.resources.trusted_relays_title
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.hidesPrivateMembers
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.loadPrivateItems
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
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
import org.jetbrains.compose.resources.StringResource

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
 * The feed card of any relay-list kind: each section's first [COLLAPSED_RELAYS] relays, and one
 * button to [Route.RelayListMembers] when any section has more. The full list never renders inline:
 * a list can hold tens of thousands of relays, and a feed row that composed them all would crash.
 */
@Composable
fun DisplayRelayList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sections = observeRelayListSections(baseNote, baseNote.event, accountViewModel)

    Column(modifier = Modifier.fillMaxWidth()) {
        sections.forEachIndexed { index, section ->
            RelayListSectionHeader(
                section,
                Modifier.padding(start = 10.dp, end = 10.dp, top = if (index == 0) 5.dp else 15.dp, bottom = 5.dp),
            )

            section.relays.take(COLLAPSED_RELAYS).forEach { relay ->
                RelayListRow(relay, accountViewModel, nav)
            }
        }

        // One button for the whole card: the screen shows every section, and the headers already
        // carry the counts. It sits under the rows rather than fading over the last one.
        if (sections.any { it.relays.size > COLLAPSED_RELAYS }) {
            FilledTonalButton(
                onClick = { nav.nav(Route.RelayListMembers(baseNote.idHex)) },
                shape = ButtonBorder,
                contentPadding = ShowAllButtonPadding,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            ) {
                Text(stringRes(Res.string.relay_list_show_all))
                Spacer(Modifier.width(4.dp))
                Icon(MaterialSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val ShowAllButtonPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)

/** A section's name with its relay count beside it, and its description underneath when it has one. */
@Composable
fun RelayListSectionHeader(
    section: RelayListSection,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = pluralStringRes(Res.plurals.relay_list_section_count, section.relays.size, section.relays.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        section.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * One relay of a list, drawn like the relays everywhere else: its NIP-11 icon (a robohash until
 * one loads), its NIP-11 name with paid and auth badges, and its address underneath. Tapping it
 * opens the relay's page, holding it copies the address, and the trailing button says whether the
 * viewer already uses it.
 *
 * The NIP-11 document only loads while the row is composed, so a lazy list of thousands only asks
 * for the relays on screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayListRow(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relayInfo by loadRelayInfo(relay)
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val address = remember(relay) { relay.displayUrl() }
    val name = relayInfo.name?.trim()?.takeIf { it.isNotEmpty() } ?: address
    // The placeholder document names the relay by its address, so only a real name gets a second line.
    val subtitle = if (name != address) address else relayInfo.description?.trim()?.takeIf { it.isNotEmpty() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { nav.nav(Route.RelayInfo(relay.url)) },
                    onLongClick = { scope.launch { clipboardManager.setText(relay.url) } },
                ).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RenderRelayIcon(
            displayUrl = relayInfo.id ?: relay.url,
            iconUrl = relayInfo.icon,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            pingInMs = 0,
            iconModifier = MediumRelayIconModifier,
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (relayInfo.limitation?.payment_required == true) {
                    Icon(
                        symbol = MaterialSymbols.Paid,
                        contentDescription = stringRes(Res.string.paid_relay),
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.allGoodColor,
                    )
                }

                if (relayInfo.limitation?.auth_required == true) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        contentDescription = stringRes(Res.string.auth_required),
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        RelayMembershipAction(relay, accountViewModel, nav)
    }
}

/**
 * A check when the viewer already uses [relay], a plus when they don't. Either one opens a menu of
 * the viewer's relay lists: tapping a list adds the relay to it right away, lists that already hold
 * it are checked, and the last entry opens the relay settings for anything more.
 */
@Composable
private fun RelayMembershipAction(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isCurrentlyOnTheUsersList by observeUserRelayIntoList(relay, accountViewModel)
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        if (isCurrentlyOnTheUsersList) {
            FilledTonalIconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Check,
                    contentDescription = stringRes(Res.string.relay_list_in_my_relays),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            OutlinedIconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Add,
                    contentDescription = stringRes(Res.string.relay_list_add_to_my_relays),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (menuOpen) {
            AddRelayToListMenu(relay, accountViewModel, nav) { menuOpen = false }
        }
    }
}

@Composable
private fun AddRelayToListMenu(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    // Read once when the menu opens: the menu closes on every pick, so it never needs to update.
    val containing = remember(relay) { accountViewModel.account.listsContaining(relay) }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        MyRelayList.entries.forEach { list ->
            val alreadyThere = list in containing
            DropdownMenuItem(
                text = { Text(stringRes(list.title())) },
                enabled = !alreadyThere,
                leadingIcon = {
                    Icon(
                        symbol = if (alreadyThere) MaterialSymbols.Check else MaterialSymbols.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismiss()
                    accountViewModel.launchSigner { accountViewModel.account.addRelayTo(list, relay) }
                },
            )
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text(stringRes(Res.string.relay_settings)) },
            leadingIcon = {
                Icon(symbol = MaterialSymbols.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            onClick = {
                onDismiss()
                nav.nav(Route.EditRelays)
            },
        )
    }
}

private fun MyRelayList.title(): StringResource =
    when (this) {
        MyRelayList.OUTBOX -> Res.string.public_home_section
        MyRelayList.INBOX -> Res.string.public_notif_section
        MyRelayList.DM_INBOX -> Res.string.dm_relays_title
        MyRelayList.SEARCH -> Res.string.search_relays_title
        MyRelayList.RELAY_FEEDS -> Res.string.favorite_relays_title
        MyRelayList.INDEXER -> Res.string.indexer_relays_title
        MyRelayList.BROADCAST -> Res.string.broadcast_relays_title
        MyRelayList.PRIVATE_HOME -> Res.string.private_outbox_section
        MyRelayList.PROXY -> Res.string.proxy_relays_title
        MyRelayList.TRUSTED -> Res.string.trusted_relays_title
    }
