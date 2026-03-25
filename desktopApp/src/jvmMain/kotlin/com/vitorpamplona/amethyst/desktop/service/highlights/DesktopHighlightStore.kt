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
package com.vitorpamplona.amethyst.desktop.service.highlights

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.commons.model.highlights.HighlightData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/**
 * Local highlight storage for article annotations.
 * Stores highlights as JSON in ~/.amethyst/highlights/index.json.
 * Uses atomic writes following the same pattern as DesktopDraftStore.
 */
class DesktopHighlightStore(
    private val scope: CoroutineScope,
) {
    private val mapper = jacksonObjectMapper()
    private val mutex = Mutex()

    private val _highlights = MutableStateFlow<Map<String, List<HighlightData>>>(emptyMap())
    val highlights: StateFlow<Map<String, List<HighlightData>>> = _highlights.asStateFlow()

    private val highlightsDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".amethyst/highlights")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val indexFile: File get() = File(highlightsDir, "index.json")

    init {
        scope.launch(Dispatchers.IO) {
            loadIndex()
        }
    }

    suspend fun addHighlight(
        articleAddressTag: String,
        text: String,
        note: String?,
        articleTitle: String?,
    ) {
        mutex.withLock {
            val current = _highlights.value.toMutableMap()
            val articleHighlights = current.getOrDefault(articleAddressTag, emptyList()).toMutableList()

            // Avoid duplicate highlights of the same text
            if (articleHighlights.any { it.text == text }) return

            articleHighlights.add(
                HighlightData(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    note = note,
                    articleAddressTag = articleAddressTag,
                    articleTitle = articleTitle,
                    createdAt = Instant.now().epochSecond,
                ),
            )
            current[articleAddressTag] = articleHighlights
            _highlights.value = current
            saveIndex(current)
        }
    }

    suspend fun updateNote(
        highlightId: String,
        note: String,
    ) {
        mutex.withLock {
            val current = _highlights.value.toMutableMap()
            for ((key, list) in current) {
                val idx = list.indexOfFirst { it.id == highlightId }
                if (idx >= 0) {
                    current[key] =
                        list.toMutableList().apply {
                            set(idx, get(idx).copy(note = note))
                        }
                    _highlights.value = current
                    saveIndex(current)
                    return
                }
            }
        }
    }

    suspend fun removeHighlight(highlightId: String) {
        mutex.withLock {
            val current = _highlights.value.toMutableMap()
            for ((key, list) in current) {
                val filtered = list.filter { it.id != highlightId }
                if (filtered.size != list.size) {
                    if (filtered.isEmpty()) {
                        current.remove(key)
                    } else {
                        current[key] = filtered
                    }
                    _highlights.value = current
                    saveIndex(current)
                    return
                }
            }
        }
    }

    suspend fun markPublished(
        highlightId: String,
        eventId: String,
    ) {
        mutex.withLock {
            val current = _highlights.value.toMutableMap()
            for ((key, list) in current) {
                val idx = list.indexOfFirst { it.id == highlightId }
                if (idx >= 0) {
                    current[key] =
                        list.toMutableList().apply {
                            set(idx, get(idx).copy(published = true, eventId = eventId))
                        }
                    _highlights.value = current
                    saveIndex(current)
                    return
                }
            }
        }
    }

    fun getHighlightsForArticle(addressTag: String): List<HighlightData> = _highlights.value[addressTag] ?: emptyList()

    fun getAllHighlights(): Map<String, List<HighlightData>> = _highlights.value

    private suspend fun loadIndex() {
        mutex.withLock {
            if (indexFile.exists()) {
                try {
                    val data: Map<String, List<HighlightData>> = mapper.readValue(indexFile)
                    _highlights.value = data
                } catch (e: Exception) {
                    // Corrupted file — start fresh
                    _highlights.value = emptyMap()
                }
            }
        }
    }

    private fun saveIndex(data: Map<String, List<HighlightData>>) {
        try {
            val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data)
            val tempFile = File(highlightsDir, "index.json.tmp")
            tempFile.writeText(json)
            Files.move(
                tempFile.toPath(),
                indexFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            // Best effort — don't crash on write failure
        }
    }
}
