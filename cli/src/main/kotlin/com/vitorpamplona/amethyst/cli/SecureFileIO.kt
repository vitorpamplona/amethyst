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
package com.vitorpamplona.amethyst.cli

import java.io.File
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Owner-only writes for the on-disk CLI data (identity, relay config, MLS
 * state, decrypted group messages). POSIX filesystems get 0600 files / 0700
 * directories; non-POSIX (Windows) falls back to `File.setReadable/Writable/
 * Executable(…, false)` to strip other-user access.
 *
 * Atomic overwrites use a sibling tempfile + `ATOMIC_MOVE`, so a crash never
 * leaves a partial world-readable file behind.
 *
 * Caveat: these permissions block *other OS users*. They do not block another
 * app running as the same user — that threat requires encryption at rest.
 */
object SecureFileIO {
    private val posixSupported: Boolean =
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private val filePerms: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
    private val dirPerms: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")

    private val fileAttr: Array<FileAttribute<*>> =
        if (posixSupported) arrayOf(PosixFilePermissions.asFileAttribute(filePerms)) else emptyArray()
    private val dirAttr: Array<FileAttribute<*>> =
        if (posixSupported) arrayOf(PosixFilePermissions.asFileAttribute(dirPerms)) else emptyArray()

    /** Create [dir] and any missing parents with owner-only permissions. */
    fun secureMkdirs(dir: File) {
        val target = dir.toPath()
        val missing = ArrayDeque<Path>()
        var p: Path? = target
        while (p != null && !Files.exists(p)) {
            missing.addFirst(p)
            p = p.parent
        }
        for (m in missing) {
            try {
                Files.createDirectory(m, *dirAttr)
            } catch (_: FileAlreadyExistsException) {
                // benign race with another process
            }
            applyPerms(m, isDir = true)
        }
        if (missing.isEmpty() && Files.exists(target)) applyPerms(target, isDir = true)
    }

    /** Apply owner-only perms to [file] if it exists. Used to tighten pre-existing data on upgrade. */
    fun tighten(file: File) {
        val path = file.toPath()
        if (!Files.exists(path)) return
        applyPerms(path, isDir = Files.isDirectory(path))
    }

    fun writeTextAtomic(
        file: File,
        text: String,
    ) = writeBytesAtomic(file, text.toByteArray(Charsets.UTF_8))

    fun writeBytesAtomic(
        file: File,
        bytes: ByteArray,
    ) = writeAtomic(file) { it.write(bytes) }

    /** Overwrite [file] via a sibling tempfile so partial writes can't replace the target. */
    fun writeAtomic(
        file: File,
        write: (OutputStream) -> Unit,
    ) {
        val target = file.toPath()
        val parent = target.parent ?: error("file must have a parent directory: $file")
        secureMkdirs(parent.toFile())
        val temp = Files.createTempFile(parent, ".${file.name}.", ".tmp", *fileAttr)
        var moved = false
        try {
            Files.newOutputStream(temp).use(write)
            applyPerms(temp, isDir = false)
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            moved = true
            applyPerms(target, isDir = false)
        } finally {
            if (!moved) Files.deleteIfExists(temp)
        }
    }

    /** Append to [file], creating it with owner-only perms if missing. */
    fun appendText(
        file: File,
        text: String,
    ) {
        val path = file.toPath()
        val parent = path.parent ?: error("file must have a parent directory: $file")
        secureMkdirs(parent.toFile())
        if (!Files.exists(path)) {
            try {
                Files.createFile(path, *fileAttr)
            } catch (_: FileAlreadyExistsException) {
                // benign race
            }
        }
        applyPerms(path, isDir = false)
        Files.newOutputStream(path, StandardOpenOption.APPEND).use {
            it.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun applyPerms(
        path: Path,
        isDir: Boolean,
    ) {
        if (posixSupported) {
            try {
                Files.setPosixFilePermissions(path, if (isDir) dirPerms else filePerms)
            } catch (_: UnsupportedOperationException) {
                // fall through to legacy path
            }
            return
        }
        val f = path.toFile()
        f.setReadable(false, false)
        f.setWritable(false, false)
        f.setExecutable(false, false)
        f.setReadable(true, true)
        f.setWritable(true, true)
        if (isDir) f.setExecutable(true, true)
    }
}
