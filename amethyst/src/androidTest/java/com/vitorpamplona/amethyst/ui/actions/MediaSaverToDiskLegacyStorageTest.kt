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
package com.vitorpamplona.amethyst.ui.actions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * Covers the pre-Q writer, which MediaStore never sees: below API 29 saveContentDefault
 * writes straight to a public directory and lets the media scanner index it.
 *
 * That path used to hardcode Pictures for every content type, so videos, audio and PDFs
 * were all filed under Pictures/Amethyst. It now routes through the same MediaStoreTarget
 * as the MediaStore path. minSdk is 26, so this range ships.
 *
 * There is no JVM coverage of any of this: Build.VERSION.SDK_INT is 0 under
 * returnDefaultValues, so unit tests can only reach the routing function, never the writer.
 */
@RunWith(AndroidJUnit4::class)
class MediaSaverToDiskLegacyStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val watchedDirs = listOf("Movies", "Pictures", "Download", "Music")
    private val createdFiles = mutableListOf<File>()

    @Before
    fun onlyBelowScopedStorage() {
        assumeTrue("saveContentDefault only runs below API 29", Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)

        // The legacy writer needs the runtime permission; no androidx.test:rules on the
        // classpath, so grant it through the instrumentation shell instead. The output has
        // to be drained: executeShellCommand runs asynchronously and closing the descriptor
        // early kills the command before it applies.
        val fd =
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .executeShellCommand(
                    "pm grant ${context.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
                )
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }

        assertEquals(
            "WRITE_EXTERNAL_STORAGE was not granted; the legacy writer cannot be exercised",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        )

        // Holding the permission is not enough below Q: external storage is mounted into
        // the process when it forks, so a grant to an already-running process never
        // reaches it and every write fails with EACCES. Probe for real writability and
        // skip rather than report a routing failure that is really a harness problem.
        assumeTrue(
            "External storage is not writable by this process. Below API 29 the grant must " +
                "exist before the process starts - install with `adb install -r -g` and drive " +
                "the run with `am instrument`; Gradle's connectedAndroidTest cannot grant in time.",
            canWriteToPublicStorage(),
        )
    }

    private fun canWriteToPublicStorage(): Boolean =
        try {
            val dir = amethystDir("Movies").apply { if (!exists()) mkdirs() }
            val probe = File(dir, ".write-probe-${System.nanoTime()}")
            probe.createNewFile().also { probe.delete() }
        } catch (e: IOException) {
            false
        }

    @After
    fun cleanUp() {
        createdFiles.forEach { it.delete() }
    }

    @Test
    fun videoGoesToMovies() = assertRoutes("video/mp4", "Movies")

    @Test
    fun imageGoesToPictures() = assertRoutes("image/jpeg", "Pictures")

    @Test
    fun audioGoesToMusic() = assertRoutes("audio/mpeg", "Music")

    @Test
    fun pdfGoesToDownloads() = assertRoutes("application/pdf", "Download")

    /**
     * Saves one file and asserts it appeared under [expectedDir]/Amethyst and nowhere else.
     * Checking the other directories is the point: the bug was everything landing in Pictures.
     */
    private fun assertRoutes(
        mimeType: String,
        expectedDir: String,
    ) {
        val before = snapshot()

        val localFile = File(context.cacheDir, "legacy-save-${System.nanoTime()}.bin")
        localFile.writeBytes(ByteArray(2048) { it.toByte() })

        var failure: Throwable? = null
        var succeeded = false

        runBlocking {
            MediaSaverToDisk.save(
                localFile = localFile,
                mimeType = mimeType,
                context = context,
                onSuccess = { succeeded = true },
                onError = { failure = it },
            )
        }

        localFile.delete()

        assertNull("save() reported an error: ${failure?.message}", failure)
        assertTrue("save() never reported success", succeeded)

        val added = snapshot().mapValues { (dir, names) -> names - before.getValue(dir) }
        added.forEach { (dir, names) -> names.forEach { createdFiles.add(File(amethystDir(dir), it)) } }

        val dirsThatGrew = added.filterValues { it.isNotEmpty() }.keys
        assertEquals("$mimeType should land only in $expectedDir/Amethyst", setOf(expectedDir), dirsThatGrew)
        assertEquals("expected exactly one new file", 1, added.getValue(expectedDir).size)
    }

    private fun amethystDir(publicDir: String) = File(Environment.getExternalStoragePublicDirectory(publicDir), "Amethyst")

    private fun snapshot(): Map<String, Set<String>> = watchedDirs.associateWith { amethystDir(it).list()?.toSet() ?: emptySet() }
}
