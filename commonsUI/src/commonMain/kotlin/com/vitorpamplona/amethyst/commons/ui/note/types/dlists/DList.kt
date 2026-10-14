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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.dlists.DLIST_ITEMS_LIMIT
import com.vitorpamplona.amethyst.commons.relayClient.dlists.DListItemsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.dlists.dListItemKinds
import com.vitorpamplona.amethyst.commons.relayClient.dlists.dListItemPointers
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.dlist_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_item_in
import com.vitorpamplona.amethyst.commons.resources.dlist_items
import com.vitorpamplona.amethyst.commons.resources.dlist_items_count
import com.vitorpamplona.amethyst.commons.resources.dlist_items_count_more
import com.vitorpamplona.amethyst.commons.resources.dlist_list_of
import com.vitorpamplona.amethyst.commons.resources.dlist_missing_fields
import com.vitorpamplona.amethyst.commons.resources.dlist_unaffiliated
import com.vitorpamplona.amethyst.commons.resources.dlist_untitled_item
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.AddressMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.BookmarkMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.PagedMemberColumn
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.CappedChips
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TonalChip
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustCardFrame
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustSectionLabel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldRequirement
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemAddressIds
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemEventIds
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemKeys
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.itemStrings
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.parentLists
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentList
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.flow.map

/** Item previews a header card shows before "open the list". */
private const val PREVIEW_ITEMS = 3

/** Facts an item card shows before it asks to be expanded. */
private const val COLLAPSED_FACTS = 6

private val PictureShape = RoundedCornerShape(12.dp)

/** Field names on a header, or words on an item, shown before a "+N more" chip. */
private const val MAX_CHIPS = 16

/** What a D-list item lists, people first, then notes, then addresses, as the card draws them. */
@Immutable
private sealed interface DListMember {
    @Immutable
    class Person(
        val pubKey: HexKey,
    ) : DListMember

    @Immutable
    class NoteId(
        val id: HexKey,
    ) : DListMember

    @Immutable
    class Coordinate(
        val address: Address,
    ) : DListMember
}

private fun dListItemMembers(event: Event): List<DListMember> =
    buildList {
        event.tags.itemKeys().forEach { add(DListMember.Person(it)) }
        event.tags.itemEventIds().forEach { add(DListMember.NoteId(it)) }
        event.tags.itemAddressIds().forEach { id -> Address.parse(id)?.let { add(DListMember.Coordinate(it)) } }
    }

private fun Event.dTagOrNull() = (this as? AddressableEvent)?.dTag()

/**
 * A Decentralized List header (kinds 9998 / 39998): its picture, its names, what it holds, the
 * fields each item carries, and how many items relays have filed under it, with the first few.
 * Mirrors Brainstorm's `DListHeaderHero`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderDListHeader(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event ?: return
    val header = remember(event) { dListHeaderView(event.tags, event.dTagOrNull()) }

    TrustCardFrame {
        Kicker(MaterialSymbols.AutoMirrored.List, stringRes(Res.string.dlist_list_of, header.plural))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ListPicture(header.image, null, 56.dp, accountViewModel)
            Column(Modifier.weight(1f)) {
                Text(
                    text = header.plural,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                header.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (header.unaffiliated) {
            TonalChip(stringRes(Res.string.dlist_unaffiliated))
        }

        if (header.fields.isNotEmpty()) {
            TrustSectionLabel(stringRes(Res.string.dlist_fields))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CappedChips(header.fields, MAX_CHIPS) { field ->
                    TonalChip(field.name, emphasized = field.requirement == FieldRequirement.REQUIRED)
                }
            }
        }

        HeaderItems(baseNote, event, header, accountViewModel, nav)
    }
}

/** The items filed under this header, loaded from the relays it was seen on while the card shows. */
@Composable
private fun HeaderItems(
    baseNote: Note,
    event: Event,
    header: DListHeaderView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DListItemsFilterAssemblerSubscription(baseNote, accountViewModel.dataSources().dListItems)

    val items by remember(event) {
        val filter = Filter(kinds = dListItemKinds(event), tags = mapOf("z" to dListItemPointers(event)))
        LocalCache.observeNotes(filter).map { notes -> notes.filter { it.event != null } }
    }.collectAsStateWithLifecycle(emptyList())

    if (items.isEmpty()) return

    TrustSectionLabel(
        if (items.size >= DLIST_ITEMS_LIMIT) {
            stringRes(Res.string.dlist_items_count_more, items.size)
        } else {
            pluralStringRes(Res.plurals.dlist_items_count, items.size, items.size)
        },
    )

    Column {
        items.take(PREVIEW_ITEMS).forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(thickness = DividerThickness)
            ItemPreviewRow(item, header, accountViewModel, nav)
        }
    }
}

@Composable
private fun ItemPreviewRow(
    item: Note,
    header: DListHeaderView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = item.event ?: return
    val view = remember(event, header) { dListItemView(event.tags, event.content, header) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(item, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(vertical = 6.dp),
    ) {
        ListPicture(view.image, header.image, 36.dp, accountViewModel)
        Column(Modifier.weight(1f)) {
            ItemTitle(view.title, header.singular, FontWeight.SemiBold)
            view.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A Decentralized List item (kinds 9999 / 39999), drawn by what its list says items have: the
 * parent list it sits on, its title, summary and picture, its link, its fields as "label: value",
 * a note of required fields it lacks, and the people, notes and words it lists. Mirrors
 * Brainstorm's `DListItemHero`.
 */
@Composable
fun RenderDListItem(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by baseNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val event = noteState.note.event ?: return
    val parent = remember(event) { event.tags.parentLists().firstOrNull() }

    when (parent) {
        is ParentList.Coordinate ->
            LoadAddressableNote(parent.address) { headerNote ->
                if (headerNote == null) {
                    RenderDListItem(event, null, null, quotesLeft, backgroundColor, accountViewModel, nav)
                } else {
                    val headerEvent by observeNoteEvent<Event>(headerNote)
                    RenderDListItem(event, headerNote, headerEvent, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

        is ParentList.EventId ->
            LoadNote(parent.eventId) { headerNote ->
                if (headerNote == null) {
                    RenderDListItem(event, null, null, quotesLeft, backgroundColor, accountViewModel, nav)
                } else {
                    val headerEvent by observeNoteEvent<Event>(headerNote)
                    RenderDListItem(event, headerNote, headerEvent, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

        is ParentList.Name -> RenderDListItem(event, null, null, quotesLeft, backgroundColor, accountViewModel, nav, listName = parent.name)

        null -> RenderDListItem(event, null, null, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderDListItem(
    event: Event,
    headerNote: Note?,
    headerEvent: Event?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    listName: String? = null,
) {
    val header = remember(headerEvent) { headerEvent?.let { dListHeaderView(it.tags, it.dTagOrNull()) } }
    val view = remember(event, header) { dListItemView(event.tags, event.content, header) }
    val members = remember(event) { dListItemMembers(event) }
    val words = remember(event) { event.tags.itemStrings() }

    TrustCardFrame {
        val singular = header?.singular?.ifBlank { null }
        val listLabel = header?.plural?.ifBlank { null } ?: listName
        if (singular != null || listLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    if (headerNote != null) {
                        Modifier.clickable { routeFor(headerNote, accountViewModel.account)?.let { nav.nav(it) } }
                    } else {
                        Modifier
                    },
            ) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.Label,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                singular?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                listLabel?.let {
                    Text(
                        text = stringRes(Res.string.dlist_item_in, it),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (view.image != null || header?.image != null) {
                ListPicture(view.image, header?.image, 56.dp, accountViewModel)
            }
            Column(Modifier.weight(1f)) {
                ItemTitle(view.title, singular ?: "", FontWeight.Bold, large = true)
                view.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        view.link?.let { link ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    symbol = MaterialSymbols.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                ClickableUrl(url = link, displayText = link.substringAfter("://").trimEnd('/'))
            }
        }

        if (view.facts.isNotEmpty()) {
            Facts(view.facts)
        }

        if (view.missing.isNotEmpty()) {
            TonalChip(stringRes(Res.string.dlist_missing_fields, view.missing.joinToString(", ")))
        }

        if (words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CappedChips(words, MAX_CHIPS) { TonalChip(it) }
            }
        }

        if (members.isNotEmpty()) {
            TrustSectionLabel(stringRes(Res.string.dlist_items))
            // An item can list thousands of people or notes: they page in like a list card's members.
            PagedMemberColumn(members, backgroundColor) { member ->
                when (member) {
                    is DListMember.Person -> UserMemberRow(member.pubKey, accountViewModel, nav)
                    is DListMember.NoteId -> BookmarkMemberRow(EventBookmark(member.id), quotesLeft, backgroundColor, accountViewModel, nav)
                    is DListMember.Coordinate -> AddressMemberRow(member.address, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun Facts(facts: List<DListFact>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) facts else facts.take(COLLAPSED_FACTS)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { fact ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = fact.label ?: fieldWords(fact.field),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.35f),
                )
                Row(Modifier.weight(0.65f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fact.href != null) {
                        // The value as written ("+1 346 493 2858"); the link is what its type spells
                        // ("tel:+13464932858"), handed to the platform: a browser, a dialer or a mail app.
                        ClickableUrl(url = fact.href, displayText = fact.value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                    } else {
                        Text(
                            text = fact.value,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (fact.extra > 0) {
                        Text(
                            text = pluralStringRes(Res.plurals.chips_n_more, fact.extra, fact.extra),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (facts.size > COLLAPSED_FACTS && !expanded) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShowMoreButton { expanded = true }
            }
        }
    }
}

@Composable
private fun Kicker(
    symbol: MaterialSymbol,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ItemTitle(
    title: String?,
    singular: String,
    fontWeight: FontWeight?,
    large: Boolean = false,
) {
    val style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    if (title != null) {
        Text(text = title, style = style, fontWeight = fontWeight, maxLines = 2, overflow = TextOverflow.Ellipsis)
    } else {
        Text(
            text = stringRes(Res.string.dlist_untitled_item, singular),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The item's picture, else its list's, else the plain list mark. */
@Composable
private fun ListPicture(
    image: String?,
    fallback: String?,
    size: Dp,
    accountViewModel: AccountViewModel,
) {
    val url = image ?: fallback
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(PictureShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            MyAsyncImage(
                imageUrl = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = Modifier.fillMaxSize(),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = { ListMark(size) },
            )
        } else {
            ListMark(size)
        }
    }
}

@Composable
private fun ListMark(size: Dp) {
    Icon(
        symbol = MaterialSymbols.AutoMirrored.List,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size / 2),
    )
}
