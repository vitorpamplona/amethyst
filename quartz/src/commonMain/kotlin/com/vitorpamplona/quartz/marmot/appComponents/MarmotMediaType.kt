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
package com.vitorpamplona.quartz.marmot.appComponents

/**
 * The frozen media-type canonicalization from `features/encrypted-media-v1.md`.
 *
 * Both sender and receiver MUST run this exact algorithm, because the result is
 * bound into AEAD associated data and into media key derivation: a receiver that
 * canonicalizes differently computes different AAD and the decryption simply
 * fails. Adding an alias or a normalization step is a breaking media-version
 * change, not an improvement — which is why the alias table below has exactly
 * one entry and must stay that way.
 */
object MarmotMediaType {
    private const val JPG_ALIAS = "image/jpg"
    private const val JPEG = "image/jpeg"

    /**
     * Canonicalize [mediaType], or return null when the result is unusable.
     *
     * 1. take the substring before the first `;`, dropping parameters
     * 2. trim leading and trailing ASCII whitespace
     * 3. lowercase with ASCII case folding ONLY — never locale-aware, so a
     *    Turkish locale cannot turn `IMAGE/PNG` into `ımage/png`
     * 4. reject an empty result, or one with no `/`
     * 5. apply the single canonical alias `image/jpg` -> `image/jpeg`
     */
    fun canonicalize(mediaType: String): String? {
        val withoutParameters = mediaType.substringBefore(';')
        val trimmed = withoutParameters.trim { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
        val lowered = trimmed.map { if (it in 'A'..'Z') it + ('a' - 'A') else it }.joinToString("")
        if (lowered.isEmpty() || !lowered.contains('/')) return null
        return if (lowered == JPG_ALIAS) JPEG else lowered
    }

    /** [canonicalize], throwing instead of returning null. */
    fun requireCanonical(mediaType: String): String = requireNotNull(canonicalize(mediaType)) { "not a usable media type: '$mediaType'" }
}
