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
package com.vitorpamplona.amethyst.desktop.ui.deck

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.LayoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkspaceManager(
    private val saveScope: CoroutineScope,
) {
    private val _workspaces = MutableStateFlow(listOf(DEFAULT_WORKSPACE))
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    val activeWorkspace: Workspace
        get() = _workspaces.value.getOrElse(_activeIndex.value) { _workspaces.value.first() }

    private var saveJob: Job? = null

    fun switchTo(index: Int): Workspace? {
        if (index !in _workspaces.value.indices) return null
        _activeIndex.value = index
        scheduleSave()
        return activeWorkspace
    }

    fun saveCurrentColumns(columns: List<DeckColumn>) {
        _workspaces.update { wsList ->
            wsList.mapIndexed { idx, ws ->
                if (idx == _activeIndex.value) {
                    ws.copy(
                        columns =
                            columns.map { col ->
                                Workspace.WorkspaceColumn(
                                    typeKey = col.type.typeKey(),
                                    param =
                                        when (col.type) {
                                            is DeckColumnType.Profile -> col.type.pubKeyHex
                                            is DeckColumnType.Thread -> col.type.noteId
                                            is DeckColumnType.Hashtag -> col.type.tag
                                            is DeckColumnType.Editor -> col.type.draftSlug
                                            is DeckColumnType.Article -> col.type.addressTag
                                            else -> null
                                        },
                                    width = col.width,
                                )
                            },
                    )
                } else {
                    ws
                }
            }
        }
        scheduleSave()
    }

    fun addWorkspace(workspace: Workspace) {
        if (_workspaces.value.size >= MAX_WORKSPACES) return
        _workspaces.update { it + workspace }
        scheduleSave()
    }

    fun updateWorkspace(workspace: Workspace) {
        _workspaces.update { wsList ->
            wsList.map { if (it.id == workspace.id) workspace else it }
        }
        scheduleSave()
    }

    fun deleteWorkspace(id: String) {
        if (_workspaces.value.size <= 1) return
        val deletedIdx = _workspaces.value.indexOfFirst { it.id == id }
        if (deletedIdx < 0) return
        _workspaces.update { it.filter { ws -> ws.id != id } }
        _activeIndex.value =
            when {
                deletedIdx < _activeIndex.value -> _activeIndex.value - 1
                deletedIdx == _activeIndex.value -> 0
                else -> _activeIndex.value
            }.coerceIn(_workspaces.value.indices)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob =
            saveScope.launch {
                delay(SAVE_DEBOUNCE_MS)
                save()
            }
    }

    fun save() {
        try {
            val data =
                mapOf(
                    "activeIndex" to _activeIndex.value,
                    "workspaces" to
                        _workspaces.value.map { ws ->
                            mapOf(
                                "id" to ws.id,
                                "name" to ws.name,
                                "iconName" to ws.iconName,
                                "layoutMode" to ws.layoutMode.name,
                                "singlePaneScreen" to ws.singlePaneScreen,
                                "columns" to
                                    ws.columns.map { col ->
                                        mapOf(
                                            "typeKey" to col.typeKey,
                                            "param" to col.param,
                                            "width" to col.width,
                                        )
                                    },
                            )
                        },
                )
            DesktopPreferences.workspaces = mapper.writeValueAsString(data)
        } catch (e: Exception) {
            println("WorkspaceManager: failed to save: ${e.message}")
        }
    }

    fun load() {
        try {
            val json = DesktopPreferences.workspaces
            if (json.isBlank()) return
            val data: Map<String, Any?> = mapper.readValue(json)
            val activeIdx = (data["activeIndex"] as? Number)?.toInt() ?: 0

            @Suppress("UNCHECKED_CAST")
            val wsList = data["workspaces"] as? List<Map<String, Any?>> ?: return
            val loaded =
                wsList.mapNotNull { entry ->
                    try {
                        val name = entry["name"] as? String ?: return@mapNotNull null
                        val iconName = entry["iconName"] as? String ?: "Home"
                        val layoutMode =
                            try {
                                LayoutMode.valueOf(entry["layoutMode"] as? String ?: "DECK")
                            } catch (e: Exception) {
                                LayoutMode.DECK
                            }
                        val singlePaneScreen = entry["singlePaneScreen"] as? String

                        @Suppress("UNCHECKED_CAST")
                        val columns =
                            (entry["columns"] as? List<Map<String, Any?>>)?.map { col ->
                                Workspace.WorkspaceColumn(
                                    typeKey = col["typeKey"] as? String ?: "home",
                                    param = col["param"] as? String,
                                    width = (col["width"] as? Number)?.toFloat() ?: 400f,
                                )
                            } ?: emptyList()

                        Workspace(
                            id =
                                entry["id"] as? String ?: java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            name = name,
                            iconName = iconName,
                            layoutMode = layoutMode,
                            columns = columns,
                            singlePaneScreen = singlePaneScreen,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            if (loaded.isNotEmpty()) {
                _workspaces.value = loaded
                _activeIndex.value = activeIdx.coerceIn(loaded.indices)
            }
        } catch (e: Exception) {
            println("WorkspaceManager: failed to load: ${e.message}")
        }
    }

    companion object {
        const val MAX_WORKSPACES = 9
        private const val SAVE_DEBOUNCE_MS = 500L
        private val mapper = jacksonObjectMapper()

        val DEFAULT_WORKSPACE =
            Workspace(
                id = "default-social",
                name = "Social",
                iconName = "Groups",
                layoutMode = LayoutMode.DECK,
                columns =
                    listOf(
                        Workspace.WorkspaceColumn("home"),
                        Workspace.WorkspaceColumn("notifications"),
                        Workspace.WorkspaceColumn("messages"),
                    ),
            )
    }
}
