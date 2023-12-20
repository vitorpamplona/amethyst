package com.vitorpamplona.quartz.crypto

import android.util.LruCache

class SharedKeyCache {
    private val sharedKeyCache = LruCache<Int, ByteArray>(200)

    fun clearCache() {
        sharedKeyCache.evictAll()
    }

    fun combinedHashCode(a: ByteArray, b: ByteArray): Int {
        var result = 1
        for (element in a) result = 31 * result + element
        for (element in b) result = 31 * result + element
        return result
    }

    fun get(privateKey: ByteArray, pubKey: ByteArray): ByteArray? {
        return sharedKeyCache[combinedHashCode(privateKey, pubKey)]
    }

    fun add(privateKey: ByteArray, pubKey: ByteArray, secret: ByteArray) {
        sharedKeyCache.put(combinedHashCode(privateKey, pubKey), secret)
    }
}