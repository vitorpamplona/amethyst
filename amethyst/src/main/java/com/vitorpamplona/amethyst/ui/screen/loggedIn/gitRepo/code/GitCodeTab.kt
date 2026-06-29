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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
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
            GitLoadingBox(stringRes(R.string.git_repo_code_loading), Modifier.padding(scaffoldPadding))

        is GitBrowseState.Error -> {
            val noClone = state.message == GitRepositoryBrowserViewModel.NO_CLONE_URL
            GitMessageBox(
                symbol = if (noClone) MaterialSymbols.Code else MaterialSymbols.ErrorOutline,
                text = if (noClone) stringRes(R.string.git_repo_no_clone_url) else stringRes(R.string.git_repo_code_error),
                modifier = Modifier.padding(scaffoldPadding),
                onRetry = if (noClone) null else viewModel::reload,
            )
        }

        is GitBrowseState.Loaded ->
            CodeBrowser(state.snapshot, viewModel, accountViewModel, nav, scaffoldPaddingTop = scaffoldPadding)
    }
}

@Composable
private fun CodeBrowser(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    scaffoldPaddingTop: PaddingValues,
) {
    var pathString by rememberSaveable(snapshot.headCommit) { mutableStateOf("") }
    var openFilePath by rememberSaveable(snapshot.headCommit) { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable(snapshot.headCommit) { mutableStateOf(false) }

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
        BackHandler { openFilePath = null }
        Column(Modifier.fillMaxSize().padding(scaffoldPaddingTop)) {
            FileHeader(name = openPath.lastOrNull() ?: "", onBack = { openFilePath = null })
            HorizontalDivider(thickness = 0.5.dp)
            if (entry == null) {
                GitMessageBox(MaterialSymbols.ErrorOutline, stringRes(R.string.git_repo_file_load_error))
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
    val searching = query.isNotBlank()

    BackHandler(enabled = searching || path.isNotEmpty()) {
        if (searching) query = "" else pathString = path.dropLast(1).joinToString("/")
    }

    val entries = remember(snapshot, pathString) { snapshot.entriesAt(path).orEmpty() }

    // A single scrolling list so the branch/search header rides along with the disappearing
    // top bar instead of staying pinned below it. The header rows are the first list items;
    // the file/search rows follow, all under one contentPadding for the scaffold inset.
    val results = remember(snapshot, query) { if (searching) snapshot.searchFiles(query.trim()) else emptyList() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = scaffoldPaddingTop) {
        item(key = "repo-info-bar") {
            RepoInfoBar(
                branch = snapshot.branch,
                headCommit = snapshot.headCommit,
                entryCount = entries.size,
                branches = snapshot.branches,
                tags = snapshot.tags,
                onSelectRef = { viewModel.switchRef(it) },
                onHistory = { showHistory = true },
            )
        }
        item(key = "file-search") {
            FileSearchField(query = query, onQueryChange = { query = it })
            HorizontalDivider(thickness = 0.5.dp)
        }

        if (searching) {
            if (results.isEmpty()) {
                item(key = "no-results") {
                    GitMessageBox(MaterialSymbols.Search, stringRes(R.string.git_repo_no_search_results), Modifier.fillParentMaxWidth())
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
            item(key = "breadcrumb") {
                Breadcrumb(
                    path = path,
                    onNavigate = { depth -> pathString = path.take(depth).joinToString("/") },
                )
                HorizontalDivider(thickness = 0.5.dp)
            }
            if (entries.isEmpty()) {
                item(key = "empty-folder") {
                    GitMessageBox(MaterialSymbols.Folder, stringRes(R.string.git_repo_empty_folder), Modifier.fillParentMaxWidth())
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
                        modifier = Modifier.padding(start = 44.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
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
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringRes(R.string.git_repo_search_files)) },
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
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp)),
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
                fontFamily = FontFamily.Monospace,
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
                .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Crumb(label = stringRes(R.string.git_repo_root), current = path.isEmpty()) { onNavigate(0) }
        path.forEachIndexed { index, segment ->
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            )
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
    val background =
        if (current) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
    val textColor = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .let { if (!current) it.clickable(onClick = onClick) else it }
                .background(background)
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val symbol =
            when {
                entry.isFolder -> MaterialSymbols.Folder
                entry.isSubmodule -> MaterialSymbols.Code
                else -> MaterialSymbols.Description
            }
        val tint = if (entry.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (entry.isFolder) null else FontFamily.Monospace,
            fontWeight = if (entry.isFolder) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.isFolder) {
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            )
        }
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
