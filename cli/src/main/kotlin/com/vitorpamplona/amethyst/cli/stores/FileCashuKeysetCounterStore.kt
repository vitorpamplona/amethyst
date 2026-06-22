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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.SecureFileIO
import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import java.io.File

/**
 * File-backed NUT-13 counter store for `amy`, persisted at
 * `~/.amy/<account>/cashu.json` as `{ "keyset_counters": { "<id>": <long> } }`.
 *
 * [reserve] is the durability-critical path: it bumps the counter and
 * rewrites the file **atomically** (tempfile + rename via [SecureFileIO])
 * before returning, so a crash after a swap can never replay a counter and
 * trip the mint's `outputs already signed`. Access is serialized on the
 * instance — amy is single-process, but a `synchronized` block keeps two
 * concurrent mint ops within one run safe.
 */
class FileCashuKeysetCounterStore(
    private val file: File,
) : CashuKeysetCounterStore {
    private val mapper = jacksonObjectMapper()
    private val lock = Any()

    private data class Persisted(
        val keyset_counters: MutableMap<String, Long> = mutableMapOf(),
    )

    private fun load(): Persisted =
        if (file.exists()) {
            runCatching { mapper.readValue<Persisted>(file.readText()) }.getOrDefault(Persisted())
        } else {
            Persisted()
        }

    override fun peek(keysetId: String): Long = synchronized(lock) { load().keyset_counters[keysetId] ?: 0L }

    override fun reserve(
        keysetId: String,
        count: Int,
    ): Long =
        synchronized(lock) {
            val state = load()
            val first = state.keyset_counters[keysetId] ?: 0L
            state.keyset_counters[keysetId] = first + count.coerceAtLeast(0)
            SecureFileIO.writeTextAtomic(file, mapper.writeValueAsString(state))
            first
        }
}
