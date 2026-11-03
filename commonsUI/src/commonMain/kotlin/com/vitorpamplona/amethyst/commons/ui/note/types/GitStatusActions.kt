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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.GitStatusIndex
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStatusButtons
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

/**
 * NIP-34 status controls for an issue, patch or pull request. Visible only to the people whose
 * status counts ([GitAuthority.memberActorsOf]): the item's author and the repository's confirmed
 * members. "Mark merged" is for confirmed maintainers only, as gitworkshop offers it.
 * [GitStatusIndex] reflects the published status everywhere.
 */
@Composable
fun GitStatusActions(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event ?: return
    if (GitAuthority.repositoriesOf(event).isEmpty()) return
    val isPatchOrPr = event is GitPatchEvent || event is GitPullRequestEvent

    val authority = observeGitAuthority()
    val me = accountViewModel.account.signer.pubKey
    val canSetStatus = remember(event, authority, me) { me in authority.memberActorsOf(event) }
    if (!canSetStatus) return
    val canMerge = remember(event, authority, me) { authority.isRepositoryMaintainer(me, event) }

    val index by GitStatusIndex.latestByTarget.collectAsStateWithLifecycle()
    if (index == null) return
    val current = index?.get(note.idHex)?.statusKind() ?: StatusKind.OPEN

    GitStatusButtons(
        current = current,
        isPatchOrPr = isPatchOrPr,
        canMerge = canMerge,
        onSetStatus = { sendStatus(accountViewModel, note, it) },
    )
}

private fun sendStatus(
    accountViewModel: AccountViewModel,
    note: Note,
    target: StatusKind,
) {
    val hint = note.toEventHint<Event>() ?: return
    accountViewModel.launchSigner {
        val template =
            when (target) {
                StatusKind.OPEN -> GitStatusOpenEvent.build("", hint)
                StatusKind.CLOSED -> GitStatusClosedEvent.build("", hint)
                StatusKind.APPLIED -> GitStatusAppliedEvent.build("", hint)
                StatusKind.DRAFT -> GitStatusDraftEvent.build("", hint)
            }
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}

/** The repository coordinate an issue, patch or PR is filed against (its first). */
internal fun repositoryAddressOf(event: Event): Address? = GitAuthority.repositoriesOf(event).firstOrNull()
