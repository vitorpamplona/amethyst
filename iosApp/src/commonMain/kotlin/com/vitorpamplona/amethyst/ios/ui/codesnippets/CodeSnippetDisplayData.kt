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
package com.vitorpamplona.amethyst.ios.ui.codesnippets

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent

/**
 * Display data for a code snippet (NIP-C0, kind 1337).
 */
data class CodeSnippetDisplayData(
    val id: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String? = null,
    val name: String,
    val language: String? = null,
    val description: String? = null,
    val code: String,
    val codePreview: String,
    val license: String? = null,
    val runtime: String? = null,
    val deps: List<String> = emptyList(),
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a CodeSnippetEvent to CodeSnippetDisplayData.
 */
fun Note.toCodeSnippetDisplayData(cache: IosLocalCache? = null): CodeSnippetDisplayData? {
    val event = this.event as? CodeSnippetEvent ?: return null
    val user = cache?.getUserIfExists(event.pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                event.pubKey.hexToByteArrayOrNull()?.toNpub() ?: event.pubKey.take(16) + "..."
            } catch (e: Exception) {
                event.pubKey.take(16) + "..."
            }

    val pictureUrl = user?.profilePicture()

    return CodeSnippetDisplayData(
        id = event.id,
        pubKeyHex = event.pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = pictureUrl,
        name = event.snippetName() ?: event.language()?.let { "$it snippet" } ?: "Code Snippet",
        language = event.language() ?: event.extension(),
        description = event.snippetDescription(),
        code = event.content,
        codePreview =
            event.content
                .lines()
                .take(5)
                .joinToString("\n"),
        license = event.license(),
        runtime = event.runtime(),
        deps = event.deps(),
        createdAt = event.createdAt,
    )
}
