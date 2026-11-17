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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.quartz.nip34Git.git.GitObject
import com.vitorpamplona.quartz.nip34Git.git.GitObjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Reads, from a local checkout, the git objects a branch adds on top of a base: what a GRASP
 * server needs to hold a proposal made on this computer. Uses the user's `git`, so packed objects,
 * alternates and worktrees all just work.
 */
object LocalGitObjects {
    /** Every object reachable from [tip] and not from [base]: the new commits, trees and blobs. */
    suspend fun between(
        dir: File,
        base: String?,
        tip: String,
    ): List<GitObject> =
        withContext(Dispatchers.IO) {
            val range = listOfNotNull(tip, base?.let { "^$it" })
            val listing = GitWorkspace.git(dir, "rev-list", "--objects", *range.toTypedArray()) ?: throw IOException("git rev-list failed in $dir")
            val oids =
                listing
                    .lineSequence()
                    .mapNotNull { it.substringBefore(' ').trim().takeIf { oid -> oid.length >= 40 } }
                    .distinct()
                    .toList()
            catFile(dir, oids)
        }

    /** The full id of [rev] (a branch, `HEAD`, a short id), or null when it doesn't resolve. */
    suspend fun resolve(
        dir: File,
        rev: String,
    ): String? = withContext(Dispatchers.IO) { GitWorkspace.git(dir, "rev-parse", "--verify", "--quiet", "$rev^{commit}")?.trim()?.takeIf { it.isNotEmpty() } }

    /**
     * The merge base of [tip] and [head], fetching [head] from [cloneUrls] first when the checkout
     * doesn't have it (the repository's main moved since the checkout last fetched).
     */
    suspend fun mergeBase(
        dir: File,
        head: String,
        tip: String,
        cloneUrls: List<String>,
        branch: String?,
    ): String? =
        withContext(Dispatchers.IO) {
            if (!GitWorkspace.isCommitId(head) || !GitWorkspace.isCommitId(tip)) return@withContext null
            if (GitWorkspace.git(dir, "cat-file", "-e", "$head^{commit}") == null) {
                // From the repository's events: only plain transports, and after `--`.
                val ref = branch?.takeIf(GitWorkspace::isSafeBranchName) ?: head
                for (url in cloneUrls.filter(GitWorkspace::isSafeRemoteUrl)) {
                    GitWorkspace.run(dir, listOf("git", "fetch", "--no-tags", "--", url, ref), timeoutSeconds = 300, noLoginWindow = true)
                    if (GitWorkspace.git(dir, "cat-file", "-e", "$head^{commit}") != null) break
                }
            }
            GitWorkspace.git(dir, "merge-base", head, tip)?.trim()?.takeIf { it.isNotEmpty() }
        }

    /** Reads [oids] through one `git cat-file --batch`. */
    private fun catFile(
        dir: File,
        oids: List<String>,
    ): List<GitObject> {
        if (oids.isEmpty()) return emptyList()
        val process =
            ProcessBuilder("git", "cat-file", "--batch")
                .directory(dir)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        // Write the ids from another thread: git answers as it reads, and both pipes have limits.
        val writer =
            Thread({
                process.outputStream.bufferedWriter().use { out ->
                    oids.forEach {
                        out.write(it)
                        out.write("\n")
                    }
                }
            }, "git-cat-file-in").apply {
                isDaemon = true
                start()
            }
        val input = BufferedInputStream(process.inputStream)
        val objects = ArrayList<GitObject>(oids.size)
        repeat(oids.size) {
            val header = readLine(input) ?: throw IOException("git cat-file ended early")
            val parts = header.split(' ')
            if (parts.size < 3 || parts[1] == "missing") throw IOException("git cat-file: $header")
            val size = parts[2].toInt()
            val data = input.readNBytes(size)
            if (data.size != size) throw IOException("git cat-file: short read for ${parts[0]}")
            input.read() // the newline after each object
            objects.add(GitObject(typeOf(parts[1]), data, parts[0]))
        }
        writer.join()
        process.waitFor()
        return objects
    }

    private fun typeOf(name: String): GitObjectType =
        when (name) {
            "commit" -> GitObjectType.COMMIT
            "tree" -> GitObjectType.TREE
            "blob" -> GitObjectType.BLOB
            "tag" -> GitObjectType.TAG
            else -> throw IOException("unknown git object type $name")
        }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.UTF_8)
            if (b == '\n'.code) return out.toString(Charsets.UTF_8)
            out.write(b)
        }
    }
}
