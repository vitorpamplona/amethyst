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
package com.vitorpamplona.quartz.experimental.profileTheme.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** The role marker in slot 3 of a theme's `f` tag. */
enum class FontRole(
    val code: String,
) {
    /** All text globally (body, headings, UI elements). */
    BODY("body"),

    /** The user's profile display name. */
    TITLE("title"),
    ;

    companion object {
        fun parse(code: String): FontRole? =
            when (code) {
                BODY.code -> BODY
                TITLE.code -> TITLE
                else -> null
            }
    }
}

/**
 * `["f", "<family>", "<url>", "<role>"]` — a font of a profile theme (Ditto kinds 16767 and 36767).
 *
 * Read leniently, as the spec and the relays require:
 * - "Legacy events with an `f` tag that has no role marker (only 3 elements) SHOULD be treated as
 *   `body`" — so a missing or empty role is [FontRole.BODY].
 * - "Clients that do not recognize a role SHOULD ignore that `f` tag" — an unknown role is null.
 * - The spec requires a direct font-file URL, but Ditto publishes `""` for some families (live
 *   events carry `["f", "Rubik Maps", "", "body"]`), and Blossom-hosted fonts arrive with
 *   extensions like `.odft`. [url] is therefore nullable and never validated beyond being
 *   non-blank: a client that cannot load it "SHOULD fall back to a default font gracefully".
 */
@Immutable
data class FontTag(
    val family: String,
    val url: String?,
    val role: FontRole,
) {
    fun toTagArray() = assemble(family, url, role)

    companion object {
        const val TAG_NAME = "f"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): FontTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotBlank()) { return null }

            val roleCode = tag.getOrNull(3)
            val role = if (roleCode.isNullOrEmpty()) FontRole.BODY else FontRole.parse(roleCode) ?: return null
            val url = tag.getOrNull(2)?.takeIf { it.isNotBlank() }

            return FontTag(tag[1], url, role)
        }

        /** Keeps the url slot (as `""`) when there is no url, so the role stays in slot 3. */
        fun assemble(
            family: String,
            url: String?,
            role: FontRole,
        ) = arrayOf(TAG_NAME, family, url ?: "", role.code)
    }
}
