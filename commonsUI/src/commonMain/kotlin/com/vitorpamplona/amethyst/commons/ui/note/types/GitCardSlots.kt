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
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * The parts of the NIP-34 git cards that need the JVM git client (smart-HTTP fetches, GRASP
 * pushes, the private-repository auth). The cards themselves are shared; a front end without that
 * client keeps these defaults, which draw nothing or the part that works without git, so it never
 * shows a button that cannot do anything.
 */
interface GitCardSlots {
    /** Under a repository announcement: the snapshot dashboard (stat tiles, languages, last commit). */
    @Composable
    fun RepositoryDashboard(
        event: GitRepositoryEvent,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /** "Check out locally" in a pull request's or patch's thread. The IDE links need a desktop IDE. */
    @Composable
    fun LocalCheckout(
        note: Note,
        branchName: String?,
        cloneUrls: List<String>,
        tip: String?,
        patch: String?,
    ) = GitLocalCheckoutSection(note, branchName, cloneUrls, tip, patch, opensInIde = false)

    /** A pull request's "View changes": its diff, computed by fetching both commits. */
    @Composable
    fun PullRequestChanges(
        note: Note,
        cloneUrls: List<String>,
        currentCommit: String?,
        mergeBase: String?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /** For a pull request's author: push a newer tip and publish the update. */
    @Composable
    fun PullRequestUpdateButton(
        note: Note,
        repository: AddressableNote?,
        currentCommit: String?,
        cloneUrls: List<String>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /** The status buttons of an item's thread; where the host can merge, with an in-app merge. */
    @Composable
    fun StatusActions(
        note: Note,
        accountViewModel: AccountViewModel,
    ) = GitStatusActions(note, accountViewModel)

    /** Only what needs no git client: the shared card parts, no fetches, pushes or merges. */
    object Portable : GitCardSlots
}
