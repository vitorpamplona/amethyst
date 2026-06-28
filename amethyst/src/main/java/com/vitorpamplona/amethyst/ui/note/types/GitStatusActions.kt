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
import androidx.compose.foundation.layout.FlowRow
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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

private enum class StatusTarget { OPEN, CLOSED, APPLIED }

/**
 * NIP-34 status controls for an issue, patch or pull request. Visible only to the
 * people allowed to moderate the thread — the item's author and the repository
 * owner / maintainers. Patches and PRs additionally offer "Mark merged"
 * ([GitStatusAppliedEvent]); everything can be closed/reopened. [GitStatusIndex]
 * reflects the published status everywhere.
 */
@Composable
fun GitStatusActions(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event ?: return
    val repoAddress = repositoryAddressOf(event) ?: return
    val isPatchOrPr = event is GitPatchEvent || event is GitPullRequestEvent

    val canModerate = remember(event, note) { canModerate(repoAddress, note, accountViewModel) }
    if (!canModerate) return

    LaunchedEffect(Unit) { GitStatusIndex.startIfNeeded() }
    val index by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    if (index == null) return
    val current = index?.get(note.idHex)
    val closedOrApplied = current is GitStatusClosedEvent || current is GitStatusAppliedEvent

    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (closedOrApplied) {
            FilledTonalButton(onClick = { sendStatus(accountViewModel, note, StatusTarget.OPEN) }) {
                Icon(MaterialSymbols.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringRes(R.string.git_status_reopen), modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            if (isPatchOrPr) {
                FilledTonalButton(onClick = { sendStatus(accountViewModel, note, StatusTarget.APPLIED) }) {
                    Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringRes(R.string.git_status_mark_merged), modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(
                onClick = { sendStatus(accountViewModel, note, StatusTarget.CLOSED) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(MaterialSymbols.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringRes(R.string.git_status_close), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun repositoryAddressOf(event: Event): Address? =
    when (event) {
        is GitIssueEvent -> event.repositoryAddress()
        is GitPatchEvent -> event.repositoryAddress()
        is GitPullRequestEvent -> event.repositoryAddress()
        else -> null
    }

private fun canModerate(
    repoAddress: Address,
    note: Note,
    accountViewModel: AccountViewModel,
): Boolean {
    val myHex = accountViewModel.account.signer.pubKey
    if (accountViewModel.isLoggedUser(note.author)) return true
    if (myHex == repoAddress.pubKeyHex) return true
    val repoEvent = LocalCache.getAddressableNoteIfExists(repoAddress)?.event as? GitRepositoryEvent
    return repoEvent?.maintainers()?.contains(myHex) == true
}

private fun sendStatus(
    accountViewModel: AccountViewModel,
    note: Note,
    target: StatusTarget,
) {
    val hint = note.toEventHint<Event>() ?: return
    accountViewModel.launchSigner {
        val template =
            when (target) {
                StatusTarget.OPEN -> GitStatusOpenEvent.build("", hint)
                StatusTarget.CLOSED -> GitStatusClosedEvent.build("", hint)
                StatusTarget.APPLIED -> GitStatusAppliedEvent.build("", hint)
            }
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}
