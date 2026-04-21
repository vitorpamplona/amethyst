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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import java.io.File

/**
 * Test-harness file stores. **Unencrypted** — the production interfaces
 * document that implementations MUST encrypt at rest, but this CLI is a
 * throwaway interop driver running against scratch keys and local scratch
 * state. Do not point it at real account material.
 */

class FileMlsGroupStateStore(
    private val dir: File,
) : MlsGroupStateStore {
    init {
        dir.mkdirs()
    }

    private fun stateFile(id: String) = File(dir, "$id.state")

    private fun retainedFile(id: String) = File(dir, "$id.retained")

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        stateFile(nostrGroupId).writeBytes(state)
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = stateFile(nostrGroupId).takeIf { it.exists() }?.readBytes()

    override suspend fun delete(nostrGroupId: String) {
        stateFile(nostrGroupId).delete()
        retainedFile(nostrGroupId).delete()
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
        val f = retainedFile(nostrGroupId)
        f.outputStream().use { out ->
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
        file.parentFile?.mkdirs()
        file.writeBytes(snapshot)
    }

    override suspend fun load(): ByteArray? = file.takeIf { it.exists() }?.readBytes()

    override suspend fun delete() {
        file.delete()
    }
}

class FileMarmotMessageStore(
    private val dir: File,
) : MarmotMessageStore {
    init {
        dir.mkdirs()
    }

    private fun file(id: String) = File(dir, "$id.messages")

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        // Each line is one inner event JSON. The store doc tolerates duplicates
        // so we don't bother deduping here — readers can do it.
        file(nostrGroupId).appendText(innerEventJson.replace("\n", " ") + "\n")
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = file(nostrGroupId).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        file(nostrGroupId).delete()
    }
}
