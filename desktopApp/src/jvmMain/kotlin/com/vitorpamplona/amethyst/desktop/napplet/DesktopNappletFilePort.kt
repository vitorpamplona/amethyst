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
package com.vitorpamplona.amethyst.desktop.napplet

import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsException
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPaths
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsWriteMode
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * NAP-FS on the desktop: the system's file dialog, and the files it returns. A handle is the chosen
 * file's absolute path, which stays in this process; the napplet sees only its virtual path.
 *
 * A replace writes beside the file and moves it into place, so a failed write leaves the old file;
 * an append that fails is cut back to where it started.
 */
class DesktopNappletFilePort(
    /** The dialogs' owner, so they open over the napplet's window; null centers them on screen. */
    private val owner: () -> Frame? = { null },
) : NappletFilePort {
    override suspend fun pickOpen(
        options: NappletFsPickOptions,
        multiple: Boolean,
    ): List<NappletPickedFile>? {
        val files =
            withContext(Dispatchers.Main) {
                val dialog =
                    FileDialog(owner(), options.description.orEmpty(), FileDialog.LOAD).apply {
                        isMultipleMode = multiple
                        extensionFilter(options)?.let { filenameFilter = it }
                    }
                dialog.isVisible = true
                dialog.files?.toList().orEmpty()
            }
        if (files.isEmpty()) return null
        return files.map { NappletPickedFile(it.absolutePath, it.name, it.length(), it.canWrite()) }
    }

    override suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile? {
        val file =
            withContext(Dispatchers.Main) {
                val dialog =
                    FileDialog(owner(), options.description.orEmpty(), FileDialog.SAVE).apply {
                        options.suggestedName?.let { file = NappletFsPaths.sanitizeName(it) }
                    }
                dialog.isVisible = true
                val name = dialog.file ?: return@withContext null
                File(dialog.directory ?: return@withContext null, name)
            } ?: return null
        return NappletPickedFile(file.absolutePath, file.name, writable = true)
    }

    override suspend fun size(handle: String): Long? =
        withContext(Dispatchers.IO) {
            val file = File(handle)
            if (!file.isFile) throw NappletFsException(NappletFsError.NOT_FOUND)
            file.length()
        }

    override suspend fun read(
        handle: String,
        offset: Long,
        length: Int,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            guard {
                RandomAccessFile(handle, "r").use { file ->
                    if (offset >= file.length()) return@use ByteArray(0)
                    val buffer = ByteArray(minOf(length.toLong(), file.length() - offset).toInt())
                    file.seek(offset)
                    file.readFully(buffer)
                    buffer
                }
            }
        }

    override suspend fun write(
        handle: String,
        data: ByteArray,
        mode: NappletFsWriteMode,
    ): Long? =
        withContext(Dispatchers.IO) {
            guard {
                when (mode) {
                    NappletFsWriteMode.Replace -> replace(File(handle), data)
                    NappletFsWriteMode.Append ->
                        RandomAccessFile(handle, "rw").use { file ->
                            val start = file.length()
                            try {
                                file.seek(start)
                                file.write(data)
                            } catch (e: IOException) {
                                runCatching { file.setLength(start) }
                                throw e
                            }
                            file.length()
                        }
                    is NappletFsWriteMode.Patch ->
                        RandomAccessFile(handle, "rw").use { file ->
                            file.seek(mode.offset)
                            file.write(data)
                            file.length()
                        }
                }
            }
        }

    private fun replace(
        target: File,
        data: ByteArray,
    ): Long {
        val partial = File(target.parentFile, ".${target.name}.part")
        try {
            partial.writeBytes(data)
            try {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (partial.exists() && !partial.delete()) partial.deleteOnExit()
        }
        return data.size.toLong()
    }

    /** The napplet's extension rules as a dialog filter; null (show everything) when any rule is a MIME type. */
    private fun extensionFilter(options: NappletFsPickOptions): FilenameFilter? {
        if (options.accept.isEmpty() || options.accept.any { it.extension.isNullOrBlank() }) return null
        val extensions = options.accept.mapNotNull { it.extension?.removePrefix(".")?.lowercase() }.toSet()
        return FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
    }

    private inline fun <T> guard(block: () -> T): T =
        try {
            block()
        } catch (e: FileNotFoundException) {
            throw NappletFsException(NappletFsError.NOT_FOUND)
        } catch (e: SecurityException) {
            throw NappletFsException(NappletFsError.PERMISSION_DENIED)
        }
}
