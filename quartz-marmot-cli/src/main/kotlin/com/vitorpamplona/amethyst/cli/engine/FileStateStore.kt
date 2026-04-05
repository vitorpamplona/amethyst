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
package com.vitorpamplona.amethyst.cli.engine

import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import java.io.File
import java.util.Base64

class FileStateStore(
    private val dataDir: File,
) : MlsGroupStateStore {
    private val groupsDir = File(dataDir, "groups").also { it.mkdirs() }
    private val epochsDir = File(dataDir, "epochs").also { it.mkdirs() }

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        File(groupsDir, "$nostrGroupId.state").writeBytes(state)
    }

    override suspend fun load(nostrGroupId: String): ByteArray? {
        val file = File(groupsDir, "$nostrGroupId.state")
        return if (file.exists()) file.readBytes() else null
    }

    override suspend fun delete(nostrGroupId: String) {
        File(groupsDir, "$nostrGroupId.state").delete()
        File(epochsDir, "$nostrGroupId.epochs").delete()
    }

    override suspend fun listGroups(): List<String> =
        groupsDir
            .listFiles()
            ?.filter { it.extension == "state" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        val encoded =
            retainedSecrets.joinToString("\n") {
                Base64.getEncoder().encodeToString(it)
            }
        File(epochsDir, "$nostrGroupId.epochs").writeText(encoded)
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> {
        val file = File(epochsDir, "$nostrGroupId.epochs")
        if (!file.exists()) return emptyList()
        return file
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .map { Base64.getDecoder().decode(it) }
    }
}
