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
        save()
    }

    fun removeColumn(id: String) {
        if (_columns.value.size <= 1) return
        _columns.value = _columns.value.filter { it.id != id }
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
                "settings" -> DeckColumnType.Settings
                "profile" -> param?.let { DeckColumnType.Profile(it) }
                "thread" -> param?.let { DeckColumnType.Thread(it) }
                "hashtag" -> param?.let { DeckColumnType.Hashtag(it) }
                else -> null
            }
        }
    }
}
