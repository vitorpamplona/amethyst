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
package com.vitorpamplona.geode.persistence

import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * On-disk snapshot of the relay's *operator-mutable* state — the
 * NIP-11 info doc (so `changerelayname/description/icon` survive a
 * restart) and the NIP-86 ban / allow / kind lists.
 *
 * One JSON file per relay. Lives next to the SQLite event store by
 * convention, but the path is configurable independently. Atomic
 * write via temp + atomic rename so a crash mid-save can never leave
 * the file half-written.
 *
 * The schema below intentionally mirrors NIP-86 list responses
 * (`pubkey + reason`, `id + reason`) so a future operator-tools CLI
 * can read these straight from disk without translation.
 */
class RelayStateStore(
    val file: File,
) {
    /** Load the snapshot from disk, or `null` if the file does not yet exist. */
    @Synchronized
    fun load(): RelayPersistedState? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(RelayPersistedState.serializer(), file.readText())
        } catch (e: Exception) {
            // Corrupt file — log to stderr and refuse to overwrite. The
            // operator chooses whether to fix or delete; we don't blow
            // away their state silently.
            System.err.println("warning: failed to read relay state file ${file.absolutePath}: ${e.message}")
            null
        }
    }

    /** Atomically write the snapshot. */
    @Synchronized
    fun save(state: RelayPersistedState) {
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(file.parentFile ?: file.absoluteFile.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(RelayPersistedState.serializer(), state))
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
}

@Serializable
data class RelayPersistedState(
    val info: Nip11RelayInformation? = null,
    val bannedPubkeys: List<BannedEntry> = emptyList(),
    val allowedPubkeys: List<BannedEntry> = emptyList(),
    val bannedEvents: List<BannedEntry> = emptyList(),
    val allowedKinds: List<Int> = emptyList(),
    val disallowedKinds: List<Int> = emptyList(),
)

@Serializable
data class BannedEntry(
    val key: String,
    val reason: String? = null,
)
