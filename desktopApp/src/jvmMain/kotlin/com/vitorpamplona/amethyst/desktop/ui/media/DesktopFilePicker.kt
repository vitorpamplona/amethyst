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
package com.vitorpamplona.amethyst.desktop.ui.media

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

object DesktopFilePicker {
    private val mediaExtensions =
        setOf(
            "png",
            "jpg",
            "jpeg",
            "gif",
            "webp",
            "svg",
            "avif",
            "mp4",
            "webm",
            "mov",
            "mp3",
            "ogg",
            "wav",
            "flac",
        )

    fun pickMediaFiles(parent: Frame? = null): List<File> {
        val dialog =
            FileDialog(parent, "Select Media", FileDialog.LOAD).apply {
                isMultipleMode = true
                filenameFilter =
                    FilenameFilter { _, name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        ext in mediaExtensions
                    }
            }
        dialog.isVisible = true
        return dialog.files?.toList() ?: emptyList()
    }
}
