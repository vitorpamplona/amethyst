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
package com.vitorpamplona.amethyst.model

import androidx.collection.LruCache
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

interface MutableMediaAspectRatioCache {
    fun get(url: String): Float?

    fun add(
        url: String,
        width: Int,
        height: Int,
    )
}

/**
 * Aspect ratios keyed by media URL, learned from imeta `dim` tags up front or from the decoder once
 * a first frame lands.
 *
 * Entries are snapshot state, so a composable that calls [get] **during composition** recomposes
 * when the real dimensions arrive later. That matters because players and image loaders only report
 * size after the first frame decodes: a caller that sized itself off a plain cache miss would stay
 * wrong for the whole visit and only look right the *next* time the media is opened. Note this only
 * works for reads made in composition — a read from inside `remember { }` is cached by `remember`
 * itself and won't pick the update up.
 */
object MediaAspectRatioCache : MutableMediaAspectRatioCache {
    private val cache = LruCache<String, MutableState<Float?>>(1000)

    // get-then-put has to be atomic, so the compound op is guarded even though LruCache is itself
    // thread-safe. A miss still stores a slot: that empty slot is what the caller observes until
    // add() fills it in.
    @Synchronized
    private fun entry(url: String): MutableState<Float?> = cache.get(url) ?: mutableStateOf<Float?>(null).also { cache.put(url, it) }

    override fun get(url: String): Float? = entry(url).value

    override fun add(
        url: String,
        width: Int,
        height: Int,
    ) {
        if (height > 1) {
            entry(url).value = width.toFloat() / height.toFloat()
        }
    }
}
