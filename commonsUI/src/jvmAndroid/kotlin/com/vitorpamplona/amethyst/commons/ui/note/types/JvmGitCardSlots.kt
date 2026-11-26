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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepoSnapshotCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.merge.GitMergeTargets
import com.vitorpamplona.amethyst.commons.nip34Git.status.publishGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip34Git.ui.rememberGitMerge
import com.vitorpamplona.amethyst.commons.nip34Git.ui.rememberGitReviewHooks
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitPullRequestUpdateButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepositoryBrowserViewModelFactory
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoExternalNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLanguageBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLastCommit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoStatTiles
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.computeLanguageBreakdown
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.repoHasFetchableClone
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/** The git cards' [GitCardSlots] on JVM and Android, where the git client fetches, pushes and merges. */
object JvmGitCardSlots : GitCardSlots {
    @Composable
    override fun RepositoryDashboard(
        event: GitRepositoryEvent,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = RepoSnapshotDashboard(event, note, accountViewModel, nav)

    @Composable
    override fun LocalCheckout(
        note: Note,
        branchName: String?,
        cloneUrls: List<String>,
        tip: String?,
        patch: String?,
    ) = GitLocalCheckoutSection(note, branchName, cloneUrls, tip, patch)

    @Composable
    override fun PullRequestChanges(
        note: Note,
        cloneUrls: List<String>,
        currentCommit: String?,
        mergeBase: String?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = GitPullRequestChanges(cloneUrls, currentCommit, mergeBase, accountViewModel, rememberGitReviewHooks(note, currentCommit, accountViewModel, nav))

    @Composable
    override fun PullRequestUpdateButton(
        note: Note,
        repository: AddressableNote?,
        currentCommit: String?,
        cloneUrls: List<String>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = GitPullRequestUpdateButton(note, repository, currentCommit, cloneUrls, accountViewModel, nav)

    @Composable
    override fun StatusActions(
        note: Note,
        accountViewModel: AccountViewModel,
    ) = GitStatusActionsWithMerge(note, accountViewModel)
}

/**
 * Loads a shallow snapshot of the repository over smart-HTTP and renders the same
 * stat tiles / language bar / last-commit strip used on the project home, in place of
 * the old web/clone links. Fetches lazily (once) when the card is composed.
 */
@Composable
private fun RepoSnapshotDashboard(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A store per card: in the screen's store, every repo scrolled past kept its model and loaded
    // snapshot until the screen was popped, around GitRepoSnapshotCache's deliberately small LRU.
    // Scrolling back reads that cache first.
    key(note.idHex) {
        ScopedViewModelStore {
            RepoSnapshotDashboardContent(noteEvent, note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RepoSnapshotDashboardContent(
    noteEvent: GitRepositoryEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val cacheKey = remember(noteEvent) { noteEvent.address().toValue() }
    val browser: GitRepositoryBrowserViewModel =
        viewModel(
            key = note.idHex + "GitRepoCardBrowser",
            factory = GitRepositoryBrowserViewModelFactory(accountViewModel.httpClientBuilder::okHttpClientForPreview, GitPrivateRepositoryAuth(accountViewModel.account.signer)),
        )
    LaunchedEffect(noteEvent) { browser.loadOnce(noteEvent.clones(), cacheKey) }
    val browserState by browser.state.collectAsStateWithLifecycle()
    // Prefer the live state, but fall back to a cached snapshot so an already-fetched repo
    // renders synchronously — including in the share-to-image capture, which won't wait for a
    // fresh clone.
    val snapshot = (browserState as? GitBrowseState.Loaded)?.snapshot ?: remember(cacheKey) { GitRepoSnapshotCache.get(cacheKey) }
    if (snapshot == null) {
        // Repos we can't clone over http(s) (e.g. Iris's htree://) get an open-in-browser notice
        // instead of an indefinitely-empty dashboard.
        if (!repoHasFetchableClone(noteEvent)) {
            Spacer(modifier = HalfDoubleVertSpacer)
            RepoExternalNotice(noteEvent)
        }
        return
    }

    val fileNames = remember(snapshot) { snapshot.walkFileNames() }
    val slices = remember(fileNames) { computeLanguageBreakdown(fileNames) }

    Spacer(modifier = HalfDoubleVertSpacer)
    RepoStatTiles(
        branches = snapshot.branches.size,
        tags = snapshot.tags.size,
        files = fileNames.size,
        updatedEpochSec = snapshot.tipCommit?.authorTimeSec,
    )
    if (slices.isNotEmpty()) {
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLanguageBar(slices)
    }
    snapshot.tipCommit?.let { commit ->
        Spacer(modifier = HalfDoubleVertSpacer)
        RepoLastCommit(commit) { nav.nav(Route.GitRepository(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())) }
    }
}

/**
 * [GitStatusActions] with the in-app merge for a pull request or a patch series' root; "Mark
 * merged" stays for a merge made with git.
 */
@Composable
private fun GitStatusActionsWithMerge(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event
    if (event !is GitPullRequestEvent && !(event is GitPatchEvent && event.isSeriesRoot())) {
        GitStatusActions(note, accountViewModel)
        return
    }
    val onMerge =
        rememberGitMerge(note, accountViewModel) {
            accountViewModel.launchSigner { accountViewModel.account.publishGitStatus(note, GitStatusEvent.KIND_APPLIED) }
        }
    // Offer the in-app merge only where it can land: a repository with a GRASP server.
    val announcements = observeGitRepositoryAnnouncements(event)
    val onGrasp =
        remember(event, announcements) {
            GitAuthority
                .repositoriesOf(event)
                .firstOrNull()
                ?.let { LocalCache.gitAuthority.membership(it) }
                ?.let { GitMergeTargets.of(it.confirmedAnnouncements).grasp.isNotEmpty() } ?: true
        }
    GitStatusActions(note, accountViewModel, onMerge.takeIf { onGrasp })
}
