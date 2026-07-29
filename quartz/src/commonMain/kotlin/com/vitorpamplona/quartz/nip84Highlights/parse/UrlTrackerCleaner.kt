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
package com.vitorpamplona.quartz.nip84Highlights.parse

/**
 * Strips known tracking parameters from a URL's query string.
 *
 * NIP-84 asks clients to "do a best effort of cleaning the URL from trackers" before
 * tagging the source of a highlight, so the same passage highlighted from two different
 * campaign links collapses to one canonical `r` tag instead of leaking the sharer's
 * `utm_*`/`fbclid`/etc. attribution into the published event.
 *
 * Only the query component is touched — the path and any fragment (including a
 * `#:~:text=` directive) are preserved verbatim.
 */
object UrlTrackerCleaner {
    /**
     * Exact parameter names known to be pure tracking/attribution noise. Names are matched
     * case-insensitively. Any parameter whose name starts with `utm_` is also dropped
     * regardless of this set.
     */
    private val TRACKER_PARAMS =
        setOf(
            "fbclid",
            "gclid",
            "gclsrc",
            "gbraid",
            "wbraid",
            "dclid",
            "msclkid",
            "yclid",
            "twclid",
            "ttclid",
            "igshid",
            "igsh",
            "mc_eid",
            "mc_cid",
            "mkt_tok",
            "_hsenc",
            "_hsmi",
            "vero_id",
            "vero_conv",
            "oly_anon_id",
            "oly_enc_id",
            "wickedid",
            "ncid",
            "s_cid",
            "cmpid",
            "spm",
            "scm",
            "ref_src",
            "ref_url",
            "_ga",
        )

    private fun isTracker(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("utm_") || lower in TRACKER_PARAMS
    }

    fun clean(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url

        // Keep any fragment (element id and/or `:~:text=` directive) untouched.
        val fragmentStart = url.indexOf('#', queryStart)
        val query = if (fragmentStart >= 0) url.substring(queryStart + 1, fragmentStart) else url.substring(queryStart + 1)
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""
        val base = url.substring(0, queryStart)

        val kept =
            query
                .split("&")
                .filter { it.isNotEmpty() && !isTracker(it.substringBefore("=")) }

        return if (kept.isEmpty()) {
            base + fragment
        } else {
            base + "?" + kept.joinToString("&") + fragment
        }
    }
}
