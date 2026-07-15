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

import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * A single short-note draft row.
 *
 * @param dTag the stable NIP-37 replaceable identifier. Used both as the local key
 *   AND as the `d` tag of the kind-31234 sync event so a local row and its synced
 *   twin dedup to one entry.
 * @param content the plaintext note body the composer was holding.
 * @param updatedAt epoch-second of last save.
 * @param synced whether this draft was also published as an encrypted NIP-37 event.
 * @param accountPubkey hex pubkey of the account that authored the draft. Scopes the
 *   draft to its owner so account A's plaintext drafts never surface — or publish —
 *   under account B. Empty on legacy rows written before this field existed; those are
 *   scoped out (hidden from every account) since we can't attribute them safely.
 */
data class NoteDraft(
    val dTag: String,
    val content: String = "",
    val updatedAt: Long = 0L,
    val synced: Boolean = false,
    val accountPubkey: String = "",
)

/**
 * Local, per-machine storage for short-note (kind-1) drafts written from the compose
 * dialog. Mirrors [DesktopDraftStore]'s JSON + [Mutex] + owner-only (0600) file pattern
 * but is keyed by NIP-37 `dTag` instead of an article slug.
 *
 * This store is intentionally simple: one `notes.json` map of `dTag -> NoteDraft`.
 * NIP-37 *sync* (publishing a kind-31234 event) is orthogonal — this store only records
 * whether a given draft was synced so the UI can badge it.
 */
class DesktopNoteDraftStore(
    private val scope: CoroutineScope,
) {
    private val mapper = jacksonObjectMapper()
    private val mutex = Mutex()
    private var cached: MutableMap<String, NoteDraft>? = null

    private val _drafts = MutableStateFlow<List<NoteDraft>>(emptyList())
    val drafts: StateFlow<List<NoteDraft>> = _drafts.asStateFlow()

    private val draftsDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".amethyst/note-drafts")
        if (!dir.exists()) {
            dir.mkdirs()
            setDirPermissions(dir)
        }
        dir
    }

    private val indexFile: File get() = File(draftsDir, "notes.json")

    init {
        scope.launch(Dispatchers.IO) {
            cached = null
            load()
        }
    }

    /**
     * Saves or updates a draft under [dTag]. Reusing an existing dTag overwrites in place.
     */
    suspend fun save(draft: NoteDraft) {
        mutex.withLock {
            val map = loadMap()
            map[draft.dTag] = draft
            atomicWriteIndex(map)
            cached = map
            publish(map)
        }
    }

    suspend fun delete(dTag: String) {
        mutex.withLock {
            val map = loadMap()
            map.remove(dTag)
            atomicWriteIndex(map)
            cached = map
            publish(map)
        }
    }

    suspend fun load(dTag: String): NoteDraft? =
        mutex.withLock {
            loadMap()[dTag]
        }

    private fun publish(map: Map<String, NoteDraft>) {
        _drafts.value = map.values.sortedByDescending { it.updatedAt }
    }

    private suspend fun load() {
        mutex.withLock { publish(loadMap()) }
    }

    private fun loadMap(): MutableMap<String, NoteDraft> {
        cached?.let { return it }
        val loaded: MutableMap<String, NoteDraft> =
            if (!indexFile.exists()) {
                mutableMapOf()
            } else {
                try {
                    mapper.readValue<MutableMap<String, NoteDraft>>(indexFile)
                } catch (e: Exception) {
                    System.err.println("Failed to read note drafts index: ${e.message}")
                    mutableMapOf()
                }
            }
        cached = loaded
        return loaded
    }

    private fun atomicWriteIndex(map: Map<String, NoteDraft>) {
        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)
        atomicWrite(indexFile, json)
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

/**
 * Provided at [com.vitorpamplona.amethyst.desktop.App] level so the composer (writer)
 * and the Drafts tab (reader) share one store instance.
 */
val LocalNoteDraftStore =
    staticCompositionLocalOf<DesktopNoteDraftStore> {
        error("LocalNoteDraftStore not provided")
    }
