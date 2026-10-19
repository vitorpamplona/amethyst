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
 * mining jobs; every later slot puts those folders under its own name ([ownFolder]) and mines
 * without checkpoints. Scheduled posts need nothing here: their drain already takes its own lock.
 */
object DesktopInstance {
    private const val TAG = "DesktopInstance"

    // Copies that each get their own slot; any further ones share the overflow slot past it.
    private const val MAX_SLOTS = 8

    // The claim this process holds: kept reachable, since a lock is released when collected.
    @Volatile private var held: FileLock? = null

    private val slotsDir: File by lazy { File(System.getProperty("user.home"), ".amethyst/instances") }

    /** This copy's slot: 1 for the primary, 2 and up for later copies. Claimed on first use. */
    val slot: Int by lazy { claimSlot(slotsDir) }

    /** Whether this is the primary copy: the one that owns the usual folders and resumes mining jobs. */
    val isPrimary: Boolean get() = slot == 1

    /** Claims the slot now, so it is settled before anything opens a folder. */
    fun claim() {
        slot
    }

    /** [dir] for the primary, and a sibling with this copy's slot appended (`videos-2`) for the rest. */
    fun ownFolder(dir: File): File = folderFor(dir, slot)

    internal fun folderFor(
        dir: File,
        slot: Int,
    ): File = if (slot == 1) dir else File(dir.parentFile, "${dir.name}-$slot")

    /** Takes the lowest free slot in [dir], holding its lock until the process exits. */
    internal fun claimSlot(dir: File): Int {
        if (!dir.isDirectory && !dir.mkdirs()) {
            Log.w(TAG) { "Cannot create $dir: running as the primary copy unchecked" }
            return 1
        }
        for (slot in 1..MAX_SLOTS) {
            val lock = tryLock(File(dir, "slot-$slot.lock")) ?: continue
            held = lock
            if (slot > 1) Log.i(TAG) { "Another copy of Amethyst is running: this one is copy $slot, with its own Tor and caches" }
            return slot
        }
        Log.w(TAG) { "$MAX_SLOTS copies of Amethyst are running: this one shares the folders of copy ${MAX_SLOTS + 1}" }
        return MAX_SLOTS + 1
    }

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
