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

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

/**
 * End-to-end regression test for issue #4009.
 *
 * MediaProvider validates RELATIVE_PATH's primary directory against the collection
 * being written to. Filing a video under "Pictures" threw
 * `IllegalArgumentException: Primary directory Pictures not allowed for
 * content://media/external/video/media; allowed directories are [DCIM, Movies]`
 * on Android 10; later releases accept the mismatch and silently misfile the video.
 *
 * This drives the real ContentResolver, so it catches both symptoms: the insert has
 * to succeed AND the row has to land in the directory the collection accepts.
 */
@RunWith(AndroidJUnit4::class)
class MediaSaverToDiskMediaStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver: ContentResolver get() = context.contentResolver

    /** Only rows this test inserted, identified by id in the collection they went into. */
    private val created = mutableListOf<Pair<Uri, Long>>()

    @Before
    fun requiresScopedStorage() {
        assumeTrue("saveContentQ only runs on API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    @After
    fun cleanUp() {
        created.forEach { (collection, id) ->
            resolver.delete(collection, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString()))
        }
    }

    @Test
    fun savingAVideoLandsInMoviesAndNotPictures() {
        val relativePath = saveAndReadBackRelativePath("video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        assertEquals("Movies/Amethyst/", relativePath)
    }

    @Test
    fun savingAnImageStillLandsInPictures() {
        val relativePath = saveAndReadBackRelativePath("image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        assertEquals("Pictures/Amethyst/", relativePath)
    }

    private fun saveAndReadBackRelativePath(
        mimeType: String,
        collection: Uri,
    ): String? {
        // Anything at or below this id predates the test and must never be read or deleted:
        // this suite is meant to be runnable on a real device holding real media.
        val highWaterMark = maxIdIn(collection)

        val localFile = File(context.cacheDir, "media-saver-${System.nanoTime()}.bin")
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

        // Surfaces the #4009 IllegalArgumentException as the test failure message.
        assertNull("save() reported an error: ${failure?.message}", failure)
        assertTrue("save() never reported success", succeeded)

        return rowInsertedAfter(collection, highWaterMark)
    }

    private fun maxIdIn(collection: Uri): Long {
        resolver
            .query(collection, arrayOf(MediaStore.MediaColumns._ID), null, null, "${MediaStore.MediaColumns._ID} DESC")
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        return -1L
    }

    /** Reads back the row the save just inserted and records it for cleanup. */
    private fun rowInsertedAfter(
        collection: Uri,
        highWaterMark: Long,
    ): String? {
        resolver
            .query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns._ID} > ?",
                arrayOf(highWaterMark.toString()),
                "${MediaStore.MediaColumns._ID} ASC",
            )?.use { cursor ->
                assertTrue("save() reported success but inserted no row into $collection", cursor.moveToFirst())
                created.add(collection to cursor.getLong(0))
                return cursor.getString(1)
            }
        return null
    }
}
