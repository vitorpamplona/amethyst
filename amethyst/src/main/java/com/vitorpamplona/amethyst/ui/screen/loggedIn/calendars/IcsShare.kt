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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import java.io.File

/**
 * Writes [content] to a `.ics` file in the app's cache dir and opens the system share sheet so
 * the user can hand the file to a calendar app, email client, file manager, etc.
 *
 * The file lives in `cacheDir/calendar/` — covered by the existing `<cache-path>` entry in
 * `file_paths.xml`, so [FileProvider] can hand out a `content://` URI without further config.
 * The receiver gets a read-permission grant via [Intent.FLAG_GRANT_READ_URI_PERMISSION] that
 * lasts only for the duration of the share.
 */
fun shareIcs(
    context: Context,
    filename: String,
    content: String,
) {
    val dir = File(context.cacheDir, "calendar").apply { mkdirs() }
    val file = File(dir, filename)
    file.writeText(content)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    val chooser =
        Intent
            .createChooser(intent, stringRes(context, R.string.calendar_export_share_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
