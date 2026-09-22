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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange

private const val MAX_INDIVIDUAL_CARDS = 2

/**
 * Cards at the top of Home, one per open [ReplaceableBackupConflict]: another app replaced
 * one of the account's backed-up lists (or the profile) with a version that lost data. They
 * can't be dismissed. They stay until the user decides on the review screen they open
 * ([BackupConflictReviewScreen]), where the differences can be inspected. With more than
 * [MAX_INDIVIDUAL_CARDS] conflicts they fold into one card with a row per conflict, so they
 * don't cover the feed.
 */
@Composable
fun BackupConflictCards(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val conflictMap by accountViewModel.account.settings.backupConflicts
        .collectAsStateWithLifecycle()
    if (conflictMap.isEmpty()) return

    val conflicts = conflictMap.values.sortedBy { it.cause.createdAt }
    val open = { conflict: ReplaceableBackupConflict -> nav.nav(Route.BackupConflictReview(conflict.slot)) }

    if (conflicts.size <= MAX_INDIVIDUAL_CARDS) {
        conflicts.forEach { conflict ->
            ConflictCardSurface(modifier) {
                ConflictRow(
                    title = stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(conflict.eventType))),
                    conflict = conflict,
                    onClick = { open(conflict) },
                )
            }
        }
    } else {
        ConflictCardSurface(modifier) {
            Column {
                Text(
                    text = stringRes(R.string.backup_conflict_many_title, conflicts.size.toString()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                )
                conflicts.forEach { conflict ->
                    ConflictRow(
                        title = stringRes(eventTypeName(conflict.eventType)).replaceFirstChar { it.uppercase() },
                        conflict = conflict,
                        onClick = { open(conflict) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictCardSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        // Home's padding only covers the top bar; in landscape a side 3-button navigation bar
        // or a display cutout would otherwise sit on top of the card.
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        content = content,
    )
}

@Composable
private fun ConflictRow(
    title: String,
    conflict: ReplaceableBackupConflict,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.SyncProblem,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = StdHorzSpacer)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conflictSummary(conflict),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = stringRes(R.string.backup_conflict_review),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "12 removed · 3 added · 1 changed", counting encrypted content as one item. */
@Composable
private fun conflictSummary(conflict: ReplaceableBackupConflict): String {
    val presentation = presentationOf(conflict.diff)
    val content = presentation.content
    val removed = presentation.removedCount + if (content == ContentChange.CLEARED) 1 else 0
    val added = presentation.addedCount + if (content == ContentChange.ADDED) 1 else 0
    val changed = presentation.changedCount + if (content == ContentChange.CHANGED) 1 else 0
    return listOfNotNull(
        if (removed > 0) stringRes(R.string.backup_conflict_count_removed, removed.toString()) else null,
        if (added > 0) stringRes(R.string.backup_conflict_count_added, added.toString()) else null,
        if (changed > 0) stringRes(R.string.backup_conflict_count_changed, changed.toString()) else null,
    ).joinToString(" · ")
}
