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
package com.vitorpamplona.amethyst.commons.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
actual fun rememberFileSharer(): FileSharer {
    val context = LocalContext.current
    return remember(context) {
        FileSharer { zipName, files, title ->
            val zipFile = File(context.cacheDir, zipName)
            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                files.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", zipFile)

            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            context.startActivity(Intent.createChooser(sendIntent, title))
        }
    }
}
