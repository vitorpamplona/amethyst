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
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * The app's side of the class archives its packages start with (see the "Startup archives" section
 * of BUILDING.md). The JVM does the rest from the launcher's options.
 *
 * A package without a trained AOT cache keeps a per-user archive of the app's classes: its launcher
 * passes `-XX:+AutoCreateSharedArchive` with `startup-<version>.jsa` in the user's cache folder,
 * and the JVM writes it as a start that found none exits, and maps it on the next ones. The JVM
 * can't create the folder (it fails the write and exits with 1), and rewrites a missing archive but
 * not one whose jars changed (checked on JDK 21 and 25): [prepare] creates the folder, deletes the
 * archives of other versions, and deletes one older than a jar (a same-version reinstall), so the
 * next start writes a new one.
 *
 * The release build trains the AOT cache by starting the packaged app logged in and letting it
 * load the feed; [prepare] ends that run on time with a normal exit, which is when the JVM writes
 * what it recorded.
 */
object StartupArchive {
    private const val TAG = "StartupArchive"

    /** The per-user archive, as the launcher expanded it (`$HOME/...`), when the package uses one. */
    const val ARCHIVE_PROPERTY = "amethyst.startupArchive"

    /** The per-user archive is `startup-<version>.jsa` (desktopApp/build.gradle.kts). */
    private const val ARCHIVE_PREFIX = "startup-"
    private const val ARCHIVE_SUFFIX = ".jsa"

    /** Seconds after which a training run of the release build exits. */
    const val TRAINING_EXIT_PROPERTY = "amethyst.exitAfterSeconds"

    fun prepare() {
        System.getProperty(ARCHIVE_PROPERTY)?.let(::createFolderOf)
        System.getProperty(TRAINING_EXIT_PROPERTY)?.toLongOrNull()?.let(::exitAfter)
    }

    private fun createFolderOf(path: String) {
        // The launcher leaves a variable it can't resolve as it is: no folder for a literal "$HOME".
        if (path.isBlank() || '$' in path) return
        val archive = File(path).absoluteFile
        val folder = archive.parentFile ?: return
        if (!folder.isDirectory && !folder.mkdirs()) {
            Log.w(TAG) { "Could not create $folder: this start's classes will not be archived" }
            return
        }
        removeOtherVersions(archive)
        removeIfStale(archive)
    }

    /** The archives of the versions this one replaced, ~140 MB each. */
    private fun removeOtherVersions(archive: File) {
        archive.parentFile
            .listFiles { f -> f != archive && f.name.startsWith(ARCHIVE_PREFIX) && f.name.endsWith(ARCHIVE_SUFFIX) }
            .orEmpty()
            .forEach { if (!it.delete() && it.exists()) Log.w(TAG) { "Could not delete $it" } }
    }

    /**
     * The JVM only writes an archive it finds missing, and maps one only while every jar has the
     * modification time it had when the archive was written. A same-version reinstall (an .msi
     * repair, a rebuilt test package) changes those times and leaves an archive this start can't
     * use and no start would replace: deleted, the next start writes a new one.
     */
    private fun removeIfStale(archive: File) {
        if (!archive.isFile) return
        val jars =
            StartupArchive::class.java.protectionDomain
                ?.codeSource
                ?.location
                ?.let { runCatching { File(it.toURI()).parentFile }.getOrNull() }
                ?.listFiles { f -> f.name.endsWith(".jar") }
                .orEmpty()
        val newestJar = jars.maxOfOrNull { it.lastModified() } ?: return
        if (newestJar > archive.lastModified()) {
            Log.i(TAG) { "The app's jars changed after $archive was written: the next start writes a new one" }
            if (!archive.delete() && archive.exists()) Log.w(TAG) { "Could not delete $archive" }
        }
    }

    private fun exitAfter(seconds: Long) {
        thread(isDaemon = true, name = "startup-archive-training") {
            Thread.sleep(seconds * 1000)
            Log.i(TAG) { "Training run done after $seconds s" }
            exitProcess(0)
        }
    }
}
