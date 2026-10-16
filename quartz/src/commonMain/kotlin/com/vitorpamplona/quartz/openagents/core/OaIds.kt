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
package com.vitorpamplona.quartz.openagents.core

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The shared contracts' identifiers. A display name is never an identity: these are.
 *
 * - A slug matches `^[a-z0-9][a-z0-9_-]{0,63}$`.
 * - A package id is `<root-pubkey>:<package-slug>`; the root signer owns the namespace.
 * - A qualified component id is `<publisher-pubkey>:<package-slug>/<component-slug>`.
 */
object OaIds {
    private const val MAX_SLUG = 64

    fun isSlug(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_SLUG) return false
        val first = value[0]
        if (!(first in 'a'..'z' || first in '0'..'9')) return false
        for (i in 1 until value.length) {
            val c = value[i]
            if (!(c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-')) return false
        }
        return true
    }

    fun packageId(
        root: HexKey,
        slug: String,
    ) = "$root:$slug"
}

/** A parsed `<root-pubkey>:<package-slug>`. */
@Immutable
data class PackageId(
    val root: HexKey,
    val slug: String,
) {
    override fun toString() = OaIds.packageId(root, slug)

    companion object {
        fun parse(value: String?): PackageId? {
            if (value == null) return null
            val colon = value.indexOf(':')
            if (colon != 64) return null
            val root = value.substring(0, colon)
            val slug = value.substring(colon + 1)
            if (!isLowerHex64(root) || !OaIds.isSlug(slug)) return null
            return PackageId(root, slug)
        }
    }
}

/** A parsed `<publisher-pubkey>:<package-slug>/<component-slug>`. */
@Immutable
data class QualifiedId(
    val publisher: HexKey,
    val packageSlug: String,
    val componentSlug: String,
) {
    /** The part a person reads: `package/component`, without the key in front. */
    fun path() = "$packageSlug/$componentSlug"

    override fun toString() = "$publisher:${path()}"

    companion object {
        fun parse(value: String?): QualifiedId? {
            if (value == null) return null
            val colon = value.indexOf(':')
            if (colon != 64) return null
            val publisher = value.substring(0, colon)
            val rest = value.substring(colon + 1)
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            val packageSlug = rest.substring(0, slash)
            val componentSlug = rest.substring(slash + 1)
            if (!isLowerHex64(publisher) || !OaIds.isSlug(packageSlug) || !OaIds.isSlug(componentSlug)) return null
            return QualifiedId(publisher, packageSlug, componentSlug)
        }
    }
}
