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
package com.vitorpamplona.amethyst.cli.stores

import com.vitorpamplona.amethyst.cli.SecureFileIO
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import java.io.File

/**
 * Test-harness file stores. **Unencrypted** — the production interfaces
 * document that implementations MUST encrypt at rest, but this CLI is a
 * throwaway interop driver running against scratch keys and local scratch
 * state. Do not point it at real account material.
 *
 * Writes go through [SecureFileIO] so files land with owner-only filesystem
 * permissions and overwrites are atomic. That blocks other OS users; it does
 * not block another app running as the same user — that still requires
 * encryption at rest.
 */

class FileMlsGroupStateStore(
    private val dir: File,
) : MlsGroupStateStore {
    init {
        SecureFileIO.secureMkdirs(dir)
    }

    private fun stateFile(id: String) = File(dir, "$id.state")

    private fun retainedFile(id: String) = File(dir, "$id.retained")

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        SecureFileIO.writeBytesAtomic(stateFile(nostrGroupId), state)
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = stateFile(nostrGroupId).takeIf { it.exists() }?.readBytes()

    override suspend fun delete(nostrGroupId: String) {
        stateFile(nostrGroupId).deleteOrWarn("FileMlsGroupStateStore", "group state")
        retainedFile(nostrGroupId).deleteOrWarn("FileMlsGroupStateStore", "retained epochs")
    }

    override suspend fun listGroups(): List<String> =
        dir
            .listFiles { f -> f.name.endsWith(".state") }
            ?.map { it.name.removeSuffix(".state") }
            ?: emptyList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        // Layout: [u32 count][(u32 len, bytes) …] — tiny framing so readers
        // can recover independent byte arrays without TLS plumbing.
        SecureFileIO.writeAtomic(retainedFile(nostrGroupId)) { out ->
            val buf = java.nio.ByteBuffer.allocate(4)
            buf.putInt(retainedSecrets.size)
            out.write(buf.array())
            for (secret in retainedSecrets) {
                buf.clear()
                buf.putInt(secret.size)
                out.write(buf.array())
                out.write(secret)
            }
        }
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> {
        val f = retainedFile(nostrGroupId)
        if (!f.exists()) return emptyList()
        val bytes = f.readBytes()
        if (bytes.size < 4) return emptyList()
        val buf = java.nio.ByteBuffer.wrap(bytes)
        val count = buf.int
        val result = ArrayList<ByteArray>(count)
        repeat(count) {
            val len = buf.int
            val arr = ByteArray(len)
            buf.get(arr)
            result.add(arr)
        }
        return result
    }
}

class FileKeyPackageBundleStore(
    private val file: File,
) : KeyPackageBundleStore {
    override suspend fun save(snapshot: ByteArray) {
        SecureFileIO.writeBytesAtomic(file, snapshot)
    }

    override suspend fun load(): ByteArray? = file.takeIf { it.exists() }?.readBytes()

    override suspend fun delete() {
        file.deleteOrWarn("FileKeyPackageBundleStore", "key package bundle")
    }
}

class FileMarmotMessageStore(
    private val dir: File,
) : MarmotMessageStore {
    init {
        SecureFileIO.secureMkdirs(dir)
    }

    private fun file(id: String) = File(dir, "$id.messages")

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        // Each line is one inner event JSON. Appends must be idempotent:
        // post-restart relay replays re-decrypt already-persisted messages.
        val line = innerEventJson.replace("\n", " ")
        val target = file(nostrGroupId)
        if (target.exists() && target.readLines().any { it == line }) return
        SecureFileIO.appendText(target, line + "\n")
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = file(nostrGroupId).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        file(nostrGroupId).deleteOrWarn("FileMarmotMessageStore", "group messages")
    }
}
