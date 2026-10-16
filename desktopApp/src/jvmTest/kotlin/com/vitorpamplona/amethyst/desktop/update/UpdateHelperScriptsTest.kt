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
package com.vitorpamplona.amethyst.desktop.update

import com.vitorpamplona.amethyst.commons.appupdate.UpdateInstallException
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the Unix helper for real: it waits for a process, swaps two folders and relaunches. */
class UpdateHelperScriptsTest {
    private val dir = Files.createTempDirectory("update-helper").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun isUnix() = File("/bin/sh").canExecute() && !System.getProperty("os.name").lowercase().startsWith("windows")

    private fun waitFor(
        timeoutMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    @Test
    fun swapsTheFoldersAfterTheAppQuitsAndRelaunches() {
        if (!isUnix()) return

        val current = File(dir, "Amethyst").apply { mkdirs() }
        File(current, "version").writeText("old")
        val replacement = File(dir, ".amethyst-update/extract/Amethyst").apply { mkdirs() }
        File(replacement, "version").writeText("new")
        val relaunched = File(dir, "relaunched")

        // Stands in for the running app: the helper must wait for it to exit.
        val app = ProcessBuilder("sleep", "1").start()
        val script = UpdateHelperScripts.writeUnix(File(dir, "updates"))
        val command =
            UpdateHelperScripts.unixCommand(
                script,
                app.pid(),
                "swap",
                listOf(current.absolutePath, replacement.absolutePath),
                listOf("touch", relaunched.absolutePath),
            )
        spawnDetached(command, File(dir, "updates/update.log"))

        assertEquals("old", File(current, "version").readText(), "nothing moves while the app runs")
        assertTrue(waitFor(10_000) { relaunched.exists() }, "relaunched; log: ${File(dir, "updates/update.log").readText()}")
        assertEquals("new", File(current, "version").readText())
        assertFalse(replacement.exists())
    }

    @Test
    fun relaunchesTheOldVersionWhenTheNewOneCannotMoveIn() {
        if (!isUnix()) return

        val current = File(dir, "Amethyst").apply { mkdirs() }
        File(current, "version").writeText("old")
        val relaunched = File(dir, "relaunched")

        val script = UpdateHelperScripts.writeUnix(File(dir, "updates"))
        val command =
            UpdateHelperScripts.unixCommand(
                script,
                ProcessHandle.current().pid() + 1_000_000,
                "swap",
                listOf(current.absolutePath, File(dir, "does-not-exist").absolutePath),
                listOf("touch", relaunched.absolutePath),
            )
        spawnDetached(command, File(dir, "updates/update.log"))

        assertTrue(waitFor(10_000) { relaunched.exists() })
        assertEquals("old", File(current, "version").readText())
    }

    @Test
    fun unzipRefusesEntriesThatLeaveTheFolder() {
        val zip = File(dir, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Amethyst/ok.txt"))
            out.write("ok".toByteArray())
            out.putNextEntry(ZipEntry("../escaped.txt"))
            out.write("no".toByteArray())
        }
        assertFailsWith<UpdateInstallException> { DesktopUpdateInstaller.unzip(zip, File(dir, "out")) }
        assertFalse(File(dir, "escaped.txt").exists())
    }

    @Test
    fun unzipKeepsTheFolderLayout() {
        val zip = File(dir, "good.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Amethyst/"))
            out.putNextEntry(ZipEntry("Amethyst/Amethyst.exe"))
            out.write("exe".toByteArray())
        }
        DesktopUpdateInstaller.unzip(zip, File(dir, "out"))
        assertEquals("exe", File(dir, "out/Amethyst/Amethyst.exe").readText())
    }

    @Test
    fun aFileNameFromAReleaseCannotLeaveTheFolder() {
        assertEquals("evil.msi", DesktopUpdateInstaller.safeName("../../evil.msi"))
        assertEquals("evil.msi", DesktopUpdateInstaller.safeName("C:\\Windows\\evil.msi"))
        assertEquals("a_b.msi", DesktopUpdateInstaller.safeName("a..b.msi"))
    }
}
