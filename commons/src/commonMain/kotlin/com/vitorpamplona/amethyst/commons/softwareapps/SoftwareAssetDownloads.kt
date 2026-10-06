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
package com.vitorpamplona.amethyst.commons.softwareapps

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent

/** Where to download a NIP-82 asset from, and what to call the file. */
object SoftwareAssetDownloads {
    /** The extension of the asset's `filename` tag ("zip", "AppImage"), or null when it has none. */
    fun extension(asset: SoftwareAssetEvent): String? =
        asset
            .filename()
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotEmpty() && it.length <= 16 && it.all { c -> c.isLetterOrDigit() } }

    /**
     * The asset's `url`, with the `filename` extension added when the url is a bare Blossom
     * hash link (`…/<sha256>`, the asset's own `x`). Blossom serves `/<sha256>.<ext>` too
     * (BUD-01), and without it a browser saves a file named after the hash with no extension.
     * Null when the asset has no `url`: NIP-82 then has clients find it by hash on Blossom.
     */
    fun url(asset: SoftwareAssetEvent): String? {
        val url = asset.url() ?: return null
        val hash = asset.hash() ?: return url
        val ext = extension(asset) ?: return url

        val pathEnd = url.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) url.length else it }
        val path = url.substring(0, pathEnd)
        if (!path.endsWith("/$hash", ignoreCase = true)) return url

        return "$path.$ext${url.substring(pathEnd)}"
    }
}
