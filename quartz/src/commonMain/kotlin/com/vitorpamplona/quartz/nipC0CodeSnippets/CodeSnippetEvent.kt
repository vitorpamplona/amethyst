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
package com.vitorpamplona.quartz.nipC0CodeSnippets

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-C0: Code Snippet event (kind:1337).
 *
 * The `.content` field holds the raw code text. All metadata is stored as optional tags.
 */
@Immutable
class CodeSnippetEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    /** Programming language, lowercase (e.g. "python"). */
    fun language() = tags.language()

    /** File extension without the leading dot (e.g. "py"). */
    fun extension() = tags.snippetExtension()

    /** Snippet filename (e.g. "hello-world.py"). */
    fun snippetName() = tags.snippetName()

    /** Brief description of the snippet's purpose. */
    fun snippetDescription() = tags.snippetDescription()

    /** Execution runtime (e.g. "node v18.15.0"). */
    fun runtime() = tags.runtime()

    /** SPDX license identifier (e.g. "MIT"). */
    fun license() = tags.license()

    /** List of required dependencies. */
    fun deps() = tags.deps()

    /** Repository URL or NIP-34 Git announcement reference. */
    fun repo() = tags.repo()

    companion object {
        const val KIND = 1337
        const val ALT_DESCRIPTION = "Code snippet"

        fun build(
            code: String,
            language: String? = null,
            extension: String? = null,
            name: String? = null,
            description: String? = null,
            runtime: String? = null,
            license: String? = null,
            deps: List<String> = emptyList(),
            repo: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CodeSnippetEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, code, createdAt) {
            alt(ALT_DESCRIPTION)
            language?.let { language(it) }
            extension?.let { extension(it) }
            name?.let { snippetName(it) }
            description?.let { snippetDescription(it) }
            runtime?.let { runtime(it) }
            license?.let { license(it) }
            if (deps.isNotEmpty()) deps(deps)
            repo?.let { repo(it) }
            initializer()
        }
    }
}
