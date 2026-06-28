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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
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
            CenteredStatus(stringRes(R.string.git_repo_code_loading), Modifier.padding(scaffoldPadding))

        is GitBrowseState.Error ->
            CenteredStatus(
                text =
                    if (state.message == GitRepositoryBrowserViewModel.NO_CLONE_URL) {
                        stringRes(R.string.git_repo_no_clone_url)
                    } else {
                        stringRes(R.string.git_repo_code_error)
                    },
                modifier = Modifier.padding(scaffoldPadding),
                onRetry = if (state.message == GitRepositoryBrowserViewModel.NO_CLONE_URL) null else viewModel::reload,
            )

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

    val path = remember(pathString) { if (pathString.isEmpty()) emptyList() else pathString.split("/") }
    val openPath = openFilePath?.let { if (it.isEmpty()) emptyList() else it.split("/") }

    if (openPath != null) {
        val entry = remember(openFilePath) { snapshot.entryAt(openPath) }
        BackHandler { openFilePath = null }
        Column(Modifier.fillMaxSize().padding(scaffoldPaddingTop)) {
            FileHeader(name = openPath.lastOrNull() ?: "", onBack = { openFilePath = null })
            HorizontalDivider(thickness = 0.5.dp)
            if (entry == null) {
                CenteredStatus(stringRes(R.string.git_repo_file_load_error), Modifier)
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

    BackHandler(enabled = path.isNotEmpty()) { pathString = path.dropLast(1).joinToString("/") }

    val entries = remember(snapshot, pathString) { snapshot.entriesAt(path).orEmpty() }

    Column(Modifier.fillMaxSize().padding(scaffoldPaddingTop)) {
        Breadcrumb(
            path = path,
            onNavigate = { depth -> pathString = path.take(depth).joinToString("/") },
        )
        HorizontalDivider(thickness = 0.5.dp)
        if (entries.isEmpty()) {
            CenteredStatus(stringRes(R.string.git_repo_empty_folder), Modifier)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.name }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = {
                            val child = (path + entry.name).joinToString("/")
                            if (entry.isFolder) pathString = child else openFilePath = child
                        },
                    )
                }
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(label = stringRes(R.string.git_repo_root), enabled = path.isNotEmpty()) { onNavigate(0) }
        path.forEachIndexed { index, segment ->
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            )
            Crumb(label = segment, enabled = index < path.lastIndex) { onNavigate(index + 1) }
        }
    }
}

@Composable
private fun Crumb(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (enabled) FontWeight.Normal else FontWeight.SemiBold,
        color =
            if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        maxLines = 1,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val symbol =
            when {
                entry.isFolder -> MaterialSymbols.Folder
                entry.isSubmodule -> MaterialSymbols.Code
                else -> MaterialSymbols.Description
            }
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint =
                if (entry.isFolder) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                },
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenteredStatus(
    text: String,
    modifier: Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            if (onRetry != null) {
                Button(onClick = onRetry) { Text(stringRes(R.string.git_repo_retry)) }
            }
        }
    }
}
