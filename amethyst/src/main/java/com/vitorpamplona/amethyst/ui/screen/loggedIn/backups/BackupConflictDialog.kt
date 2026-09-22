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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange

/**
 * Asks the user what to do when another client replaced one of the account's backed-up
 * replaceable events with a version that lost data: keep the new one, or re-sign the
 * version saved on this device over it.
 *
 * The dialog is a short summary (what changed, how many of each kind of entry); "Review
 * changes" opens [BackupConflictReviewScreen], which lists every entry and links people,
 * notes, chats and relays. The dialog stays hidden while that screen is open.
 *
 * One conflict at a time; "decide later" snoozes it while the account stays loaded (so it
 * survives rotation) and until another app changes that event again: the conflict stays
 * open, so the backup stays frozen on the saved version and the question comes back on the
 * next launch.
 */
@Composable
fun BackupConflictDialog(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val conflicts by settings.backupConflicts.collectAsStateWithLifecycle()
    val snoozed by settings.snoozedBackupConflicts.collectAsStateWithLifecycle()
    val reviewing by settings.reviewingBackupConflict.collectAsStateWithLifecycle()

    val conflict = conflicts.values.firstOrNull { it.slot !in snoozed && it.slot != reviewing } ?: return

    BackupConflictDialog(
        conflict = conflict,
        onRestore = { accountViewModel.launchSigner { accountViewModel.account.restoreBackupOver(conflict) } },
        onKeepNew = { accountViewModel.account.acceptExternalVersion(conflict) },
        onReview = { nav.nav(Route.BackupConflictReview(conflict.slot)) },
        onDismiss = { settings.snoozeBackupConflict(conflict) },
    )
}

@Composable
private fun BackupConflictDialog(
    conflict: ReplaceableBackupConflict,
    onRestore: () -> Unit,
    onKeepNew: () -> Unit,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    val presentation = presentationOf(conflict.diff)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(conflict.eventType)))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BackupConflictIntro(conflict)
                SummarySection(R.string.backup_conflict_removed, presentation.removedCount, MaterialTheme.colorScheme.error, presentation.content == ContentChange.CLEARED, R.string.backup_conflict_private_cleared) {
                    presentation.groups.filter { it.removed.isNotEmpty() }.map { it.label to it.removed.size }
                }
                SummarySection(R.string.backup_conflict_added, presentation.addedCount, MaterialTheme.colorScheme.primary, presentation.content == ContentChange.ADDED, R.string.backup_conflict_private_added) {
                    presentation.groups.filter { it.added.isNotEmpty() }.map { it.label to it.added.size }
                }
                SummarySection(R.string.backup_conflict_changed, presentation.changedCount, MaterialTheme.colorScheme.tertiary, presentation.content == ContentChange.CHANGED, R.string.backup_conflict_private_changed) {
                    presentation.groups.filter { it.changed.isNotEmpty() }.map { it.label to it.changed.size }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringRes(R.string.backup_conflict_restore_explainer),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onRestore) { Text(stringRes(R.string.backup_conflict_restore_mine)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onReview) { Text(stringRes(R.string.backup_conflict_review)) }
                TextButton(onClick = onKeepNew) { Text(stringRes(R.string.backup_conflict_keep_new)) }
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.backup_conflict_decide_later)) }
            }
        },
    )
}

/** What the event is for and when the other app changed it; shared by the dialog and the review screen. */
@Composable
fun BackupConflictIntro(conflict: ReplaceableBackupConflict) {
    val context = LocalContext.current
    Text(
        text = stringRes(eventTypeExplainer(conflict.eventType)),
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
    )
    Spacer(Modifier.height(12.dp))
    Text(stringRes(R.string.backup_conflict_intro, timeAbsolute(conflict.cause.createdAt, context)))
}

@Composable
private fun SummarySection(
    titleRes: Int,
    count: Int,
    color: Color,
    showContentNote: Boolean,
    contentNoteRes: Int,
    groups: () -> List<Pair<Int, Int>>,
) {
    if (count == 0 && !showContentNote) return
    Spacer(Modifier.height(12.dp))
    Text(stringRes(titleRes, count.toString()), style = MaterialTheme.typography.titleSmall, color = color)
    if (showContentNote) {
        Text("• " + stringRes(contentNoteRes), modifier = Modifier.padding(start = 8.dp))
    }
    groups().forEach { (label, size) ->
        Text("• " + stringRes(label) + ": " + size, modifier = Modifier.padding(start = 8.dp))
    }
}
