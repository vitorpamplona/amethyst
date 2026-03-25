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
package com.vitorpamplona.amethyst.desktop.service.drafts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant

data class DraftMetadata(
    val title: String = "",
    val summary: String? = null,
    val image: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val published: Boolean = false,
)

data class DraftEntry(
    val slug: String,
    val metadata: DraftMetadata,
)

/**
 * Local draft storage for long-form articles.
 * Stores markdown content as .md files and metadata in index.json.
 * Uses atomic writes and restrictive file permissions.
 */
class DesktopDraftStore(
    private val scope: CoroutineScope,
) {
    private val mapper = jacksonObjectMapper()
    private val mutex = Mutex()
    private var cachedIndex: MutableMap<String, DraftMetadata>? = null

    private val _drafts = MutableStateFlow<List<DraftEntry>>(emptyList())
    val drafts: StateFlow<List<DraftEntry>> = _drafts.asStateFlow()

    private val draftsDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".amethyst/drafts")
        if (!dir.exists()) {
            dir.mkdirs()
            setDirPermissions(dir)
        }
        dir
    }

    private val indexFile: File get() = File(draftsDir, "index.json")

    init {
        scope.launch(Dispatchers.IO) {
            cachedIndex = null
            loadIndex()
        }
    }

    /**
     * Sanitizes a slug to prevent path traversal and ensure safe filenames.
     */
    private fun sanitizeSlug(slug: String): String {
        val sanitized =
            slug
                .replace("/", "")
                .replace("\\", "")
                .replace("\u0000", "")
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_-]"), "-")
                .replace(Regex("-+"), "-")
                .trimStart('-')
                .trimEnd('-')
                .take(128)

        require(sanitized.isNotEmpty()) { "Slug cannot be empty after sanitization" }

        // Validate canonical path stays within drafts dir
        val resolved = File(draftsDir, "$sanitized.md").canonicalPath
        require(resolved.startsWith(draftsDir.canonicalPath)) {
            "Slug resolves outside drafts directory"
        }

        return sanitized
    }

    /**
     * Generates a slug from a title. Falls back to timestamp if title is blank.
     */
    fun slugFromTitle(title: String): String {
        if (title.isBlank()) return "untitled-${Instant.now().epochSecond}"
        return sanitizeSlug(title)
    }

    /**
     * Saves or updates a draft. Creates content file and updates index atomically.
     */
    suspend fun saveDraft(
        slug: String,
        content: String,
        metadata: DraftMetadata,
    ) {
        val safeSlug = sanitizeSlug(slug)

        mutex.withLock {
            // Write content file atomically
            val contentFile = File(draftsDir, "$safeSlug.md")
            atomicWrite(contentFile, content)

            // Update index
            val index = loadIndexMap()
            index[safeSlug] = metadata.copy(updatedAt = Instant.now().toString())
            atomicWriteIndex(index)
            cachedIndex = index

            // Refresh state
            _drafts.value =
                index.entries
                    .map { DraftEntry(it.key, it.value) }
                    .sortedByDescending { it.metadata.updatedAt }
        }
    }

    /**
     * Loads a draft's content by slug.
     */
    suspend fun loadContent(slug: String): String? {
        val safeSlug = sanitizeSlug(slug)
        val file = File(draftsDir, "$safeSlug.md")
        return if (file.exists()) file.readText() else null
    }

    /**
     * Loads a draft's metadata by slug.
     */
    suspend fun loadMetadata(slug: String): DraftMetadata? {
        val safeSlug = sanitizeSlug(slug)
        return mutex.withLock {
            loadIndexMap()[safeSlug]
        }
    }

    /**
     * Deletes a draft by slug.
     */
    suspend fun deleteDraft(slug: String) {
        val safeSlug = sanitizeSlug(slug)
        mutex.withLock {
            File(draftsDir, "$safeSlug.md").delete()

            val index = loadIndexMap()
            index.remove(safeSlug)
            atomicWriteIndex(index)
            cachedIndex = index

            _drafts.value =
                index.entries
                    .map { DraftEntry(it.key, it.value) }
                    .sortedByDescending { it.metadata.updatedAt }
        }
    }

    /**
     * Marks a draft as published.
     */
    suspend fun markPublished(slug: String) {
        val safeSlug = sanitizeSlug(slug)
        mutex.withLock {
            val index = loadIndexMap()
            val existing = index[safeSlug] ?: return
            index[safeSlug] = existing.copy(published = true, updatedAt = Instant.now().toString())
            atomicWriteIndex(index)
            cachedIndex = index

            _drafts.value =
                index.entries
                    .map { DraftEntry(it.key, it.value) }
                    .sortedByDescending { it.metadata.updatedAt }
        }
    }

    private fun loadIndexMap(): MutableMap<String, DraftMetadata> {
        cachedIndex?.let { return it }
        val loaded: MutableMap<String, DraftMetadata> =
            if (!indexFile.exists()) {
                mutableMapOf()
            } else {
                try {
                    mapper.readValue<MutableMap<String, DraftMetadata>>(indexFile)
                } catch (e: Exception) {
                    System.err.println("Failed to read drafts index: ${e.message}")
                    mutableMapOf()
                }
            }
        cachedIndex = loaded
        return loaded
    }

    private suspend fun loadIndex() {
        mutex.withLock {
            _drafts.value =
                loadIndexMap()
                    .entries
                    .map { DraftEntry(it.key, it.value) }
                    .sortedByDescending { it.metadata.updatedAt }
        }
    }

    private fun atomicWrite(
        file: File,
        content: String,
    ) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            tempFile.writeText(content)
            setFilePermissions(tempFile)
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun atomicWriteIndex(index: Map<String, DraftMetadata>) {
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(index)
        atomicWrite(indexFile, json)
    }

    private fun setDirPermissions(dir: File) {
        try {
            Files.setPosixFilePermissions(
                dir.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows
        }
    }

    private fun setFilePermissions(file: File) {
        try {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows
        }
    }
}
