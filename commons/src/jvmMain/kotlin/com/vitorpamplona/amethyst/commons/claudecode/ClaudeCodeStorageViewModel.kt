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
package com.vitorpamplona.amethyst.commons.claudecode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A workspace on the storage screen: what it takes, and whether its work is done. */
@Immutable
data class WorkspaceUse(
    val workspace: AgentWorkspace,
    val bytes: Long,
    /** Every pull request merged or closed. */
    val finished: Boolean,
    /** Uncommitted files, or commits on no remote: cleaning up would lose them, so it doesn't. */
    val unsaved: Boolean,
)

/** What workspaces and mirrors take on this computer. */
@Immutable
data class StorageState(
    val workspaces: List<WorkspaceUse>,
    val mirrors: Int,
    val mirrorsBytes: Long,
    val unusedMirrors: Int,
) {
    val workspacesBytes: Long get() = workspaces.sumOf { it.bytes }

    /** The finished workspaces a cleanup would remove: those with nothing unsaved. */
    val removable: List<WorkspaceUse> get() = workspaces.filter { it.finished && !it.unsaved }
}

/** What the last action did, for the line under it. */
@Immutable
sealed interface StorageResult {
    data class Cleaned(
        val workspaces: Int,
        val freed: Long,
    ) : StorageResult

    data class Tidied(
        val removedMirrors: Int,
        val freed: Long,
    ) : StorageResult
}

/** The storage screen: how much workspaces and mirrors take, and cleaning them up. */
class ClaudeCodeStorageViewModel(
    private val service: ClaudeCodeService,
) : ViewModel() {
    private val workspaces = service.agentWorkspaces

    val preferences = service.preferences

    private val _state = MutableStateFlow<StorageState?>(null)

    /** Null while it is measured. */
    val state: StateFlow<StorageState?> = _state.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _result = MutableStateFlow<StorageResult?>(null)
    val result: StateFlow<StorageResult?> = _result.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _state.value = measure() }
    }

    private suspend fun measure(): StorageState {
        val uses =
            workspaces.list().map { ws ->
                val finished = ws.finishedAt != null
                WorkspaceUse(ws, service.measure(ws), finished, finished && workspaces.hasUnsavedWork(ws))
            }
        val mirrorDirs = workspaces.mirrorsDir.listFiles { f -> f.isDirectory && f.name.endsWith(".git") }.orEmpty()
        return StorageState(uses, mirrorDirs.size, workspaces.mirrorsSize(), workspaces.unusedMirrors().size)
    }

    fun setAutoCleanup(on: Boolean) = service.setAutoCleanup(on)

    /** Removes the finished workspaces' clones; their conversations stay, archived. */
    fun cleanUpFinished() {
        val removable = _state.value?.removable ?: return
        if (removable.isEmpty() || _working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                val removed = service.cleanUp(removable.map { it.workspace }).map { it.id }.toSet()
                _result.value = StorageResult.Cleaned(removed.size, removable.filter { it.workspace.id in removed }.sumOf { it.bytes })
                _state.value = measure()
            } finally {
                _working.value = false
            }
        }
    }

    /** Compacts the mirrors and removes the unused ones. */
    fun tidyMirrors() {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                val before = workspaces.mirrorsSize()
                val removed = workspaces.tidyMirrors()
                _result.value = StorageResult.Tidied(removed, (before - workspaces.mirrorsSize()).coerceAtLeast(0))
                _state.value = measure()
            } finally {
                _working.value = false
            }
        }
    }
}
