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
package com.vitorpamplona.amethyst.ios.napplet

import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsChunk
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsError
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsException
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPaths
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsWriteMode
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.amethyst.ios.playback.topViewController
import com.vitorpamplona.amethyst.ios.toByteArray
import com.vitorpamplona.amethyst.ios.toNSData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL
import platform.Foundation.fileHandleForWritingToURL
import platform.Foundation.readDataOfLength
import platform.Foundation.seekToEndOfFile
import platform.Foundation.seekToFileOffset
import platform.Foundation.truncateFileAtOffset
import platform.Foundation.writeData
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * NAP-FS on iOS: the system's document picker, and the files it hands back. Only what the user picks
 * is reachable, through the security-scoped URL the picker returns, entered for each read or write; a
 * handle is a random id that stays in this process (the napplet sees only its virtual path). One port
 * per session, so what a launch picked goes with it.
 *
 * Open picks files in place (`asCopy = false`), so a napplet the user let write can save back. Save
 * picks a folder and names the file in it ([NappletFsPaths.sanitizeName]).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNappletFilePort : NappletFilePort {
    // Where a handle's file is, and the picked URL whose security scope covers it (the file itself,
    // or the folder a save went into).
    private class Picked(
        val scope: NSURL,
        val file: NSURL,
    )

    private val lock = KmpLock()
    private val picked = HashMap<String, Picked>()

    override suspend fun pickOpen(
        options: NappletFsPickOptions,
        multiple: Boolean,
    ): List<NappletPickedFile>? {
        val urls = pick(contentTypes(options), multiple) ?: return null
        return urls.map { url ->
            val handle = remember(Picked(url, url))
            scoped(url) {
                val path = url.path.orEmpty()
                NappletPickedFile(handle, url.lastPathComponent.orEmpty(), sizeAt(path), NSFileManager.defaultManager.isWritableFileAtPath(path))
            }
        }
    }

    override suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile? {
        val folder = pick(listOf(UTTypeFolder), multiple = false)?.firstOrNull() ?: return null
        val name = NappletFsPaths.sanitizeName(options.suggestedName ?: "untitled")
        val file = folder.URLByAppendingPathComponent(name) ?: return null
        return NappletPickedFile(remember(Picked(folder, file)), name, writable = true)
    }

    override suspend fun size(handle: String): Long? =
        withContext(Dispatchers.IO) {
            val entry = entryOf(handle)
            scoped(entry.scope) { sizeAt(entry.file.path.orEmpty()) ?: throw NappletFsException(NappletFsError.NOT_FOUND) }
        }

    override suspend fun read(
        handle: String,
        offset: Long,
        length: Int,
    ): NappletFsChunk =
        withContext(Dispatchers.IO) {
            val entry = entryOf(handle)
            scoped(entry.scope) {
                val size = sizeAt(entry.file.path.orEmpty()) ?: throw NappletFsException(NappletFsError.NOT_FOUND)
                if (offset >= size) return@scoped NappletFsChunk(ByteArray(0), size)
                val file = NSFileHandle.fileHandleForReadingFromURL(entry.file, null) ?: throw NappletFsException(NappletFsError.NOT_FOUND)
                try {
                    file.seekToFileOffset(offset.toULong())
                    NappletFsChunk(file.readDataOfLength(minOf(length.toLong(), size - offset).toULong()).toByteArray(), size)
                } finally {
                    file.closeFile()
                }
            }
        }

    override suspend fun write(
        handle: String,
        data: ByteArray,
        mode: NappletFsWriteMode,
    ): Long? =
        withContext(Dispatchers.IO) {
            val entry = entryOf(handle)
            scoped(entry.scope) {
                val path = entry.file.path.orEmpty()
                when (mode) {
                    // Written beside the file and moved into place, so a failed write leaves the old file.
                    NappletFsWriteMode.Replace -> {
                        if (!data.asNSData().writeToURL(entry.file, atomically = true)) throw NappletFsException(NappletFsError.IO_ERROR)
                        data.size.toLong()
                    }
                    NappletFsWriteMode.Append ->
                        withWriter(entry.file) { file ->
                            val start = file.seekToEndOfFile()
                            try {
                                file.writeData(data.asNSData())
                            } catch (e: Throwable) {
                                file.truncateFileAtOffset(start)
                                throw e
                            }
                            sizeAt(path)
                        }
                    is NappletFsWriteMode.Patch ->
                        withWriter(entry.file) { file ->
                            file.seekToFileOffset(mode.offset.toULong())
                            file.writeData(data.asNSData())
                            sizeAt(path)
                        }
                }
            }
        }

    private fun remember(entry: Picked): String {
        val handle = NSUUID().UUIDString
        lock.withLock { picked[handle] = entry }
        return handle
    }

    private fun entryOf(handle: String): Picked = lock.withLock { picked[handle] } ?: throw NappletFsException(NappletFsError.NOT_FOUND)

    // The picker's URL reaches its file only between these two calls.
    private inline fun <T> scoped(
        url: NSURL,
        block: () -> T,
    ): T {
        val entered = url.startAccessingSecurityScopedResource()
        try {
            return block()
        } finally {
            if (entered) url.stopAccessingSecurityScopedResource()
        }
    }

    private inline fun <T> withWriter(
        url: NSURL,
        block: (NSFileHandle) -> T,
    ): T {
        val file = NSFileHandle.fileHandleForWritingToURL(url, null) ?: throw NappletFsException(NappletFsError.NOT_FOUND)
        try {
            return block(file)
        } finally {
            file.closeFile()
        }
    }

    private fun sizeAt(path: String): Long? = (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue

    private fun ByteArray.asNSData(): NSData = if (isEmpty()) NSData() else toNSData()

    /** The picker's filter from the napplet's hints; anything at all when a hint names no known type. */
    private fun contentTypes(options: NappletFsPickOptions): List<UTType> {
        val types =
            options.accept.mapNotNull { rule ->
                rule.mime?.takeIf { it.isNotBlank() }?.let { UTType.typeWithMIMEType(it) }
                    ?: rule.extension?.takeIf { it.isNotBlank() }?.let { UTType.typeWithFilenameExtension(it.removePrefix(".")) }
            }
        return if (types.isEmpty() || types.size < options.accept.size) listOf(UTTypeItem) else types
    }

    /** Shows the document picker over the app; null when the user cancels or nothing can show it. */
    private suspend fun pick(
        types: List<UTType>,
        multiple: Boolean,
    ): List<NSURL>? =
        withContext(Dispatchers.Main) {
            val presenter = topViewController() ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false)
                picker.allowsMultipleSelection = multiple
                // The picker holds its delegate weakly: this keeps it until the user answers.
                val delegate =
                    PickerDelegate { urls ->
                        if (cont.isActive) cont.resume(urls)
                    }
                picker.delegate = delegate
                pending = delegate
                cont.invokeOnCancellation { dispatchDismiss(picker) }
                presenter.presentViewController(picker, animated = true, completion = null)
            }.also { pending = null }
        }

    // The delegate of the picker on screen, held while it is.
    private var pending: PickerDelegate? = null

    private fun dispatchDismiss(picker: UIDocumentPickerViewController) {
        dispatch_async(dispatch_get_main_queue()) { picker.dismissViewControllerAnimated(true, null) }
    }

    private class PickerDelegate(
        private val done: (List<NSURL>?) -> Unit,
    ) : NSObject(),
        UIDocumentPickerDelegateProtocol {
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>,
        ) = done(didPickDocumentsAtURLs.filterIsInstance<NSURL>().ifEmpty { null })

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = done(null)
    }
}
