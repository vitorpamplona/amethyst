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
package com.vitorpamplona.amethyst.desktop.service.scheduledposts

import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStore
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * App-level factory for the shared [ScheduledPostStore], persisted under
 * `~/.amethyst/scheduled/scheduled_posts.json`. Mirrors the storage/permission
 * conventions of `DesktopDraftStore` (owner-only directory).
 *
 * A single instance is created at app startup and provided down the tree via
 * [LocalScheduledPostStore] so both the composer and the in-app drain timer share
 * the same store (and therefore the same in-memory `flow`).
 */
object DesktopScheduledPostStore {
    fun create(): ScheduledPostStore {
        val dir = File(System.getProperty("user.home"), ".amethyst/scheduled")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Re-apply on every launch, not just on first create — a dir left at the
        // default 0755 by an earlier build (or created by another tool) gets locked
        // down to owner-only 0700 too.
        setDirPermissions(dir)
        val file = File(dir, ScheduledPostStore.FILE_NAME)
        // Lock down a pre-existing store file up front (older builds wrote it at the
        // 0644 umask default). New writes are already 0600 in ScheduledPostStore.
        if (file.exists()) {
            setOwnerOnly(file, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        }
        return ScheduledPostStore(file)
    }

    private fun setDirPermissions(dir: File) =
        setOwnerOnly(
            dir,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )

    private fun setOwnerOnly(
        target: File,
        vararg perms: PosixFilePermission,
    ) {
        try {
            Files.setPosixFilePermissions(target.toPath(), perms.toSet())
        } catch (_: UnsupportedOperationException) {
            // Windows — no POSIX perms; confidentiality relies on the user profile dir.
        } catch (e: Exception) {
            Log.w("DesktopScheduledPostStore") { "Failed to set permissions on $target: ${e.message}" }
        }
    }
}

/**
 * Provided at [com.vitorpamplona.amethyst.desktop.App] level so any composable (the
 * composer, a future management screen) can reach the single shared store.
 */
val LocalScheduledPostStore =
    staticCompositionLocalOf<ScheduledPostStore> {
        error("LocalScheduledPostStore not provided")
    }
