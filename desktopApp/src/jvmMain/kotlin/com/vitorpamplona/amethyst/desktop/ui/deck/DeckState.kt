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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeckState {
    private val _columns = MutableStateFlow(DEFAULT_COLUMNS)
    val columns: StateFlow<List<DeckColumn>> = _columns.asStateFlow()

    private val _focusedColumnIndex = MutableStateFlow(0)
    val focusedColumnIndex: StateFlow<Int> = _focusedColumnIndex.asStateFlow()

    private var lastKnownWidth: Float = 0f

    fun setAvailableWidth(width: Float) {
        lastKnownWidth = width
    }

    fun addColumn(
        type: DeckColumnType,
        afterIndex: Int? = null,
    ) {
        val col = DeckColumn(type = type)
        _columns.value =
            if (afterIndex != null && afterIndex < _columns.value.size) {
                _columns.value.toMutableList().apply { add(afterIndex + 1, col) }
            } else {
                _columns.value + col
            }
        // Auto-fit all columns to available width when known
        if (lastKnownWidth > 0f) {
            fitColumnsToWidth(lastKnownWidth)
        } else {
            save()
        }
    }

    fun removeColumn(id: String) {
        if (_columns.value.size <= 1) return
        val removed = _columns.value.find { it.id == id } ?: return
        val remaining = _columns.value.filter { it.id != id }
        // Redistribute removed column's width evenly across remaining columns
        val extra = removed.width / remaining.size
        _columns.value =
            remaining.map {
                it.copy(width = (it.width + extra).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH))
            }
        if (_focusedColumnIndex.value >= _columns.value.size) {
            _focusedColumnIndex.value = _columns.value.size - 1
        }
        save()
    }

    fun moveColumn(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val list = _columns.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _columns.value = list
        save()
    }

    fun updateColumnWidth(
        id: String,
        width: Float,
    ) {
        _columns.value =
            _columns.value.map {
                if (it.id == id) it.copy(width = width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)) else it
            }
        save()
    }

    fun expandColumn(
        id: String,
        availableWidth: Float,
    ) {
        val cols = _columns.value
        val target = cols.find { it.id == id } ?: return
        val others = cols.filter { it.id != id }
        // Shrink others to minimum, give the rest to the target
        val othersMin = others.size * MIN_COLUMN_WIDTH
        val dividerWidth = (cols.size - 1) * DIVIDER_WIDTH
        val maxForTarget = (availableWidth - othersMin - dividerWidth).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
        _columns.value =
            cols.map {
                if (it.id == id) {
                    it.copy(width = maxForTarget)
                } else {
                    it.copy(width = MIN_COLUMN_WIDTH)
                }
            }
        save()
    }

    fun resizePair(
        leftId: String,
        rightId: String,
        delta: Float,
        availableWidth: Float,
    ) {
        val cols = _columns.value
        val left = cols.find { it.id == leftId } ?: return
        val right = cols.find { it.id == rightId } ?: return
        // Compute max total allowed (available minus other columns and dividers)
        val otherWidth = cols.filter { it.id != leftId && it.id != rightId }.sumOf { it.width.toDouble() }.toFloat()
        val dividerWidth = (cols.size - 1) * DIVIDER_WIDTH
        val maxPairWidth = availableWidth - otherWidth - dividerWidth
        var newLeft = (left.width + delta).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
        var newRight = (right.width - delta).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
        // Ensure pair doesn't exceed available space
        if (newLeft + newRight > maxPairWidth) {
            if (delta > 0) {
                newLeft = (maxPairWidth - newRight).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            } else {
                newRight = (maxPairWidth - newLeft).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
            }
        }
        _columns.value =
            cols.map {
                when (it.id) {
                    leftId -> it.copy(width = newLeft)
                    rightId -> it.copy(width = newRight)
                    else -> it
                }
            }
        save()
    }

    fun fitColumnsToWidth(availableWidth: Float) {
        val cols = _columns.value
        if (cols.isEmpty()) return
        val dividers = (cols.size - 1) * DIVIDER_WIDTH
        val usable = availableWidth - dividers
        val perColumn = (usable / cols.size).coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
        _columns.value = cols.map { it.copy(width = perColumn) }
        save()
    }

    fun focusColumn(index: Int) {
        if (index in _columns.value.indices) {
            _focusedColumnIndex.value = index
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
                        "param" to
                            when (col.type) {
                                is DeckColumnType.Profile -> col.type.pubKeyHex
                                is DeckColumnType.Thread -> col.type.noteId
                                is DeckColumnType.Hashtag -> col.type.tag
                                else -> null
                            },
                    )
                }
            DesktopPreferences.deckColumns = mapper.writeValueAsString(data)
        } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }

    companion object {
        const val MIN_COLUMN_WIDTH = 300f
        const val MAX_COLUMN_WIDTH = 800f
        const val DIVIDER_WIDTH = 4f

        val DEFAULT_COLUMNS =
            listOf(
                DeckColumn(type = DeckColumnType.HomeFeed),
                DeckColumn(type = DeckColumnType.Notifications),
                DeckColumn(type = DeckColumnType.Messages),
            )

        private val mapper = jacksonObjectMapper()

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
                "profile" -> param?.let { DeckColumnType.Profile(it) }
                "thread" -> param?.let { DeckColumnType.Thread(it) }
                "hashtag" -> param?.let { DeckColumnType.Hashtag(it) }
                else -> null
            }
        }
    }
}
