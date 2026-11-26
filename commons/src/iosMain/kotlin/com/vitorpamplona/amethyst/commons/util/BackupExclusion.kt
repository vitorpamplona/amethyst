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

import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey

/**
 * Creates the directory at [path] if needed and marks it excluded from iCloud and Finder/iTunes
 * backups (`NSURLIsExcludedFromBackupKey`), for data that is a cache in all but name: it can be
 * rebuilt, it is device-specific, and backing it up would only cost the user quota. The flag is on
 * the directory itself and covers what is inside it, so it must be set again whenever the directory
 * is deleted and created anew. Returns false (and logs) when it could not be set.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun excludeDirectoryFromBackup(path: String): Boolean =
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val created =
            NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = error.ptr)
        if (!created) {
            Log.w("BackupExclusion") { "Could not create $path: ${error.value?.localizedDescription}" }
            return@memScoped false
        }
        val excluded = NSURL.fileURLWithPath(path, isDirectory = true).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = error.ptr)
        if (!excluded) Log.w("BackupExclusion") { "Could not exclude $path from backups: ${error.value?.localizedDescription}" }
        excluded
    }

/** Whether the directory or file at [path] is excluded from backups; null when it can't be read. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun isExcludedFromBackup(path: String): Boolean? =
    memScoped {
        val value = alloc<ObjCObjectVar<Any?>>()
        val read = NSURL.fileURLWithPath(path).getResourceValue(value.ptr, forKey = NSURLIsExcludedFromBackupKey, error = null)
        if (!read) null else (value.value as? NSNumber)?.boolValue ?: false
    }
