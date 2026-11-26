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
package com.vitorpamplona.amethyst.commons.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Arti's data directory is kept out of backups, including after a reset deletes and recreates it. */
@OptIn(ExperimentalForeignApi::class)
class BackupExclusionTest {
    @Test
    fun createsTheDirectoryAndExcludesItAgainAfterItIsDeleted() {
        val dir = NSTemporaryDirectory() + "BackupExclusionTest-${Random.nextLong().toULong()}/arti"
        try {
            assertTrue(excludeDirectoryFromBackup(dir))
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(dir))
            assertEquals(true, isExcludedFromBackup(dir))

            // A clean-state reset deletes the whole directory, and the flag with it.
            NSFileManager.defaultManager.removeItemAtPath(dir, null)
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            assertEquals(false, isExcludedFromBackup(dir), "a new directory is backed up by default")

            assertTrue(excludeDirectoryFromBackup(dir), "setting it on an existing directory works")
            assertEquals(true, isExcludedFromBackup(dir))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(dir.removeSuffix("/arti"), null)
        }
    }
}
