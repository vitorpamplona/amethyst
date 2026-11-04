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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.GitStateMismatch
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLoadingBox
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitMessageBox
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.RepoInfoBar
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_loading
import com.vitorpamplona.amethyst.commons.resources.git_repo_empty_folder
import com.vitorpamplona.amethyst.commons.resources.git_repo_file_load_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_no_clone_url
import com.vitorpamplona.amethyst.commons.resources.git_repo_no_search_results
import com.vitorpamplona.amethyst.commons.resources.git_repo_root
import com.vitorpamplona.amethyst.commons.resources.git_repo_search_files
import com.vitorpamplona.amethyst.commons.resources.git_repo_state_mismatch
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingBarState
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.git.GitTreeEntry

@Composable
fun GitCodeTab(
    state: GitBrowseState,
    viewModel: GitRepositoryBrowserViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scaffoldPadding = LocalDisappearingScaffoldPadding.current
    when (state) {
        is GitBrowseState.Loading ->
            GitLoadingBox(stringRes(Res.string.git_repo_code_loading), Modifier.padding(scaffoldPadding))

        is GitBrowseState.Error -> {
            val noClone = state.message == GitRepositoryBrowserViewModel.NO_CLONE_URL
            GitMessageBox(
                symbol = if (noClone) MaterialSymbols.Code else MaterialSymbols.ErrorOutline,
                text = if (noClone) stringRes(Res.string.git_repo_no_clone_url) else stringRes(Res.string.git_repo_code_error),
                modifier = Modifier.padding(scaffoldPadding),
                onRetry = if (noClone) null else viewModel::reload,
            )
        }

        is GitBrowseState.Loaded ->
            CodeBrowser(state.snapshot, viewModel, accountViewModel, nav, scaffoldPaddingTop = scaffoldPadding, mismatch = state.mismatch)
    }
}

@Composable
private fun CodeBrowser(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    scaffoldPaddingTop: PaddingValues,
    mismatch: GitStateMismatch? = null,
) {
    var pathString by rememberSaveable(snapshot.headCommit) { mutableStateOf("") }
    var openFilePath by rememberSaveable(snapshot.headCommit) { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable(snapshot.headCommit) { mutableStateOf(false) }

    // Each of these toggles swaps the scrollable content in place, which lands the new view at
    // the top with no scroll delta. Pull the disappearing top bar back into view so it doesn't
    // stay stranded at its hidden offset, leaving a blank band over the fresh content.
    val barState = LocalDisappearingBarState.current
    LaunchedEffect(pathString, openFilePath, showHistory) {
        barState?.resetToVisible()
    }

    if (showHistory) {
        Column(Modifier.fillMaxSize().padding(scaffoldPaddingTop)) {
            GitCommitLog(snapshot, viewModel, onBack = { showHistory = false })
        }
        return
    }

    val path = remember(pathString) { if (pathString.isEmpty()) emptyList() else pathString.split("/") }
    val openPath = openFilePath?.let { if (it.isEmpty()) emptyList() else it.split("/") }

    if (openPath != null) {
        val entry = remember(openFilePath) { snapshot.entryAt(openPath) }
        PlatformBackHandler { openFilePath = null }
        Column(Modifier.fillMaxSize().padding(scaffoldPaddingTop)) {
            FileHeader(name = openPath.lastOrNull() ?: "", onBack = { openFilePath = null })
            HorizontalDivider(thickness = 0.5.dp)
            if (entry == null) {
                GitMessageBox(MaterialSymbols.ErrorOutline, stringRes(Res.string.git_repo_file_load_error))
            } else {
                GitFileViewer(
                    snapshot = snapshot,
                    viewModel = viewModel,
                    entry = entry,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    var query by rememberSaveable(snapshot.headCommit) { mutableStateOf("") }
    var searchOpen by rememberSaveable(snapshot.headCommit) { mutableStateOf(false) }
    val searching = query.isNotBlank()

    PlatformBackHandler(enabled = searchOpen || searching || path.isNotEmpty()) {
        when {
            searching -> query = ""
            searchOpen -> searchOpen = false
            else -> pathString = path.dropLast(1).joinToString("/")
        }
    }

    val entries = remember(snapshot, pathString) { snapshot.entriesAt(path).orEmpty() }

    // A single scrolling list so the branch/search header rides along with the disappearing
    // top bar instead of staying pinned below it. The header rows are the first list items;
    // the file/search rows follow, all under one contentPadding for the scaffold inset.
    val results = remember(snapshot, query) { if (searching) snapshot.searchFiles(query.trim()) else emptyList() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = scaffoldPaddingTop) {
        if (mismatch != null) {
            item(key = "signed-state-mismatch") { SignedStateMismatchBanner(mismatch) }
        }
        item(key = "repo-info-bar") {
            RepoInfoBar(
                branch = snapshot.branch,
                entryCount = entries.size,
                branches = snapshot.branches,
                tags = snapshot.tags,
                onSelectRef = { viewModel.switchRef(it) },
                onHistory = { showHistory = true },
                onSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
            )
        }
        if (searchOpen || searching) {
            item(key = "file-search") {
                FileSearchField(query = query, onQueryChange = { query = it })
            }
        }

        if (searching) {
            if (results.isEmpty()) {
                item(key = "no-results") {
                    GitMessageBox(MaterialSymbols.Search, stringRes(Res.string.git_repo_no_search_results), Modifier.fillParentMaxWidth())
                }
            } else {
                items(results, key = { "result/" + it.joinToString("/") }) { result ->
                    SearchResultRow(result) { openFilePath = result.joinToString("/") }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 40.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    )
                }
            }
        } else {
            if (path.isNotEmpty()) {
                item(key = "breadcrumb") {
                    Breadcrumb(
                        path = path,
                        onNavigate = { depth -> pathString = path.take(depth).joinToString("/") },
                    )
                }
            }
            item(key = "list-top") { HorizontalDivider(thickness = 0.5.dp) }
            if (entries.isEmpty()) {
                item(key = "empty-folder") {
                    GitMessageBox(MaterialSymbols.Folder, stringRes(Res.string.git_repo_empty_folder), Modifier.fillParentMaxWidth())
                }
            } else {
                items(entries, key = { "entry/" + it.name }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = {
                            val child = (path + entry.name).joinToString("/")
                            if (entry.isFolder) pathString = child else openFilePath = child
                        },
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.subtleBorder,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    // Opened from the search icon: take the keyboard at once.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringRes(Res.string.git_repo_search_files)) },
        leadingIcon = { Icon(MaterialSymbols.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(MaterialSymbols.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .gitCard()
                .focusRequester(focusRequester),
    )
}

@Composable
private fun SearchResultRow(
    path: List<String>,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = path.last(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (path.size > 1) {
                Text(
                    text = path.dropLast(1).joinToString("/"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "root / src / apps": each folder above the current one opens it. */
@Composable
private fun Breadcrumb(
    path: List<String>,
    onNavigate: (depth: Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(label = stringRes(Res.string.git_repo_root), current = path.isEmpty()) { onNavigate(0) }
        path.forEachIndexed { index, segment ->
            Text(text = "/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText)
            Crumb(label = segment, current = index == path.lastIndex) { onNavigate(index + 1) }
        }
    }
}

@Composable
private fun Crumb(
    label: String,
    current: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier =
            Modifier
                .clip(GitShape)
                .let { if (!current) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun EntryRow(
    entry: GitTreeEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val symbol =
            when {
                entry.isFolder -> MaterialSymbols.Folder
                entry.isSubmodule -> MaterialSymbols.Code
                else -> MaterialSymbols.Description
            }
        // One font for folders and files, folders told apart by their tinted icon, as forges list a tree.
        val tint = if (entry.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FileHeader(
    name: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) { ArrowBackIcon() }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * No clone URL served the commit the maintainers signed for this branch (kind 30618): the code shown
 * comes from a server that may be behind, or not serve their repository at all.
 */
@Composable
private fun SignedStateMismatchBanner(mismatch: GitStateMismatch) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(MaterialSymbols.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            text = stringRes(Res.string.git_repo_state_mismatch, mismatch.branch, mismatch.signedCommit.take(7), mismatch.servedCommit.take(7)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
