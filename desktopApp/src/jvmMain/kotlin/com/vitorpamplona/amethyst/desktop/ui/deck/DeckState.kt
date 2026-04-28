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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeckState(
    private val saveScope: CoroutineScope,
) {
    private val _columns = MutableStateFlow(DEFAULT_COLUMNS)
    val columns: StateFlow<List<DeckColumn>> = _columns.asStateFlow()

    private val _focusedColumnIndex = MutableStateFlow(0)
    val focusedColumnIndex: StateFlow<Int> = _focusedColumnIndex.asStateFlow()

    @Volatile
    private var lastKnownWidth: Float = 0f

    private var saveJob: Job? = null

    fun setAvailableWidth(width: Float) {
        lastKnownWidth = width
    }

    fun addColumn(
        type: DeckColumnType,
        afterIndex: Int? = null,
    ) {
        val col = DeckColumn(type = type)
        _columns.update { current ->
            if (afterIndex != null && afterIndex < current.size) {
                current.toMutableList().apply { add(afterIndex + 1, col) }
            } else {
                current + col
            }
        }
        // Auto-fit all columns to available width when known
        val width = lastKnownWidth
        if (width > 0f) {
            fitColumnsToWidth(width)
        } else {
            scheduleSave()
        }
    }

    fun hasColumnOfType(type: DeckColumnType): Boolean = _columns.value.any { it.type == type }

    fun focusExistingColumn(type: DeckColumnType) {
        val idx = _columns.value.indexOfFirst { it.type == type }
        if (idx >= 0) focusColumn(idx)
    }

    fun removeColumn(id: String) {
        _columns.update { current ->
            if (current.size <= 1) return
            val removed = current.find { it.id == id } ?: return
            val remaining = current.filter { it.id != id }
            val extra = removed.width / remaining.size
            val result =
                remaining.map {
                    it.copy(width = (it.width + extra).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH))
                }
            // Fix gaps: if total is less than available, redistribute remainder
            val width = lastKnownWidth
            if (width > 0f) {
                val dividers = (result.size - 1) * DIVIDER_WIDTH
                val totalUsed = result.sumOf { it.width.toDouble() }.toFloat()
                val deficit = width - dividers - totalUsed
                if (deficit > 1f) {
                    val perColumn = deficit / result.size
                    return@update result.map {
                        it.copy(width = (it.width + perColumn).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH))
                    }
                }
            }
            result
        }
        _focusedColumnIndex.update { idx ->
            idx.coerceAtMost(_columns.value.size - 1)
        }
        scheduleSave()
    }

    fun moveColumn(
        fromIndex: Int,
        toIndex: Int,
    ) {
        _columns.update { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices) return
            current.toMutableList().apply {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
        scheduleSave()
    }

    fun updateColumnWidth(
        id: String,
        width: Float,
    ) {
        _columns.update { current ->
            current.map {
                if (it.id == id) it.copy(width = width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)) else it
            }
        }
        scheduleSave()
    }

    fun expandColumn(
        id: String,
        availableWidth: Float,
    ) {
        _columns.update { cols ->
            if (cols.find { it.id == id } == null) return
            val othersMin = (cols.size - 1) * MIN_COLUMN_WIDTH
            val dividerWidth = (cols.size - 1) * DIVIDER_WIDTH
            val maxForTarget = (availableWidth - othersMin - dividerWidth).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            cols.map {
                if (it.id == id) {
                    it.copy(width = maxForTarget)
                } else {
                    it.copy(width = MIN_COLUMN_WIDTH)
                }
            }
        }
        scheduleSave()
    }

    fun resizePair(
        leftId: String,
        rightId: String,
        delta: Float,
        availableWidth: Float,
    ) {
        _columns.update { cols ->
            val left = cols.find { it.id == leftId } ?: return
            val right = cols.find { it.id == rightId } ?: return
            val otherWidth = cols.filter { it.id != leftId && it.id != rightId }.sumOf { it.width.toDouble() }.toFloat()
            val dividerWidth = (cols.size - 1) * DIVIDER_WIDTH
            val maxPairWidth = availableWidth - otherWidth - dividerWidth
            var newLeft = (left.width + delta).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            var newRight = (right.width - delta).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            if (newLeft + newRight > maxPairWidth) {
                if (delta > 0) {
                    newLeft = (maxPairWidth - newRight).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
                } else {
                    newRight = (maxPairWidth - newLeft).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
                }
            }
            cols.map {
                when (it.id) {
                    leftId -> it.copy(width = newLeft)
                    rightId -> it.copy(width = newRight)
                    else -> it
                }
            }
        }
        scheduleSave()
    }

    fun fitColumnsToWidth(availableWidth: Float) {
        _columns.update { cols ->
            if (cols.isEmpty()) return
            val dividers = (cols.size - 1) * DIVIDER_WIDTH
            val usable = availableWidth - dividers
            val perColumn = (usable / cols.size).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            cols.map { it.copy(width = perColumn) }
        }
        scheduleSave()
    }

    fun focusColumn(index: Int) {
        if (index in _columns.value.indices) {
            _focusedColumnIndex.value = index
        }
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
                _columns.value.map { col ->
                    mapOf(
                        "id" to col.id,
                        "type" to col.type.typeKey(),
                        "width" to col.width,
                        "param" to col.type.param(),
                    )
                }
            DesktopPreferences.deckColumns = mapper.writeValueAsString(data)
        } catch (e: Exception) {
            println("DeckState: failed to save columns: ${e.message}")
        }
    }

    fun load() {
        try {
            val json = DesktopPreferences.deckColumns
            if (json.isBlank()) return
            val data: List<Map<String, Any?>> = mapper.readValue(json)
            val loaded =
                data.mapNotNull { entry ->
                    val type = parseColumnType(entry) ?: return@mapNotNull null
                    val id = entry["id"] as? String ?: return@mapNotNull null
                    val width = (entry["width"] as? Number)?.toFloat() ?: 400f
                    DeckColumn(id = id, type = type, width = width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH))
                }
            if (loaded.isNotEmpty()) {
                _columns.value = loaded
            }
        } catch (e: Exception) {
            println("DeckState: failed to load columns: ${e.message}")
        }
    }

    fun loadFromWorkspace(workspaceColumns: List<Workspace.WorkspaceColumn>) {
        val loaded =
            workspaceColumns.mapNotNull { col ->
                val entry = mapOf("type" to col.typeKey, "param" to col.param)
                val type = parseColumnType(entry) ?: return@mapNotNull null
                DeckColumn(
                    type = type,
                    width = col.width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH),
                )
            }
        _columns.value = loaded.ifEmpty { DEFAULT_COLUMNS }
        _focusedColumnIndex.value = 0
    }

    companion object {
        const val MIN_COLUMN_WIDTH = 300f
        const val MAX_COLUMN_WIDTH = 800f
        const val DIVIDER_WIDTH = 12f
        private const val SAVE_DEBOUNCE_MS = 500L

        val DEFAULT_COLUMNS =
            listOf(
                DeckColumn(id = "default-home", type = DeckColumnType.HomeFeed),
                DeckColumn(id = "default-notifications", type = DeckColumnType.Notifications),
                DeckColumn(id = "default-messages", type = DeckColumnType.Messages),
            )

        private val mapper = jacksonObjectMapper()

        fun parseColumnTypeFromKey(
            typeKey: String,
            param: String? = null,
        ): DeckColumnType? = parseColumnType(mapOf("type" to typeKey, "param" to param))

        private fun parseColumnType(entry: Map<String, Any?>): DeckColumnType? {
            val typeKey = entry["type"] as? String ?: return null
            val param = entry["param"] as? String
            return when (typeKey) {
                "home" -> DeckColumnType.HomeFeed
                "notifications" -> DeckColumnType.Notifications
                "messages" -> DeckColumnType.Messages
                "search" -> DeckColumnType.Search
                "reads" -> DeckColumnType.Reads
                "bookmarks" -> DeckColumnType.Bookmarks
                "global" -> DeckColumnType.GlobalFeed
                "my_profile" -> DeckColumnType.MyProfile
                "chess" -> DeckColumnType.Chess
                "settings" -> DeckColumnType.Settings
                "relays" -> DeckColumnType.Relays
                "drafts" -> DeckColumnType.Drafts
                "highlights" -> DeckColumnType.MyHighlights
                "editor" -> DeckColumnType.Editor(param)
                "article" -> param?.let { DeckColumnType.Article(it) }
                "profile" -> param?.let { DeckColumnType.Profile(it) }
                "thread" -> param?.let { DeckColumnType.Thread(it) }
                "hashtag" -> param?.let { DeckColumnType.Hashtag(it) }
                else -> null
            }
        }
    }
}
