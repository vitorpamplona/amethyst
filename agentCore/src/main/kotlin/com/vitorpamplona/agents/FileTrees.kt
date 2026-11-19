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
package com.vitorpamplona.agents

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Removes [dir] and everything in it, carrying on past what it can't remove. Returns whether
 * nothing is left, true when [dir] never existed.
 *
 * Symbolic links are removed, never followed: a link in an agent's folder (a committed one, a
 * pnpm `node_modules`, one the agent made to the home folder) must not take its target with it,
 * as `File.deleteRecursively` would. Git makes its pack files read-only, and Windows refuses to
 * delete a read-only file: each one is made writable first, the link itself never.
 */
fun deleteTree(dir: File): Boolean {
    val root = dir.toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return true
    try {
        // Without FOLLOW_LINKS the walk visits a link as a file, and never goes through it.
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    if (!attrs.isSymbolicLink) makeWritable(file)
                    deleteQuietly(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException,
                ): FileVisitResult {
                    deleteQuietly(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    directory: Path,
                    exc: IOException?,
                ): FileVisitResult {
                    makeWritable(directory)
                    deleteQuietly(directory)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    } catch (e: IOException) {
        // Reported by what is left below.
    }
    return !Files.exists(root, LinkOption.NOFOLLOW_LINKS)
}

/**
 * Returns whether [path] is writable now. A failure is not acted on: the delete is still tried
 * (POSIX deletes a read-only file the folder lets it), and anything that stays is reported by
 * deleteTree's result.
 */
private fun makeWritable(path: Path): Boolean {
    val file = path.toFile()
    return file.canWrite() || file.setWritable(true)
}

private fun deleteQuietly(path: Path) {
    try {
        Files.deleteIfExists(path)
    } catch (e: IOException) {
        // A file that stays is reported by deleteTree's result.
    }
}
