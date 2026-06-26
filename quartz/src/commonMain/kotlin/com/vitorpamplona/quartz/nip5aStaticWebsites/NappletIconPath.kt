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
package com.vitorpamplona.quartz.nip5aStaticWebsites

import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/**
 * Picks the [PathTag] most likely to be a site's / napplet's own app icon, from the `path` tags it
 * already publishes — so a launcher (favorite tab, grid, card) can show the app's real icon even when
 * the manifest carries no explicit `icon` URL tag. The chosen blob is content-addressed (sha256), so it
 * loads from the same verified, Tor-routed blob cache as the rest of the site — never a clearnet favicon
 * fetch that would leak the visit.
 *
 * Selection is by file name, not by reading the index HTML's `<link rel="icon">` (which would need the
 * index blob fetched + parsed first). The conventional locations below cover the common cases; a future
 * pass could add HTML parsing for the rest.
 */
object NappletIconPath {
    // Preferred exact file names, best first. Raster web formats Android/Coil decodes natively rank above
    // .ico (flaky on Android) and .svg (needs an extra Coil decoder), which sit last as best-effort — a
    // failed decode just falls back to the type glyph, so listing them costs nothing.
    private val PRIORITY =
        listOf(
            "apple-touch-icon.png",
            "apple-touch-icon-precomposed.png",
            "icon.png",
            "icon-512.png",
            "icon-512x512.png",
            "icon-256.png",
            "icon-192.png",
            "icon-192x192.png",
            "favicon.png",
            "logo.png",
            "icon.webp",
            "favicon.webp",
            "logo.webp",
            "icon.jpg",
            "icon.jpeg",
            "favicon.ico",
            "icon.svg",
            "favicon.svg",
            "logo.svg",
        )

    // Decode-friendly raster extensions for the loose fallback (no .ico / .svg here — only names we're
    // confident render, since the fallback has no curated-name signal to justify a best-effort decode).
    private val RASTER = listOf(".png", ".webp", ".jpg", ".jpeg", ".gif", ".bmp")

    // Loose-fallback name stems: a raster file whose name looks like an icon, when no exact name matched.
    private val STEMS = listOf("apple-touch-icon", "favicon", "icon", "logo")

    // PRIORITY as a name -> rank lookup, so a path's preference is an O(1) map hit instead of a scan of
    // the whole list per name. Built once.
    private val PRIORITY_RANK: Map<String, Int> = PRIORITY.withIndex().associate { (i, name) -> name to i }

    /**
     * The best icon blob in [paths], or null if none looks like an icon. A lower-ranked conventional name
     * wins over a higher one; among equally-named candidates the shallowest path wins (a root
     * `/favicon.png` over a nested `/assets/x/favicon.png`); a loose icon-ish raster is the last resort.
     *
     * Single pass: [basename] is computed once per path (vs. once per path *per* priority name), and no
     * intermediate lists are allocated — this runs over a whole site's `path` set, which can be large.
     */
    fun choose(paths: List<PathTag>): PathTag? {
        var best: PathTag? = null
        var bestRank = Int.MAX_VALUE
        var bestDepth = Int.MAX_VALUE
        var fallback: PathTag? = null
        var fallbackDepth = Int.MAX_VALUE

        for (p in paths) {
            val b = basename(p.path)
            val rank = PRIORITY_RANK[b]
            if (rank != null) {
                val d = depth(p.path)
                if (rank < bestRank || (rank == bestRank && d < bestDepth)) {
                    best = p
                    bestRank = rank
                    bestDepth = d
                }
            } else if (best == null && RASTER.any(b::endsWith) && STEMS.any(b::contains)) {
                val d = depth(p.path)
                if (d < fallbackDepth) {
                    fallback = p
                    fallbackDepth = d
                }
            }
        }

        return best ?: fallback
    }

    private fun basename(path: String) = path.substringAfterLast('/').lowercase()

    private fun depth(path: String) = path.count { it == '/' }
}
