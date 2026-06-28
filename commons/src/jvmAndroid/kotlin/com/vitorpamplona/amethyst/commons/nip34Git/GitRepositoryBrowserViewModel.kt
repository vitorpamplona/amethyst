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
package com.vitorpamplona.amethyst.commons.nip34Git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.git.GitHttpClient
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/** UI state for the git repository code/README browser. */
sealed interface GitBrowseState {
    object Loading : GitBrowseState

    class Loaded(
        val snapshot: GitRepoSnapshot,
    ) : GitBrowseState

    class Error(
        val message: String,
    ) : GitBrowseState
}

/**
 * Loads a shallow snapshot of a NIP-34 repository over git smart-HTTP and serves
 * both the README tab and the Code browser. The snapshot lets the UI walk
 * directories offline; [readBlob] lazily fetches file contents on demand.
 */
class GitRepositoryBrowserViewModel(
    private val okHttpClient: (String) -> OkHttpClient,
) : ViewModel() {
    private val client = GitHttpClient(okHttpClient)

    private val _state = MutableStateFlow<GitBrowseState>(GitBrowseState.Loading)
    val state = _state.asStateFlow()

    private var cloneUrls: List<String> = emptyList()
    private var started = false

    /** The branch/tag currently displayed (null = the server's default branch). */
    var currentRef: String? = null
        private set

    /**
     * Loads the repository once, using the announcement's clone URLs. Safe to call
     * on every recomposition; only the first call (after the event has arrived) runs.
     */
    fun loadOnce(cloneUrls: List<String>) {
        if (started) return
        started = true
        this.cloneUrls = cloneUrls
        reload()
    }

    /** Reloads the tree at [ref] (a branch or tag name; null = default branch). */
    fun switchRef(ref: String?) {
        if (ref == currentRef) return
        currentRef = ref
        reload()
    }

    fun reload() {
        _state.value = GitBrowseState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = candidateUrls()
            if (candidates.isEmpty()) {
                _state.value = GitBrowseState.Error(NO_CLONE_URL)
                return@launch
            }
            val errors = StringBuilder()
            for (url in candidates) {
                try {
                    val snapshot = client.open(url, currentRef)
                    _state.value = GitBrowseState.Loaded(snapshot)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors
                        .append(url)
                        .append(" → ")
                        .append(e.message ?: e.toString())
                        .append('\n')
                }
            }
            _state.value = GitBrowseState.Error(errors.toString().trim())
        }
    }

    suspend fun readBlob(
        snapshot: GitRepoSnapshot,
        oid: String,
    ): ByteArray = snapshot.readBlob(oid)

    /** Recent commits ending at the snapshot's tip, most recent first. */
    suspend fun loadHistory(snapshot: GitRepoSnapshot): List<GitCommit> = withContext(Dispatchers.IO) { client.loadHistory(snapshot.cloneUrl, snapshot.headCommit) }

    /** The diff a commit introduced (commit vs its first parent). */
    suspend fun commitDiff(
        snapshot: GitRepoSnapshot,
        commit: GitCommit,
    ): ParsedPatch =
        withContext(Dispatchers.IO) {
            client.computeDiff(snapshot.cloneUrl, commit.oid, commit.parents.firstOrNull())
        }

    /** http(s) clone URLs to try, in order, including a `.git` variant when missing. */
    private fun candidateUrls(): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in cloneUrls) {
            val url = raw.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            out.add(url)
            if (!url.removeSuffix("/").endsWith(".git")) out.add(url.removeSuffix("/") + ".git")
        }
        return out.toList()
    }

    class Factory(
        private val okHttpClient: (String) -> OkHttpClient,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GitRepositoryBrowserViewModel(okHttpClient) as T
    }

    companion object {
        const val NO_CLONE_URL = "no-clone-url"
    }
}
