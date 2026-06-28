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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.GitStatusIndex
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

/**
 * Open / Close controls for a git issue. Visible only to the people NIP-34 lets
 * moderate the thread: the issue author and the repository owner / maintainers.
 * Publishing toggles the issue between [GitStatusOpenEvent] and
 * [GitStatusClosedEvent]; [GitStatusIndex] then reflects the change everywhere.
 */
@Composable
fun GitIssueStatusActions(
    issueNote: Note,
    accountViewModel: AccountViewModel,
) {
    val event = issueNote.event as? GitIssueEvent ?: return

    val canModerate = remember(event, issueNote) { canModerate(event, issueNote, accountViewModel) }
    if (!canModerate) return

    LaunchedEffect(Unit) { GitStatusIndex.startIfNeeded() }
    val index by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    if (index == null) return // wait for the initial scan
    val isClosed = GitStatusIndex.isClosedOrResolved(issueNote.idHex)

    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isClosed) {
            FilledTonalButton(onClick = { sendIssueStatus(accountViewModel, issueNote, close = false) }) {
                Icon(MaterialSymbols.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringRes(R.string.git_issue_reopen), modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            OutlinedButton(
                onClick = { sendIssueStatus(accountViewModel, issueNote, close = true) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(MaterialSymbols.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringRes(R.string.git_issue_close), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun canModerate(
    event: GitIssueEvent,
    issueNote: Note,
    accountViewModel: AccountViewModel,
): Boolean {
    val myHex = accountViewModel.account.signer.pubKey
    if (accountViewModel.isLoggedUser(issueNote.author)) return true
    val ownerHex = event.repositoryAddress()?.pubKeyHex
    if (myHex == ownerHex) return true
    // Include extra maintainers when the repository event is already in cache.
    val repoEvent =
        event.repositoryAddress()?.let { LocalCache.getAddressableNoteIfExists(it)?.event as? GitRepositoryEvent }
    return repoEvent?.maintainers()?.contains(myHex) == true
}

private fun sendIssueStatus(
    accountViewModel: AccountViewModel,
    issueNote: Note,
    close: Boolean,
) {
    val target = issueNote.toEventHint<GitIssueEvent>() ?: return
    accountViewModel.launchSigner {
        val template =
            if (close) {
                GitStatusClosedEvent.build("", target)
            } else {
                GitStatusOpenEvent.build("", target)
            }
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}
