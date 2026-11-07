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
 * passes `-XX:+AutoCreateSharedArchive` with a file in the user's cache folder, and the JVM writes
 * it when a start finds it missing or stale (the first start, or the first after an update) and
 * maps it on the next ones. The JVM cannot create the folder, and fails the write, exiting with 1,
 * when it is missing; [prepare] creates it.
 *
 * The release build trains the AOT cache by starting the packaged app logged in and letting it
 * load the feed; [prepare] ends that run on time with a normal exit, which is when the JVM writes
 * what it recorded.
 */
object StartupArchive {
    private const val TAG = "StartupArchive"

    /** The per-user archive, as the launcher expanded it (`$HOME/...`), when the package uses one. */
    const val ARCHIVE_PROPERTY = "amethyst.startupArchive"

    /** Seconds after which a training run of the release build exits. */
    const val TRAINING_EXIT_PROPERTY = "amethyst.exitAfterSeconds"

    fun prepare() {
        System.getProperty(ARCHIVE_PROPERTY)?.let(::createFolderOf)
        System.getProperty(TRAINING_EXIT_PROPERTY)?.toLongOrNull()?.let(::exitAfter)
    }

    private fun createFolderOf(path: String) {
        // The launcher leaves a variable it can't resolve as it is: no folder for a literal "$HOME".
        if (path.isBlank() || '$' in path) return
        val folder = File(path).absoluteFile.parentFile ?: return
        if (!folder.isDirectory && !folder.mkdirs()) {
            Log.w(TAG) { "Could not create $folder: this start's classes will not be archived" }
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
