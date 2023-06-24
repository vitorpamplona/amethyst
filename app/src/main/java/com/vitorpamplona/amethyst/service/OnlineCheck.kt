package com.vitorpamplona.amethyst.service

import android.util.Log
import android.util.LruCache
import com.google.errorprone.annotations.Immutable
import com.vitorpamplona.amethyst.BuildConfig
import okhttp3.Request

@Immutable
data class OnlineCheckResult(val timeInMs: Long, val online: Boolean)

object OnlineChecker {
    val checkOnlineCache = LruCache<String, OnlineCheckResult>(10)
    val fiveMinutes = 1000 * 60 * 5

    fun isOnline(url: String?): Boolean {
        checkNotInMainThread()

        if (url.isNullOrBlank()) return false
        if ((checkOnlineCache.get(url)?.timeInMs ?: 0) > System.currentTimeMillis() - fiveMinutes) {
            return checkOnlineCache.get(url).online
        }

        val request = Request.Builder()
            .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
            .url(url)
            .get()
            .build()

        return try {
            val result = HttpClient.getHttpClient().newCall(request).execute().code == 200
            checkOnlineCache.put(url, OnlineCheckResult(System.currentTimeMillis(), result))
            result
        } catch (e: Exception) {
            checkOnlineCache.put(url, OnlineCheckResult(System.currentTimeMillis(), false))
            Log.e("LiveActivities", "Failed to check streaming url $url", e)
            false
        }
    }
}
