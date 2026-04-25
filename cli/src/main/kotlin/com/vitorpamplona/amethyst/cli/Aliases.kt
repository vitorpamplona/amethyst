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

import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Per-account `aliases.json` — short human-friendly names that map to
 * npubs. Today it's only populated by `init --name X` (a self-entry so
 * the user can refer to their own account by name); future verbs like
 * `amy alias add bob npub1…` and recipient resolution in `dm send` will
 * read from the same file.
 *
 * Shape on disk: a JSON object of `{name: npub}` pairs. We persist the
 * npub form (not hex) so `cat aliases.json` is human-inspectable.
 */
object Aliases {
    /** Read the alias map; returns empty when the file doesn't exist. */
    fun load(dataDir: DataDir): MutableMap<String, String> {
        val f = dataDir.aliasesFile
        if (!f.exists()) return linkedMapOf()
        return Output.mapper.readValue(f.readText())
    }

    /** Upsert one entry. Idempotent. */
    fun set(
        dataDir: DataDir,
        name: String,
        npub: String,
    ) {
        val map = load(dataDir)
        map[name] = npub
        SecureFileIO.writeTextAtomic(dataDir.aliasesFile, Output.mapper.writeValueAsString(map))
    }
}
