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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.git.GitHttpClient
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private sealed interface ChangesState {
    object Idle : ChangesState

    object Loading : ChangesState

    class Loaded(
        val patch: ParsedPatch,
    ) : ChangesState

    object Failed : ChangesState
}

/**
 * "View changes" section of a pull-request card. A NIP-34 PR references a clone
 * URL + commit instead of embedding a patch, so the actual diff is computed on
 * demand over the git smart-HTTP client (base = merge base, head = current
 * commit) and rendered with the shared [GitDiffView].
 */
@Composable
fun GitPullRequestChanges(
    cloneUrls: List<String>,
    headCommit: String?,
    mergeBase: String?,
    accountViewModel: AccountViewModel,
) {
    val candidates = remember(cloneUrls) { candidateUrls(cloneUrls) }
    if (candidates.isEmpty() || headCommit.isNullOrBlank()) return

    var state by remember(headCommit, mergeBase) { mutableStateOf<ChangesState>(ChangesState.Idle) }
    val scope = rememberCoroutineScope()

    fun load() {
        state = ChangesState.Loading
        scope.launch {
            state =
                try {
                    val patch = loadDiff(accountViewModel, candidates, headCommit, mergeBase)
                    ChangesState.Loaded(patch)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ChangesState.Failed
                }
        }
    }

    when (val s = state) {
        ChangesState.Idle ->
            FilledTonalButton(
                onClick = { load() },
                modifier = Modifier.padding(top = 8.dp).then(CompactButtonHeight),
                contentPadding = CompactButtonPadding,
            ) {
                Icon(MaterialSymbols.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(R.string.git_pr_view_changes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }

        ChangesState.Loading ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(stringRes(R.string.git_pr_loading_changes), style = MaterialTheme.typography.bodySmall)
            }

        is ChangesState.Loaded ->
            if (s.patch.hasDiff) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    GitDiffView(s.patch, Modifier.fillMaxWidth())
                }
            } else {
                Text(
                    text = stringRes(R.string.git_pr_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

        ChangesState.Failed ->
            FilledTonalButton(
                onClick = { load() },
                modifier = Modifier.padding(top = 8.dp).then(CompactButtonHeight),
                contentPadding = CompactButtonPadding,
            ) {
                Icon(MaterialSymbols.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(R.string.git_pr_changes_retry), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
    }
}

private suspend fun loadDiff(
    accountViewModel: AccountViewModel,
    candidates: List<String>,
    headCommit: String,
    mergeBase: String?,
): ParsedPatch =
    withContext(Dispatchers.IO) {
        val client = GitHttpClient(accountViewModel.httpClientBuilder::okHttpClientForPreview)
        var lastError: Exception? = null
        for (url in candidates) {
            try {
                return@withContext client.computeDiff(url, headCommit, mergeBase?.takeIf { it.isNotBlank() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no usable clone URL")
    }

private fun candidateUrls(cloneUrls: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    for (raw in cloneUrls) {
        val url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) continue
        out.add(url)
        if (!url.removeSuffix("/").endsWith(".git")) out.add(url.removeSuffix("/") + ".git")
    }
    return out.toList()
}
