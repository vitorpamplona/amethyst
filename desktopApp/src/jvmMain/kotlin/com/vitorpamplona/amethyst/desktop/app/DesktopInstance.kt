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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.desktop.service.images.DesktopImageLoaderSetup
import com.vitorpamplona.amethyst.desktop.service.media.VideoDiskCache
import com.vitorpamplona.amethyst.desktop.tor.DesktopTorManager
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Which copy of the desktop app this process is, when several run at once (a dev build beside the
 * release one, two windows on two profiles). Each copy claims a numbered slot at start, with a lock
 * file the OS releases when the process exits, crash included.
 *
 * Copies share the user's folders, and some of what lives there allows one process only:
 * - Tor locks its data directory, so a second daemon on it never starts (the second copy sat at
 *   "connecting" forever).
 * - Coil's image cache and [com.vitorpamplona.amethyst.desktop.service.media.VideoDiskCache] keep
 *   their index in memory and trim on their own, so two writers corrupt or delete each other's
 *   entries.
 * - Mining jobs checkpointed to disk were resumed by every copy that logged in, publishing the
 *   same post twice.
 *
 * So slot 1, the primary, keeps the usual folders and is the only one that checkpoints and resumes
 * mining jobs; every later slot puts the [PerCopyFolder]s under its own name ([ownFolder]) and mines
 * without checkpoints. Scheduled posts need nothing here: their drain already takes its own lock.
 *
 * When no slot can be claimed (the lock folder cannot be created, every slot is taken), the copy runs
 * as a later one, never as the primary: two primaries would each resume and publish the same post,
 * while a copy wrongly left out only starts with cold caches.
 */
object DesktopInstance {
    private const val TAG = "DesktopInstance"

    // Copies that each get their own slot; any further ones share the overflow slot past it.
    private const val MAX_SLOTS = 8

    /** The slot shared, without a lock, by copies that found none free: never the primary. */
    const val OVERFLOW_SLOT = MAX_SLOTS + 1

    /**
     * Every folder a later copy keeps its own sibling of (`videos-2`). The one list both [ownFolder]
     * and [removeIdleCopies] read, so a new per-copy folder cannot get its own copies without them
     * also being cleaned up. [isCache] folders of idle slots are deleted; Tor's are kept, a few
     * dozen MB that let a later copy connect quickly next time.
     */
    enum class PerCopyFolder(
        val isCache: Boolean,
        val primaryFolder: () -> File,
    ) {
        TOR(false, { DesktopTorManager.torDataDirectory() }),
        VIDEOS(true, { File(System.getProperty("user.home"), ".cache/amethyst-desktop/videos") }),
        VIDEO_THUMBS(true, { File(System.getProperty("user.home"), ".cache/amethyst-desktop/video-thumbs") }),
        IMAGES(true, { DesktopImageLoaderSetup.cacheDir().resolve("AmethystDesktop/image_cache") }),
    }

    /** A slot, and the lock that holds it (null for [OVERFLOW_SLOT]). */
    internal class Claim(
        val slot: Int,
        val lock: FileLock?,
    )

    // This process's claim: kept reachable for the life of the process, since a lock is released
    // when collected.
    private val claim: Claim by lazy { tryClaim(slotsDir) }

    private val slotsDir: File get() = File(System.getProperty("user.home"), ".amethyst/instances")

    /** This copy's slot: 1 for the primary, 2 and up for later copies. */
    val slot: Int get() = claim.slot

    /** Whether this is the primary copy: the one that owns the usual folders and resumes mining jobs. */
    val isPrimary: Boolean get() = slot == 1

    /** Claims this copy's slot. Called first thing in main, before anything opens a folder. */
    fun claim() {
        Log.d(TAG) { "Running as copy ${claim.slot}" }
    }

    /** [folder] for this copy: the usual one for the primary, a sibling with the slot appended for the rest. */
    fun ownFolder(folder: PerCopyFolder): File = folderFor(folder.primaryFolder(), slot)

    internal fun folderFor(
        dir: File,
        slot: Int,
    ): File = if (slot == 1) dir else File(dir.parentFile, "${dir.name}-$slot")

    /**
     * Deletes the [PerCopyFolder] caches of every slot no copy holds now. A later copy's caches fill
     * up to the same limits as the primary's (2 GB of video, 512 MB of images) and are only reused
     * when another later copy runs, so one second copy left them on disk for good.
     */
    fun removeIdleCopies() {
        removeIdleCopies(slotsDir, PerCopyFolder.entries.filter { it.isCache }.map { it.primaryFolder() })
    }

    /**
     * Deletes [folders]' copies for each idle slot in [dir]. Each slot stays locked while its folders
     * go, so a copy starting meanwhile takes the next slot instead.
     */
    internal fun removeIdleCopies(
        dir: File,
        folders: List<File>,
    ) {
        for (slot in 2..MAX_SLOTS) {
            val lock = tryLock(slotFile(dir, slot)) ?: continue
            try {
                folders.map { folderFor(it, slot) }.filter { it.exists() }.forEach { stale ->
                    if (stale.deleteRecursively()) {
                        Log.d(TAG) { "Removed $stale: no copy of Amethyst uses slot $slot" }
                    } else {
                        Log.w(TAG) { "Could not fully remove $stale" }
                    }
                }
            } finally {
                lock.release()
                lock.channel().close()
            }
        }
    }

    /**
     * Takes the lowest free slot in [dir]. Pure: the caller keeps the returned lock for as long as it
     * holds the slot. Anything that stops a claim falls back to [OVERFLOW_SLOT], never to the primary.
     */
    internal fun tryClaim(dir: File): Claim {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG) { "Cannot create $dir: running as a later copy, without mining checkpoints" }
            return Claim(OVERFLOW_SLOT, null)
        }
        for (slot in 1..MAX_SLOTS) {
            val lock = tryLock(slotFile(dir, slot)) ?: continue
            if (slot > 1) Log.i(TAG) { "Another copy of Amethyst is running: this one is copy $slot, with its own Tor and caches" }
            return Claim(slot, lock)
        }
        Log.w(TAG) { "No free slot in $dir: running as a later copy, sharing copy $OVERFLOW_SLOT's folders" }
        return Claim(OVERFLOW_SLOT, null)
    }

    private fun slotFile(
        dir: File,
        slot: Int,
    ) = File(dir, "slot-$slot.lock")

    private fun tryLock(file: File): FileLock? {
        val channel =
            try {
                FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            } catch (e: IOException) {
                Log.w(TAG, "Cannot open $file", e)
                return null
            }
        val lock =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // Held by this process already.
                null
            } catch (e: IOException) {
                Log.w(TAG, "Cannot lock $file", e)
                null
            }
        if (lock == null) channel.close()
        return lock
    }
}
