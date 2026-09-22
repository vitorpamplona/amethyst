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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.backups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange

/** A row of the review list, flattened so hundreds of entries scroll lazily. */
@Immutable
internal sealed interface ReviewRow {
    class Section(
        val titleRes: Int,
        val count: Int,
        val tone: Tone,
    ) : ReviewRow

    class Note(
        val textRes: Int,
    ) : ReviewRow

    class Group(
        val labelRes: Int,
    ) : ReviewRow

    class Entry(
        val item: ReviewItem,
    ) : ReviewRow
}

internal enum class Tone { REMOVED, ADDED, CHANGED }

internal fun buildRows(
    presentation: DiffPresentation,
    skipGroups: Set<Int> = emptySet(),
): List<ReviewRow> {
    val rows = mutableListOf<ReviewRow>()

    fun section(
        titleRes: Int,
        count: Int,
        tone: Tone,
        contentNote: Int?,
        items: (DiffGroup) -> List<ReviewItem>,
    ) {
        if (count == 0 && contentNote == null) return
        rows.add(ReviewRow.Section(titleRes, count, tone))
        contentNote?.let { rows.add(ReviewRow.Note(it)) }
        presentation.groups.forEach { group ->
            if (group.label in skipGroups) return@forEach
            val entries = items(group)
            if (entries.isNotEmpty()) {
                rows.add(ReviewRow.Group(group.label))
                entries.forEach { rows.add(ReviewRow.Entry(it)) }
            }
        }
    }

    val content = presentation.content
    section(R.string.backup_conflict_removed, presentation.removedCount, Tone.REMOVED, R.string.backup_conflict_private_cleared.takeIf { content == ContentChange.CLEARED }) { it.removed }
    section(R.string.backup_conflict_added, presentation.addedCount, Tone.ADDED, R.string.backup_conflict_private_added.takeIf { content == ContentChange.ADDED }) { it.added }
    section(R.string.backup_conflict_changed, presentation.changedCount, Tone.CHANGED, R.string.backup_conflict_private_changed.takeIf { content == ContentChange.CHANGED }) { it.changed }
    return rows
}

/**
 * Every entry of a backup conflict, removed / added / changed, in a lazy list: people,
 * notes, communities, feeds, chats and relays are loaded from relays and open their own
 * screens when tapped, so the user can investigate the change before deciding. Opened from
 * the Home cards ([BackupConflictCards]); deciding resolves the conflict and removes its card.
 * Leaving without deciding keeps the card and the frozen backup.
 */
@Composable
fun BackupConflictReviewScreen(
    slot: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val conflicts by settings.backupConflicts.collectAsStateWithLifecycle()
    val conflict = conflicts[slot]

    // Resolved from here or elsewhere: nothing left to review.
    if (conflict == null) {
        LaunchedEffect(Unit) { nav.popBack() }
        return
    }

    BackupConflictReview(
        conflict = conflict,
        accountViewModel = accountViewModel,
        nav = nav,
        onRestore = {
            accountViewModel.launchSigner { accountViewModel.account.restoreBackupOver(conflict) }
            nav.popBack()
        },
        onKeepNew = {
            accountViewModel.account.acceptExternalVersion(conflict)
            nav.popBack()
        },
    )
}

@Composable
private fun BackupConflictReview(
    conflict: ReplaceableBackupConflict,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRestore: () -> Unit,
    onKeepNew: () -> Unit,
) {
    val presentation = presentationOf(conflict.diff)

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(conflict.eventType))), nav)
        },
        bottomBar = { ReviewActions(onRestore, onKeepNew) },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            // Scaffold hands the content its insets (status bar, the bottom action bar and,
            // in landscape, a side 3-button navigation bar); consume them so the IME padding
            // below only adds what the keyboard covers beyond them.
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePaddingSafe(),
            contentPadding =
                PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            item(key = "intro", contentType = "intro") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BackupConflictIntro(conflict)
                    Text(
                        text = stringRes(R.string.backup_conflict_restore_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            eventDiffItems(conflict, presentation, accountViewModel, nav)
        }
    }
}

/** The default layout: removed / added / changed sections of labelled, typed entries. */
internal fun LazyListScope.genericDiffItems(
    rows: List<ReviewRow>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    itemsIndexed(
        rows,
        contentType = { _, row ->
            when (row) {
                is ReviewRow.Section -> "section"
                is ReviewRow.Note -> "note"
                is ReviewRow.Group -> "group"
                is ReviewRow.Entry -> row.item::class.simpleName
            }
        },
    ) { _, row ->
        when (row) {
            is ReviewRow.Section -> SectionRow(row)
            is ReviewRow.Note -> DetailText("• " + stringRes(row.textRes), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            is ReviewRow.Group -> GroupLabel(row.labelRes)
            is ReviewRow.Entry -> ReviewEntry(row.item, accountViewModel, nav)
        }
    }
}

@Composable
internal fun GroupLabel(labelRes: Int) {
    Text(
        text = stringRes(labelRes),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
internal fun SectionRow(row: ReviewRow.Section) {
    val color =
        when (row.tone) {
            Tone.REMOVED -> MaterialTheme.colorScheme.error
            Tone.ADDED -> MaterialTheme.colorScheme.primary
            Tone.CHANGED -> MaterialTheme.colorScheme.tertiary
        }
    HorizontalDivider()
    Text(
        text = stringRes(row.titleRes, row.count.toString()),
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReviewActions(
    onRestore: () -> Unit,
    onKeepNew: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            // Scaffold only insets its content slot; a bottomBar handles its own. The padding
            // goes on the content, not the Surface, so the bar's background still reaches the
            // screen edge while the buttons sit above the 3-button navigation (and above the
            // keyboard, if one is up) instead of underneath it.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onKeepNew, modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.backup_conflict_keep_new), textAlign = TextAlign.Center)
            }
            Button(onClick = onRestore, modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.backup_conflict_restore_mine), textAlign = TextAlign.Center)
            }
        }
    }
}

/** What the event is for and when the other app changed it. */
@Composable
private fun BackupConflictIntro(conflict: ReplaceableBackupConflict) {
    val context = LocalContext.current
    Text(
        text = stringRes(eventTypeExplainer(conflict.eventType)),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
    )
    Spacer(Modifier.height(12.dp))
    Text(stringRes(R.string.backup_conflict_intro, timeAbsolute(conflict.cause.createdAt, context)))
}

internal val EntryPadding = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)

@Composable
internal fun ReviewEntry(
    item: ReviewItem,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is ReviewItem.Person -> PersonEntry(item, accountViewModel, nav)
        is ReviewItem.Thread ->
            LoadNote(item.eventId, accountViewModel) { note ->
                if (note != null) {
                    Column(EntryPadding) {
                        NoteCompose(note, makeItShort = true, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                        item.detail?.let { DetailText(it) }
                    }
                } else {
                    TextEntry(item.eventId.toShortDisplay(), item.detail)
                }
            }
        is ReviewItem.Addressable ->
            LoadAddressableNote(item.address, accountViewModel) { note ->
                if (note != null) {
                    Column(EntryPadding) {
                        NoteCompose(note, makeItShort = true, quotesLeft = 1, accountViewModel = accountViewModel, nav = nav)
                        item.detail?.let { DetailText(it) }
                    }
                } else {
                    TextEntry(item.address.dTag.ifBlank { item.address.toValue() }, item.detail)
                }
            }
        is ReviewItem.PublicChat -> PublicChatEntry(item, accountViewModel, nav)
        is ReviewItem.ChatRoom ->
            TextEntry(item.roomId.id + " @ " + item.roomId.relayUrl.url, item.detail) { nav.nav(routeFor(item.roomId)) }
        is ReviewItem.Relay -> TextEntry(item.url.url, item.detail) { nav.nav(Route.RelayInfo(item.url.url)) }
        is ReviewItem.Text -> TextEntry(item.text, item.detail)
    }
}

@Composable
private fun PersonEntry(
    item: ReviewItem.Person,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(item.pubKey, accountViewModel) { user ->
        if (user == null) {
            TextEntry(item.pubKey.toShortDisplay(), item.detail)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { nav.nav(routeFor(user)) }.then(EntryPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserPicture(user, Size35dp, accountViewModel = accountViewModel, nav = nav)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    // Subscribes to the user's metadata on relays, so unknown people resolve.
                    UsernameDisplay(user, accountViewModel = accountViewModel)
                    item.detail?.let { DetailText(it) }
                }
            }
        }
    }
}

@Composable
private fun PublicChatEntry(
    item: ReviewItem.PublicChat,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadPublicChatChannel(item.channelId, accountViewModel) { channel ->
        // Subscribes to the channel's metadata on relays and recomposes when it arrives.
        val state by observeChannel(channel, accountViewModel)
        val name = state?.channel?.toBestDisplayName() ?: channel.toBestDisplayName()
        TextEntry(name, item.detail) { nav.nav(routeFor(channel)) }
    }
}

@Composable
internal fun TextEntry(
    text: String,
    detail: String?,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Column(modifier.then(EntryPadding)) {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        detail?.let { DetailText(it) }
    }
}

@Composable
internal fun DetailText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
