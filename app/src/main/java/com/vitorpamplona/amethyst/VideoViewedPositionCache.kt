package com.vitorpamplona.amethyst

import android.util.LruCache

object VideoViewedPositionCache {
    val cachedPosition = LruCache<String, Long>(10)

    fun add(uri: String, position: Long) {
        cachedPosition.put(uri, position)
    }

    fun get(uri: String): Long? {
        return cachedPosition.get(uri)
    }
}
