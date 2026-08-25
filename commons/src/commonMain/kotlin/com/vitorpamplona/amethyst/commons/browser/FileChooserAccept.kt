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
package com.vitorpamplona.amethyst.commons.browser

/**
 * Translates an HTML `<input type="file" accept="…">` list into what an Android picker Intent needs:
 * a single `type` plus an optional `EXTRA_MIME_TYPES` array.
 *
 * WebView hands us the `accept` attribute pre-split into `FileChooserParams.getAcceptTypes`, but the
 * entries are whatever the page author wrote — exact MIME types, family wildcards, bare extensions
 * (`.heic`), or several of those comma-joined into one entry. Android's own
 * `FileChooserParams.createIntent()` keeps only the FIRST entry and drops multi-select entirely, so a
 * page asking for `accept="image/png,image/jpeg" multiple` would offer PNGs only, one at a time. This
 * resolves the whole list instead.
 *
 * Extension → MIME lookup is injected ([resolve]'s `extensionToMime`) rather than calling
 * `android.webkit.MimeTypeMap` directly, which keeps this pure and unit-testable in commonMain; the
 * Android callers pass the real map.
 */
object FileChooserAccept {
    /** The any-type wildcard, used when the page asked for nothing or for types with no common family. */
    const val ANY = "*/*"

    /**
     * [primaryType] goes in `Intent.setType` — the only thing pickers that predate `EXTRA_MIME_TYPES`
     * (and plain `ACTION_GET_CONTENT` targets) look at, so it is widened to the narrowest wildcard that
     * still covers everything asked for. [mimeTypes] is the exact list for `EXTRA_MIME_TYPES`, empty
     * when nothing resolved (in which case [primaryType] is [ANY] and the picker shows everything).
     */
    data class Resolved(
        val primaryType: String,
        val mimeTypes: List<String>,
    )

    /**
     * Resolves [acceptTypes] (raw `accept` entries) into a picker filter.
     *
     * [extensionToMime] maps a lower-case extension with no leading dot (`"heic"`) to a MIME type, or
     * null when the platform doesn't know it — an unknown extension simply contributes nothing rather
     * than narrowing the picker to something the user can't satisfy.
     */
    fun resolve(
        acceptTypes: List<String>,
        extensionToMime: (String) -> String?,
    ): Resolved {
        val mimes =
            acceptTypes
                // A page may write accept="image/*,video/*" and some WebView versions pass that through
                // as ONE entry, so split again on the separator the attribute itself uses.
                .flatMap { it.split(',') }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token ->
                    when {
                        token.contains('/') -> token
                        else -> extensionToMime(token.removePrefix("."))
                    }
                }.filter { it.isNotEmpty() }
                .distinct()

        return Resolved(primaryType = commonType(mimes), mimeTypes = mimes)
    }

    /**
     * The narrowest single type covering [mimes]: the type itself when there is only one, the shared
     * family's wildcard when they all belong to one family, and [ANY] when they span families (or the
     * list is empty). Never narrower than the request — a picker filtered to `image/png` would hide a
     * JPEG the page also accepts.
     */
    private fun commonType(mimes: List<String>): String {
        if (mimes.isEmpty()) return ANY
        if (mimes.size == 1) return mimes.first()
        val families = mimes.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.first()}/*" else ANY
    }
}
