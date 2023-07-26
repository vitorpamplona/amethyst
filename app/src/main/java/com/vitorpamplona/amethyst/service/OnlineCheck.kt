package com.vitorpamplona.amethyst.service

import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.BuildConfig
import okhttp3.Request

@Immutable
data class OnlineCheckResult(val timeInMs: Long, val online: Boolean)

object OnlineChecker {
    val checkOnlineCache = LruCache<String, OnlineCheckResult>(100)
    val fiveMinutes = 1000 * 60 * 5

    fun isOnline(url: String?): Boolean {
        checkNotInMainThread()

        if (url.isNullOrBlank()) return false
        if ((checkOnlineCache.get(url)?.timeInMs ?: 0) > System.currentTimeMillis() - fiveMinutes) {
            return checkOnlineCache.get(url).online
        }

        return try {
            val request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url(url)
                .get()
                .build()

            val result = HttpClient.getHttpClient().newCall(request).execute().use {
                it.isSuccessful
            }
            checkOnlineCache.put(url, OnlineCheckResult(System.currentTimeMillis(), result))
            result
        } catch (e: Exception) {
            checkOnlineCache.put(url, OnlineCheckResult(System.currentTimeMillis(), false))
            Log.e("LiveActivities", "Failed to check streaming url $url", e)
            false
        }
    }
}
